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

    LoopTurnEngine(LoopTurnPreparer loopTurnPreparer,
                   ModelTurnExecutor modelTurnExecutor,
                   ToolCallProcessor toolCallProcessor,
                   CompletionResultHandler completionResultHandler) {
        this.loopTurnPreparer = loopTurnPreparer;
        this.modelTurnExecutor = modelTurnExecutor;
        this.toolCallProcessor = toolCallProcessor;
        this.completionResultHandler = completionResultHandler;
    }

    TurnExecutionResult executeTurn(AgentSession session,
                                    SkillDefinition skill,
                                    int currentLoop,
                                    LoopProgressTracker progressTracker) {
        LoopTurnPreparer.PreparedTurn preparedTurn = loopTurnPreparer.prepare(session, skill);
        if (!preparedTurn.isReady()) {
            return TurnExecutionResult.finished(TaskResult.failed(session.getSessionId(), preparedTurn.getFailureMessage()));
        }

        ModelTurnExecutor.ModelTurnExecution modelTurn = modelTurnExecutor.executeTurn(
                session,
                preparedTurn.getPromptPackage(),
                preparedTurn.getVisibleTools()
        );
        if (modelTurn.isTerminalFailure()) {
            return TurnExecutionResult.finished(TaskResult.failed(session.getSessionId(), modelTurn.getErrorMessage()));
        }
        if (!modelTurn.isValid()) {
            return TurnExecutionResult.continueLoop();
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
            return TurnExecutionResult.continueLoop();
        }

        ModelTurnExecutor.FinalResponseEvaluation finalResponseEvaluation =
                modelTurnExecutor.evaluateFinalResponse(session, modelResponse);
        if (!finalResponseEvaluation.isValid()) {
            return TurnExecutionResult.continueLoop();
        }

        TaskResult completionResult = completionResultHandler.handleCompletion(
                session,
                skill,
                modelResponse,
                finalResponseEvaluation.getFinalResult()
        );
        if (completionResult != null) {
            return TurnExecutionResult.finished(completionResult);
        }
        return TurnExecutionResult.continueLoop();
    }

    static final class TurnExecutionResult {
        private final boolean finished;
        private final TaskResult taskResult;

        private TurnExecutionResult(boolean finished, TaskResult taskResult) {
            this.finished = finished;
            this.taskResult = taskResult;
        }

        static TurnExecutionResult continueLoop() {
            return new TurnExecutionResult(false, null);
        }

        static TurnExecutionResult finished(TaskResult taskResult) {
            return new TurnExecutionResult(true, taskResult);
        }

        boolean isFinished() {
            return finished;
        }

        TaskResult getTaskResult() {
            return taskResult;
        }
    }
}
