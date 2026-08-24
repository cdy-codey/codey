package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.infra.ModelToolCall;
import com.codey.infra.WorkspacePathSupport;
import com.codey.config.AgentSession;
import com.codey.session.SessionEventFactory;
import com.codey.session.SessionStore;
import com.codey.skill.SkillDefinition;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.ToolExecutionRecord;
import com.codey.tools.ToolExecutor;
import com.codey.verify.Verifier;
import com.codey.verify.VerifyResult;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 处理一轮模型返回中的 tool calls，收敛工具权限、执行、校验和 transcript 回写逻辑。
 */
final class ToolCallProcessor {
    private final ToolExecutor toolExecutor;
    private final ToolAccessController toolAccessController;
    private final LoopGuard loopGuard;
    private final HumanConfirmationService humanConfirmationService;
    private final SessionStore sessionStore;
    private final Verifier verifier;
    private final ReplanService replanService;

    ToolCallProcessor(ToolExecutor toolExecutor,
                      ToolAccessController toolAccessController,
                      LoopGuard loopGuard,
                      HumanConfirmationService humanConfirmationService,
                      SessionStore sessionStore,
                      Verifier verifier,
                      ReplanService replanService) {
        this.toolExecutor = toolExecutor;
        this.toolAccessController = toolAccessController;
        this.loopGuard = loopGuard;
        this.humanConfirmationService = humanConfirmationService;
        this.sessionStore = sessionStore;
        this.verifier = verifier;
        this.replanService = replanService;
    }

    ToolResult processToolCalls(List<ModelToolCall> toolCalls,
                          AgentSession session,
                          SkillDefinition skill,
                          String assistantContent,
                          String assistantReasoningContent) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        assignMissingToolCallIds(toolCalls);
        // 每轮处理前重置“写成功即自动完成”标记，避免上一轮残留影响本轮判断。
        if (session != null) {
            session.resetVerifiedWriteAutoComplete();
        }

        List<ToolInvocation> parallelBatch = new ArrayList<ToolInvocation>();
        List<String> parallelSignatures = new ArrayList<String>();
        List<ModelToolCall> parallelToolCalls = new ArrayList<ModelToolCall>();
        List<ModelToolCall> executedToolCalls = new ArrayList<ModelToolCall>();
        List<ModelMessage> toolResultMessages = new ArrayList<ModelMessage>();
        ToolResult pendingUserChoice = null;
        for (ModelToolCall toolCall : toolCalls) {
            ToolInvocation request = toToolInvocation(toolCall, session);
            if (!toolExecutor.canRunInParallel(request)
                    && !flushParallelBatch(parallelBatch, parallelSignatures, parallelToolCalls, session, executedToolCalls, toolResultMessages)) {
                break;
            }
            if (!toolAccessController.isAllowed(skill, request, session)) {
                String error = "Tool request not allowed by skill constraints: " + request.getToolName();
                sessionStore.appendEvent(SessionEventFactory.securityEvent(session.getSessionId(), error));
                replanService.appendFeedbackAndRequestReplan(session, error);
                break;
            }

            boolean isWriteTool = !toolExecutor.isReadOnly(request);
            LoopGuardDecision guardDecision = loopGuard.inspect(request, isWriteTool, session);
            if (guardDecision.getAction() == LoopGuardDecision.Action.SKIP) {
                session.appendSystemFeedback(guardDecision.getMessage());
                // 关键：跳过重复请求时也要给模型回写一条可见的 tool 结果。
                // 否则模型在下一轮看不到任何新信息，会原样重复同一工具请求，最终被判定为停滞。
                rememberExecutedToolCall(executedToolCalls, toolCall);
                appendToolResultMessage(
                        toolResultMessages,
                        toolCall,
                        buildSkippedToolResult(request, session, guardDecision.getMessage())
                );
                continue;
            }
            if (guardDecision.getAction() == LoopGuardDecision.Action.REPLAN) {
                if (!flushParallelBatch(parallelBatch, parallelSignatures, parallelToolCalls, session, executedToolCalls, toolResultMessages)) {
                    break;
                }
                replanService.appendFeedbackAndRequestReplan(session, guardDecision.getMessage());
                continue;
            }

            String requestSignature = guardDecision.getRequestSignature();
            if (toolExecutor.canRunInParallel(request)) {
                parallelBatch.add(request);
                parallelSignatures.add(requestSignature);
                parallelToolCalls.add(toolCall);
                continue;
            }

            if (isWriteTool) {
                FinalResult confirmationState = toConfirmationState(request);
                if (shouldRequireHumanConfirmation(confirmationState, request, session, skill)) {
                    HumanDecision decision = humanConfirmationService.confirmEdit(session, confirmationState, request);
                    sessionStore.appendEvent(SessionEventFactory.humanDecision(session.getSessionId(), decision));
                    session.appendInteraction(buildHumanDecisionMessage(request, confirmationState, decision));
                    if (!decision.isApproved()) {
                        replanService.appendFeedbackAndRequestReplan(session, decision.getFeedback());
                        break;
                    }
                    if (!isBlank(decision.getFeedback())) {
                        session.appendSystemFeedback("人工已批准本次编辑，并补充说明: " + decision.getFeedback());
                    }
                }
                session.setLastEditedFilePath(resolveWriteTargetPath(request));
            }

            sessionStore.appendEvent(SessionEventFactory.toolExecutionStarted(session.getSessionId(), request));
            ToolResult result = toolExecutor.execute(request, session.getWorkingDirectory(), session.getTenantId(), session.isFormMode());
            sessionStore.appendEvent(SessionEventFactory.toolCall(session.getSessionId(), request, result));
            rememberExecutedToolCall(executedToolCalls, toolCall);
            if (result.isUserChoice()) {
                // 工具要求用户先选择再继续（如校验未通过列出问题），暂停本循环等待用户决策。
                appendToolResultMessage(toolResultMessages, toolCall, result.getContentForModel());
                pendingUserChoice = result;
                break;
            }
            if (!result.isSuccess()) {
                // #endregion
                loopGuard.recordFailure(session, requestSignature);
                session.appendSystemFeedback(result.getErrorMessage());
                appendToolResultMessage(toolResultMessages, toolCall, result.getErrorMessage());
                replanService.requestReplan(session, result.getErrorMessage());
                break;
            }
            // #endregion
            loopGuard.recordSuccess(session, requestSignature, isWriteTool);
            rememberContextAfterToolSuccess(session, request);
            appendToolResultMessage(toolResultMessages, toolCall, result.getContentForModel());
            rememberReadFileSnippetIfAny(session, request, result.getContentForModel());

            if (isWriteTool) {
                session.appendEditResult(result.getContent());
                VerifyResult verifyResult = verifier.verifyEdit(session, skill);
                if (verifyResult.isApplicable()) {
                    sessionStore.appendEvent(SessionEventFactory.verification(session.getSessionId(), verifyResult));
                }
                if (verifyResult.isPassed()) {
                    // 该技能配置了“写成功并通过校验即结束”，不再要求模型额外输出 FINISH，直接结束本轮循环。
                    if (skill != null && skill.isAutoCompleteOnVerifiedWrite()) {
                        session.markVerifiedWriteAutoComplete();
                    } else {
                        session.appendSystemFeedback(
                                "最近写工具已执行成功并通过校验；如果用户目标已经满足，请直接输出 FINISH，不要为了确认结果重复读取同一文件。"
                        );
                    }
                } else if (verifyResult.isFailed()) {
                    replanService.appendFeedbackAndRequestReplan(session, verifyResult.getMessage());
                }
                continue;
            }

            session.appendToolResult(result.getContentForModel());
        }

        flushParallelBatch(parallelBatch, parallelSignatures, parallelToolCalls, session, executedToolCalls, toolResultMessages);
        flushExecutedToolTranscript(session, assistantContent, assistantReasoningContent, executedToolCalls, toolResultMessages);
        return pendingUserChoice;
    }

    private ToolInvocation toToolInvocation(ModelToolCall toolCall) {
        ToolInvocation request = new ToolInvocation();
        request.setToolName(toolCall.getName() == null ? null : toolCall.getName().trim());
        request.setArguments(toolCall.getArguments());
        return request;
    }

    private ToolInvocation toToolInvocation(ModelToolCall toolCall, AgentSession session) {
        ToolInvocation request = toToolInvocation(toolCall);
        normalizeToolArguments(request, session);
        return request;
    }

    private void normalizeToolArguments(ToolInvocation request, AgentSession session) {
        if (request == null || request.getArguments() == null || session == null) {
            return;
        }
        String tool = request.getToolName();
        if (isBlank(tool)) {
            return;
        }
        if ("read_file".equals(tool)) {
            normalizePathArg(request, session, "path");
            normalizeReadFileArgs(request, session);
            return;
        }
        if ("write_file".equals(tool) || "edit_file".equals(tool)) {
            normalizePathArg(request, session, "path");
            return;
        }
        if ("delete_file".equals(tool)) {
            normalizePathArrayArg(request, session, "paths");
            return;
        }
        if ("list_workspace".equals(tool)
                || "project_map".equals(tool)
                || "search_content".equals(tool)){
            normalizePathArg(request, session, "pathHint");
            return;
        }
        if ("edit_code".equals(tool)) {
            normalizePathArg(request, session, "file");
        }
    }

    private void normalizeReadFileArgs(ToolInvocation request, AgentSession session) {
        if (request == null || request.getArguments() == null || session == null) {
            return;
        }
        Object pathValue = request.getArguments().get("path");
        String path = pathValue == null ? "" : String.valueOf(pathValue).trim();
        Integer offset = readIntegerArg(request, "offset");
        Integer limit = readIntegerArg(request, "limit");
        int normalizedOffset = offset == null ? 1 : Math.max(1, offset.intValue());
        if (offset == null || offset.intValue() != normalizedOffset) {
            request.getArguments().put("offset", normalizedOffset);
        }

        boolean firstRead = !isBlank(path) && !session.hasReadFileRanges(path);
        if (firstRead && normalizedOffset <= 1) {
            int normalizedLimit = limit == null ? 0 : Math.max(0, limit.intValue());
            if (normalizedLimit == 0 || normalizedLimit < 600) {
                request.getArguments().put("limit", 1200);
            }
        }
    }

    private void normalizePathArg(ToolInvocation request, AgentSession session, String key) {
        Object value = request.getArguments().get(key);
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            if (toolExecutor.getWorkspaceRoot() == null) {
                return;
            }
            // 会话对模型始终只暴露相对路径；执行前也保持相对语义，避免绝对路径继续进入 transcript。
            String normalized = WorkspacePathSupport.normalizeToolPathArgument(
                    text,
                    session.getWorkingDirectory(),
                    toolExecutor.getWorkspaceRoot()
            );
            request.getArguments().put(key, normalized);
        } catch (Exception ignored) {
            // 忽略路径标准化失败。
        }
    }

    private void normalizePathArrayArg(ToolInvocation request, AgentSession session, String key) {
        Object value = request.getArguments().get(key);
        if (!(value instanceof List)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> rawValues = (List<Object>) value;
        List<String> normalized = new ArrayList<String>();
        for (Object item : rawValues) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                if (toolExecutor.getWorkspaceRoot() == null) {
                    normalized.add(text);
                    continue;
                }
                normalized.add(WorkspacePathSupport.normalizeToolPathArgument(
                        text,
                        session.getWorkingDirectory(),
                        toolExecutor.getWorkspaceRoot()
                ));
            } catch (Exception ignored) {
                normalized.add(text);
            }
        }
        request.getArguments().put(key, normalized);
    }

    private String buildHumanDecisionMessage(ToolInvocation request, FinalResult turn, HumanDecision decision) {
        StringBuilder builder = new StringBuilder();
        builder.append("人工决策[").append(decision.getDecisionLabel()).append("]: ");
        builder.append(request.getToolName());
        String file = readStringArg(request, "file");
        if (!isBlank(file)) {
            builder.append(" -> ").append(file);
        }
        if (!isBlank(turn.getUncertaintyReason())) {
            builder.append(" | 不确定原因: ").append(turn.getUncertaintyReason());
        }
        if (!isBlank(decision.getFeedback())) {
            builder.append(" | ").append(decision.getFeedback());
        }
        return builder.toString();
    }

    private boolean shouldRequireHumanConfirmation(FinalResult turn, ToolInvocation request, AgentSession session, SkillDefinition skill) {
        if (request == null) {
            return true;
        }
        String toolName = request.getToolName();
        // 写文件、改表单、删除、打补丁都属于高风险修改操作，执行前都需要人工确认。
        if ("write_file".equals(toolName) || "edit_file".equals(toolName)) {
            // 技能声明了上下文文件（如 context.json）时，写入该文件属于已授权动作，不再重复弹确认。
            if (isFormContextTarget(request, skill)) {
                return false;
            }
            return true;
        }
        if ("delete_file".equals(toolName) || "apply_structured_patch".equals(toolName)) {
            return true;
        }
        if (Boolean.TRUE.equals(turn.getRequiresHumanConfirmation())) {
            return true;
        }
        if (!isBlank(turn.getUncertaintyReason())) {
            return true;
        }
        return isBlank(resolveWriteTargetPath(request));
    }

    private FinalResult toConfirmationState(ToolInvocation request) {
        FinalResult finalResult = new FinalResult();
        finalResult.setStatus("TOOL_CALL");
        finalResult.setSummary("准备执行文件修改");
        Object requiresHumanConfirmation = request.getArguments().get("requiresHumanConfirmation");
        if (requiresHumanConfirmation instanceof Boolean) {
            finalResult.setRequiresHumanConfirmation((Boolean) requiresHumanConfirmation);
        } else if (requiresHumanConfirmation != null) {
            finalResult.setRequiresHumanConfirmation(Boolean.valueOf(String.valueOf(requiresHumanConfirmation)));
        }
        Object uncertaintyReason = request.getArguments().get("uncertaintyReason");
        if (uncertaintyReason != null) {
            finalResult.setUncertaintyReason(String.valueOf(uncertaintyReason));
        }
        return finalResult;
    }

    private boolean flushParallelBatch(List<ToolInvocation> parallelBatch,
                                       List<String> parallelSignatures,
                                       List<ModelToolCall> parallelToolCalls,
                                       AgentSession session,
                                       List<ModelToolCall> executedToolCalls,
                                       List<ModelMessage> toolResultMessages) {
        if (parallelBatch.isEmpty()) {
            return true;
        }

        List<ToolExecutionRecord> records =
                toolExecutor.executeBatch(new ArrayList<ToolInvocation>(parallelBatch), session.getWorkingDirectory(), session.getTenantId(), session.isFormMode());
        parallelBatch.clear();
        for (int index = 0; index < records.size(); index++) {
            ToolExecutionRecord record = records.get(index);
            String requestSignature = parallelSignatures.size() > index
                    ? parallelSignatures.get(index)
                    : ToolRequestSignature.from(record.getRequest());
            sessionStore.appendEvent(SessionEventFactory.toolCall(session.getSessionId(), record.getRequest(), record.getResult()));
            rememberExecutedToolCall(executedToolCalls, parallelToolCalls.size() > index ? parallelToolCalls.get(index) : null);
            if (!record.getResult().isSuccess()) {
                loopGuard.recordFailure(session, requestSignature);
                session.appendSystemFeedback(record.getResult().getErrorMessage());
                appendParallelToolResultMessage(parallelToolCalls, index, toolResultMessages, record.getResult().getErrorMessage());
                replanService.requestReplan(session, record.getResult().getErrorMessage());
                parallelSignatures.clear();
                parallelToolCalls.clear();
                return false;
            }

            loopGuard.recordSuccess(session, requestSignature, false);
            rememberContextAfterToolSuccess(session, record.getRequest());
            appendParallelToolResultMessage(parallelToolCalls, index, toolResultMessages, record.getResult().getContentForModel());
            rememberReadFileSnippetIfAny(session, record.getRequest(), record.getResult().getContentForModel());
            session.appendToolResult(record.getResult().getContentForModel());
        }
        parallelSignatures.clear();
        parallelToolCalls.clear();
        return true;
    }

    private void rememberContextAfterToolSuccess(AgentSession session, ToolInvocation request) {
        if (session == null || request == null || request.getArguments() == null) {
            return;
        }
        String tool = request.getToolName();
        if ("read_file".equals(tool)) {
            String path = readStringArg(request, "path");
            if (!isBlank(path)) {
                Integer offset = readIntegerArg(request, "offset");
                Integer limit = readIntegerArg(request, "limit");
                if (offset != null && limit != null) {
                    session.rememberReadFileRange(path, offset, limit);
                }
            }
            return;
        }
        if ("edit_file".equals(tool) || "write_file".equals(tool)) {
            String path = readStringArg(request, "path");
            if (!isBlank(path)) {
                // 写工具只更新最近编辑目标，不污染前端原始上下文文件列表。
                session.setLastEditedFilePath(path);
            }
            return;
        }
        if ("search_in_files".equals(tool) || "global_search".equals(tool)) {
            String dir = readStringArg(request, "path");
            if (isBlank(dir)) {
                dir = readStringArg(request, "directory");
            }
        }
    }

    private Integer readIntegerArg(ToolInvocation request, String key) {
        Object value = request.getArguments().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readStringArg(ToolInvocation request, String key) {
        Object value = request.getArguments().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private void appendParallelToolResultMessage(List<ModelToolCall> parallelToolCalls,
                                                 int index,
                                                 List<ModelMessage> toolResultMessages,
                                                 String content) {
        if (parallelToolCalls == null || parallelToolCalls.size() <= index) {
            return;
        }
        ModelToolCall toolCall = parallelToolCalls.get(index);
        if (toolCall == null) {
            return;
        }
        appendToolResultMessage(toolResultMessages, toolCall, content);
    }

    private void appendToolResultMessage(List<ModelMessage> toolResultMessages,
                                         ModelToolCall toolCall,
                                         String content) {
        if (toolResultMessages == null || toolCall == null || isBlank(content)) {
            return;
        }
        toolResultMessages.add(ModelMessage.toolResult(toolCall.getId(), toolCall.getName(), content));
    }

    /**
     * 构造被 LoopGuard 跳过的工具请求对模型可见的结果内容。
     * read_file 优先复用已缓存的读取片段，其余工具回退为守卫反馈文案。
     */
    private String buildSkippedToolResult(ToolInvocation request, AgentSession session, String feedback) {
        if (request != null && "read_file".equals(request.getToolName())) {
            String path = readStringArg(request, "path");
            Integer offset = readIntegerArg(request, "offset");
            Integer limit = readIntegerArg(request, "limit");
            String cached = session == null ? "" : session.getReadFileSnippetForRange(path, offset, limit);
            if (!isBlank(cached)) {
                return "该文件内容此前已读取，以下为已缓存片段，请直接复用并继续任务：\n" + cached;
            }
        }
        return isBlank(feedback) ? "该工具请求已执行过，请复用已有结果。" : feedback;
    }

    private void rememberReadFileSnippetIfAny(AgentSession session, ToolInvocation request, String contentForModel) {
        if (session == null || request == null || contentForModel == null) {
            return;
        }
        if (!"read_file".equals(request.getToolName())) {
            return;
        }
        String path = readStringArg(request, "path");
        Integer offset = readIntegerArg(request, "offset");
        Integer limit = readIntegerArg(request, "limit");
        if (isBlank(path) || offset == null || limit == null) {
            return;
        }
        String snippet = extractPreview(contentForModel);
        if (!isBlank(snippet)) {
            session.rememberReadFileSnippet(path, offset, limit, snippet);
        }
    }

    private String extractPreview(String contentForModel) {
        String normalized = contentForModel.replace("\r", "");
        int index = normalized.indexOf("\npreview:\n");
        if (index < 0) {
            return "";
        }
        return normalized.substring(index + "\npreview:\n".length()).trim();
    }

    private void rememberExecutedToolCall(List<ModelToolCall> executedToolCalls, ModelToolCall toolCall) {
        if (executedToolCalls == null || toolCall == null || isBlank(toolCall.getId()) || isBlank(toolCall.getName())) {
            return;
        }
        for (ModelToolCall existing : executedToolCalls) {
            if (existing != null && toolCall.getId().equals(existing.getId())) {
                return;
            }
        }
        executedToolCalls.add(toolCall);
    }

    private void flushExecutedToolTranscript(AgentSession session,
                                             String assistantContent,
                                             String assistantReasoningContent,
                                             List<ModelToolCall> executedToolCalls,
                                             List<ModelMessage> toolResultMessages) {
        if (session == null) {
            return;
        }
        if (executedToolCalls != null && !executedToolCalls.isEmpty()) {
            session.appendAssistantToolCalls(assistantContent, assistantReasoningContent, executedToolCalls);
            if (toolResultMessages != null) {
                for (ModelMessage toolResultMessage : toolResultMessages) {
                    if (toolResultMessage == null || !toolResultMessage.isToolResult()) {
                        continue;
                    }
                    session.appendToolResultMessage(
                            toolResultMessage.getToolCallId(),
                            toolResultMessage.getToolName(),
                            toolResultMessage.getContent()
                    );
                }
            }
            return;
        }
        if (!isBlank(assistantContent) || !isBlank(assistantReasoningContent)) {
            session.appendAssistantMessage(assistantContent, assistantReasoningContent);
        }
    }

    private void assignMissingToolCallIds(List<ModelToolCall> toolCalls) {
        if (toolCalls == null) {
            return;
        }
        int sequence = 1;
        for (ModelToolCall toolCall : toolCalls) {
            if (toolCall != null && isBlank(toolCall.getId())) {
                toolCall.setId("tool-call-" + sequence);
            }
            sequence++;
        }
    }

    private String resolveWriteTargetPath(ToolInvocation request) {
        String file = readStringArg(request, "file");
        if (!isBlank(file)) {
            return file;
        }
        String path = readStringArg(request, "path");
        if (!isBlank(path)) {
            return path;
        }
        return "";
    }

    private boolean isFormContextTarget(ToolInvocation request, SkillDefinition skill) {
        if (skill == null || isBlank(skill.getContextFileName())) {
            return false;
        }
        String path = resolveWriteTargetPath(request);
        if (isBlank(path)) {
            return false;
        }
        String normalized = path.replace("\\", "/");
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return skill.getContextFileName().equalsIgnoreCase(fileName);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escapeDebug(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private String limitDebug(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
    // #endregion
}
