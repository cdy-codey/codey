package com.codey.loop;

import com.codey.config.AgentSession;
import com.codey.infra.ModelGateway;
import com.codey.infra.ModelMessage;
import com.codey.infra.ModelRequest;
import com.codey.infra.ModelRequestType;
import com.codey.infra.ModelResponse;
import com.codey.session.SessionEventFactory;
import com.codey.session.SessionStore;
import com.codey.skill.SkillDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * 当上下文预算达到阈值，或单轮 loop 结束时，
 * 用模型生成一条回灌摘要来折叠旧上下文，同时保留最新一轮输出不被替换。
 */
final class ContextSummaryService {
    /** 默认模式：上下文超过 50000 字符时触发摘要 */
    private static final int SUMMARY_TRIGGER_CHARS = 50000;
    /** 单表模式：上下文超过 30M 字符时触发摘要 */
    private static final int SINGLE_FILE_TRIGGER_CHARS = 30_000_000;
    /** 单表模式：每次触发后阈值增量 10M */
    private static final int SINGLE_FILE_TRIGGER_INCREMENT = 10_000_000;

    private final PromptAssembler promptAssembler;
    private final PromptBudgetEstimator promptBudgetEstimator = new PromptBudgetEstimator();
    private final PromptContractDefinition promptContractDefinition = new PromptContractDefinition();
    private final ModelGateway modelGateway;
    private final SessionStore sessionStore;

    ContextSummaryService(PromptAssembler promptAssembler,
                          ModelGateway modelGateway,
                          SessionStore sessionStore) {
        this.promptAssembler = promptAssembler;
        this.modelGateway = modelGateway;
        this.sessionStore = sessionStore;
    }

    boolean maybeSummarize(AgentSession session,
                           SkillDefinition skill,
                           List<String> visibleTools,
                           int currentLoop) {
        PromptPackage promptPackage = buildPromptPackage(session, skill, visibleTools);
        if (promptPackage == null) {
            return false;
        }
        PromptBudgetReport budgetReport = promptBudgetEstimator.estimate(promptPackage, promptContractDefinition);
        if (!shouldSummarize(session, budgetReport)) {
            return false;
        }
        return summarize(session, promptPackage, currentLoop, budgetReport, false);
    }

    boolean summarizeOnLoopFinished(AgentSession session,
                                    SkillDefinition skill,
                                    List<String> visibleTools,
                                    int currentLoop) {
        PromptPackage promptPackage = buildPromptPackage(session, skill, visibleTools);
        if (promptPackage == null) {
            return false;
        }
        PromptBudgetReport budgetReport = promptBudgetEstimator.estimate(promptPackage, promptContractDefinition);
        return summarize(session, promptPackage, currentLoop, budgetReport, true);
    }

    private boolean shouldSummarize(AgentSession session, PromptBudgetReport budgetReport) {
        if (budgetReport == null) {
            return false;
        }
        int estimatedTotalChars = budgetReport.getEstimatedTotalChars();
        int lastTriggeredChars = session == null ? 0 : session.getLastContextSummaryTriggerChars();
        // 单表模式：上下文可达100M，30M开始压缩，每次增量10M
        boolean singleFileMode = session != null && session.isSingleFileMode();
        int triggerChars = singleFileMode ? SINGLE_FILE_TRIGGER_CHARS : SUMMARY_TRIGGER_CHARS;
        int increment = singleFileMode ? SINGLE_FILE_TRIGGER_INCREMENT : SUMMARY_TRIGGER_CHARS;
        int nextThreshold = lastTriggeredChars <= 0
                ? triggerChars
                : lastTriggeredChars + increment;
        return estimatedTotalChars >= triggerChars
                && estimatedTotalChars >= nextThreshold;
    }

    private PromptPackage buildPromptPackage(AgentSession session,
                                             SkillDefinition skill,
                                             List<String> visibleTools) {
        if (session == null) {
            return null;
        }
        return promptAssembler.buildPackage(session, skill, visibleTools);
    }

    private boolean summarize(AgentSession session,
                              PromptPackage promptPackage,
                              int currentLoop,
                              PromptBudgetReport budgetReport,
                              boolean force) {
        if (session == null || promptPackage == null) {
            return false;
        }
        if (!force) {
            List<String> historyToSummarize = session.getChatHistoryBeforeRecentTail(0);
            List<ModelMessage> transcriptToSummarize = session.getTranscriptBeforeLatestBundle();
            if (historyToSummarize.isEmpty() && transcriptToSummarize.isEmpty()) {
                return false;
            }
        }
        String updatedSummary = generateSummary(session, promptPackage);
        if (isBlank(updatedSummary)) {
            return false;
        }
        session.setRuntimeContextSummary(updatedSummary);
        if (budgetReport != null) {
            session.setLastContextSummaryTriggerChars(budgetReport.getEstimatedTotalChars());
        }
        // 摘要生成后，历史只保留当前用户输入和最后一轮模型回复，其余旧记录全部删除。
        session.discardChatHistoryBeforeRecentTail(0);
        session.discardTranscriptBeforeLatestBundle();
        sessionStore.appendEvent(SessionEventFactory.debugTrace(
                session.getSessionId(),
                force ? "context_summary_refreshed_on_loop_finish" : "context_summary_generated",
                "context summary updated at loop=" + currentLoop
                        + ", estimatedTotal=" + (budgetReport == null ? 0 : budgetReport.getEstimatedTotalChars())
                        + ", inputBudget=" + (budgetReport == null ? 0 : budgetReport.getInputBudgetChars())
        ));
        return true;
    }

    private String generateSummary(AgentSession session, PromptPackage promptPackage) {
        ModelRequest request = new ModelRequest();
        request.setSessionId(session == null ? null : session.getSessionId());
        request.setModelConfig(session == null ? null : session.getModelConfig());
        request.setRequestType(ModelRequestType.CONTEXT_SUMMARY);
        request.setMessages(buildSummaryMessages(promptPackage));
        try {
            ModelResponse response = modelGateway.chat(request);
            String content = response == null ? "" : safe(response.getContent());
            if (!content.isEmpty()) {
                return content;
            }
            return response == null ? "" : safe(response.getReasoningContent());
        } catch (RuntimeException exception) {
            if (session != null && sessionStore != null) {
                sessionStore.appendEvent(SessionEventFactory.debugTrace(
                        session.getSessionId(),
                        "context_summary_failed",
                        safe(exception.getMessage())
                ));
            }
            return "";
        }
    }

    private List<ModelMessage> buildSummaryMessages(PromptPackage promptPackage) {
        List<ModelMessage> messages = new ArrayList<ModelMessage>();
        List<ModelMessage> sourceMessages = promptPackage == null ? null : promptPackage.getMessages();
        int index = 0;
        if (sourceMessages != null) {
            while (index < sourceMessages.size()) {
                ModelMessage message = sourceMessages.get(index);
                if (message == null || !message.isSystem()) {
                    break;
                }
                messages.add(copyMessage(message));
                index++;
            }
        }
        messages.add(ModelMessage.system(
                "当前要做的是上下文折叠，而不是继续完成原任务。\n"
                        + "请基于本次请求里已经提供的完整上下文，生成一个摘要用于回灌上下文。\n"
                        + "要求：\n"
                        + "1. 只保留已确认事实、已完成动作、未解决问题、下一步。\n"
                        + "2. 不要保留详细推理过程，不要复述试错过程，不要展开计算细节。\n"
                        + "3. 旧历史会被这条摘要替换，但刚才最新一轮输出不会被替换，因此摘要必须覆盖关键历史信息。\n"
                        + "4. 输出一段简洁中文纯文本，不要 markdown，不要代码块。\n"
                        + "5. 目标是让后续模型能无缝继续当前任务。"
        ));
        if (sourceMessages != null) {
            while (index < sourceMessages.size()) {
                messages.add(copyMessage(sourceMessages.get(index)));
                index++;
            }
        }
        messages.add(ModelMessage.user(
                "请生成一个摘要用于回灌上下文。"
                        + "只保留已确认事实、已完成动作、未解决问题、下一步。"
                        + "不要保留详细推理过程，不要复述试错过程，不要展开计算细节。"
        ));
        return messages;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private ModelMessage copyMessage(ModelMessage source) {
        if (source == null) {
            return null;
        }
        ModelMessage copy = new ModelMessage();
        copy.setRoleEnum(source.getRoleEnum());
        copy.setContent(source.getContent());
        copy.setSummary(source.getSummary());
        copy.setReasoningContent(source.getReasoningContent());
        copy.setToolCallId(source.getToolCallId());
        copy.setToolName(source.getToolName());
        copy.setToolCalls(source.getToolCalls());
        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
