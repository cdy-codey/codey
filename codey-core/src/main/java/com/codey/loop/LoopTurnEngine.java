package com.codey.loop;

import com.codey.infra.ModelResponse;
import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.codey.task.TaskResult;

/**
 * 驱动单轮 loop，从准备 prompt 到处理 tool/result，返回本轮结果。
 */
final class LoopTurnEngine {
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
            toolCallProcessor.processToolCalls(
                    modelResponse.getToolCalls(),
                    session,
                    skill,
                    modelResponse.getContent(),
                    modelResponse.getReasoningContent()
            );
            LoopProgressTracker.SessionProgressSnapshot afterProgress = progressTracker.snapshot(session);
            progressTracker.markProgress(currentLoop, beforeProgress, afterProgress);
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
