package com.codey.loop;

import com.codey.infra.ModelResponse;
import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.codey.task.TaskResult;
import com.codey.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 驱动单轮 loop，从准备 prompt 到处理 tool/result，返回本轮结果。
 */
final class LoopTurnEngine {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LoopTurnPreparer loopTurnPreparer;
    private final ModelTurnExecutor modelTurnExecutor;
    private final ToolCallProcessor toolCallProcessor;
    private final CompletionResultHandler completionResultHandler;
    private final ContextSummaryService contextSummaryService;

    LoopTurnEngine(LoopTurnPreparer loopTurnPreparer,
                   ModelTurnExecutor modelTurnExecutor,
                   ToolCallProcessor toolCallProcessor,
                   CompletionResultHandler completionResultHandler,
                   ContextSummaryService contextSummaryService) {
        this.loopTurnPreparer = loopTurnPreparer;
        this.modelTurnExecutor = modelTurnExecutor;
        this.toolCallProcessor = toolCallProcessor;
        this.completionResultHandler = completionResultHandler;
        this.contextSummaryService = contextSummaryService;
    }

    TurnExecutionResult executeTurn(AgentSession session,
                                    SkillDefinition skill,
                                    int currentLoop,
                                    int currentCallSequence,
                                    LoopProgressTracker progressTracker) {
        LoopTurnPreparer.PreparedTurn preparedTurn = loopTurnPreparer.prepare(session, skill, currentLoop);
        if (!preparedTurn.isReady()) {
            return TurnExecutionResult.finished(TaskResult.failed(session.getSessionId(), preparedTurn.getFailureMessage()));
        }

        // 首次模型调用使用当前轮次序号
        int callSeq = currentCallSequence;
        ModelTurnExecutor.ModelTurnExecution modelTurn = modelTurnExecutor.executeTurn(
                session,
                preparedTurn.getPromptPackage(),
                preparedTurn.getVisibleTools(),
                currentLoop,
                callSeq
        );

        callSeq++; // 为可能的连续调用递增序号

        if (modelTurn.isTerminalFailure()) {
            return TurnExecutionResult.finished(TaskResult.failed(session.getSessionId(), modelTurn.getErrorMessage()));
        }
        if (!modelTurn.isValid()) {
            return TurnExecutionResult.continueLoop(callSeq);
        }

        ModelResponse modelResponse = modelTurn.getModelResponse();
        if (modelResponse.hasToolCalls()) {
            LoopProgressTracker.SessionProgressSnapshot beforeProgress = progressTracker.snapshot(session);
            ToolResult pendingUserChoice = toolCallProcessor.processToolCalls(
                    modelResponse.getToolCalls(),
                    session,
                    skill,
                    modelResponse.getContent(),
                    modelResponse.getReasoningContent()
            );
            LoopProgressTracker.SessionProgressSnapshot afterProgress = progressTracker.snapshot(session);
            progressTracker.markProgress(currentLoop, beforeProgress, afterProgress);
            // 写工具成功且通过校验后，无需再让模型多跑一轮输出 FINISH，直接在本轮完成。
            if (session != null && session.isVerifiedWriteAutoComplete()) {
                TaskResult completionResult = completeAutoCompletedTurn(session, skill, modelResponse);
                if (completionResult != null) {
                    return TurnExecutionResult.finished(completionResult);
                }
            }
            // 工具要求用户先选择（如校验未通过），直接在本轮以 user_choice 视图结束并等待用户选择。
            if (pendingUserChoice != null && pendingUserChoice.isUserChoice()) {
                TaskResult completionResult = completeWithUserChoice(session, skill, modelResponse, pendingUserChoice.getView());
                if (completionResult != null) {
                    return TurnExecutionResult.finished(completionResult);
                }
            }
            return TurnExecutionResult.continueLoop(callSeq);
        }

        ModelTurnExecutor.FinalResponseEvaluation finalResponseEvaluation =
                modelTurnExecutor.evaluateFinalResponse(session, modelResponse);
        if (!finalResponseEvaluation.isValid()) {
            return TurnExecutionResult.continueLoop(callSeq);
        }

        TaskResult completionResult = completionResultHandler.handleCompletion(
                session,
                skill,
                modelResponse,
                finalResponseEvaluation.getFinalResult()
        );
        if (completionResult != null) {
            if (contextSummaryService != null) {
                contextSummaryService.summarizeOnLoopFinished(
                        session,
                        skill,
                        preparedTurn.getVisibleTools(),
                        currentLoop
                );
            }
            return TurnExecutionResult.finished(completionResult);
        }
        return TurnExecutionResult.continueLoop(callSeq);
    }

    /**
     * 写工具成功并通过校验后的自动完成：构造一个最小的 FINISH 结果，复用正常完成链路，
     * 保证 final_summary / task_status 等事件与普通完成态完全一致，仅省去一次模型往返。
     * 表单填写属于短任务，跳过上下文摘要调度，避免在任务结束时再触发一次模型调用。
     */
    private TaskResult completeAutoCompletedTurn(AgentSession session,
                                                SkillDefinition skill,
                                                ModelResponse modelResponse) {
        // 业务技能可通过 autoCompleteSummary 定制最终文案；未配置时回退为通用中性文案。
        String summary = resolveAutoCompleteSummary(skill);
        FinalResult finalResult = new FinalResult();
        finalResult.setStatus("FINISH");
        finalResult.setSummary(summary);
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("_view_type", "text");
        view.put("content", summary);
        finalResult.setView(view);

        ModelResponse syntheticResponse = new ModelResponse();
        syntheticResponse.setContent(summary);
        syntheticResponse.setReasoningContent(modelResponse == null ? "" : modelResponse.getReasoningContent());

        return completionResultHandler.handleCompletion(
                session,
                skill,
                syntheticResponse,
                finalResult
        );
    }

    private String resolveAutoCompleteSummary(SkillDefinition skill) {
        if (skill != null) {
            String summary = skill.getAutoCompleteSummary();
            if (summary != null && !summary.trim().isEmpty()) {
                return summary.trim();
            }
        }
        return "已完成文件写入并通过校验。";
    }

    /**
     * 工具要求用户先选择（如校验未通过列出问题）时的完成态：复用正常完成链路，
     * 用工具返回的 user_choice 视图构造最终结果，让本轮暂停并等待用户选择。
     */
    private TaskResult completeWithUserChoice(AgentSession session,
                                              SkillDefinition skill,
                                              ModelResponse modelResponse,
                                              Map<String, Object> view) {
        FinalResult finalResult = new FinalResult();
        finalResult.setStatus("FINISH");
        finalResult.setView(view);

        // 内容写成与模型原生 FINISH 一致的 JSON，便于前端按 user_choice 协议渲染。
        Map<String, Object> envelope = new LinkedHashMap<String, Object>();
        envelope.put("status", "FINISH");
        envelope.put("view", view);
        String content;
        try {
            content = OBJECT_MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            content = "{\"status\":\"FINISH\",\"view\":{\"_view_type\":\"user_choice\",\"title\":\"请选择\",\"options\":[]}}";
        }

        ModelResponse syntheticResponse = new ModelResponse();
        syntheticResponse.setContent(content);
        syntheticResponse.setReasoningContent(modelResponse == null ? "" : modelResponse.getReasoningContent());

        return completionResultHandler.handleCompletion(
                session,
                skill,
                syntheticResponse,
                finalResult
        );
    }

    static final class TurnExecutionResult {
        private final boolean finished;
        private final TaskResult taskResult;
        /** 下一轮模型调用的起始序号 */
        private final int nextCallSequence;

        private TurnExecutionResult(boolean finished, TaskResult taskResult, int nextCallSequence) {
            this.finished = finished;
            this.taskResult = taskResult;
            this.nextCallSequence = nextCallSequence;
        }

        static TurnExecutionResult continueLoop(int nextCallSequence) {
            return new TurnExecutionResult(false, null, nextCallSequence);
        }

        static TurnExecutionResult finished(TaskResult taskResult) {
            return new TurnExecutionResult(true, taskResult, 0);
        }

        boolean isFinished() {
            return finished;
        }

        TaskResult getTaskResult() {
            return taskResult;
        }

        int getNextCallSequence() {
            return nextCallSequence;
        }
    }
}
