package com.codey.loop;

import com.codey.infra.ModelGateway;
import com.codey.infra.ModelRequest;
import com.codey.infra.ModelResponse;
import com.codey.infra.ModelStreamListener;
import com.codey.infra.ModelToolCall;
import com.codey.config.AgentSession;
import com.codey.session.SessionEventFactory;
import com.codey.session.SessionStore;
import com.codey.tools.ToolRegistry;

import java.util.List;

/**
 * 负责单轮模型请求构建、调用和响应契约校验。
 */
final class ModelTurnExecutor {
    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final ResponseContractValidator responseContractValidator;
    private final FinalResultInterpreter finalResultInterpreter;
    private final SessionStore sessionStore;
    private final ReplanService replanService;

    ModelTurnExecutor(ModelGateway modelGateway,
                      ToolRegistry toolRegistry,
                      ResponseContractValidator responseContractValidator,
                      FinalResultInterpreter finalResultInterpreter,
                      SessionStore sessionStore,
                      ReplanService replanService) {
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.responseContractValidator = responseContractValidator;
        this.finalResultInterpreter = finalResultInterpreter;
        this.sessionStore = sessionStore;
        this.replanService = replanService;
    }

    ModelTurnExecution executeTurn(AgentSession session,
                                   PromptPackage promptPackage,
                                   List<String> visibleTools) {
        ModelRequest modelRequest = buildModelRequest(session, promptPackage, visibleTools);
        ModelResponse modelResponse;
        try {
            modelResponse = modelGateway.chat(modelRequest);
        } catch (RuntimeException exception) {
            String errorMessage = buildModelFailureMessage(exception);
            sessionStore.appendEvent(SessionEventFactory.securityEvent(session.getSessionId(), errorMessage));
            return ModelTurnExecution.failed(errorMessage);
        }
        sessionStore.completeModelText(session.getSessionId());
        sessionStore.appendEvent(SessionEventFactory.modelOutput(session.getSessionId(), safe(modelResponse.getRawResponse())));
        ResponseValidationResult rawResponseValidation = responseContractValidator.validateModelResponse(modelResponse);
        if (!rawResponseValidation.isPassed()) {
            replanService.appendFeedbackAndRequestReplan(session, rawResponseValidation.getMessage());
            sessionStore.appendEvent(SessionEventFactory.securityEvent(session.getSessionId(), rawResponseValidation.getMessage()));
            return ModelTurnExecution.invalid(modelResponse);
        }
        return ModelTurnExecution.valid(modelResponse);
    }

    private String buildModelFailureMessage(RuntimeException exception) {
        if (exception == null || isBlank(exception.getMessage())) {
            return "模型调用失败";
        }
        return exception.getMessage();
    }

    FinalResponseEvaluation evaluateFinalResponse(AgentSession session, ModelResponse modelResponse) {
        FinalResultParseResult parseResult = finalResultInterpreter.parse(modelResponse.getContent());
        if (!parseResult.isSuccess()) {
            replanService.appendFeedbackAndRequestReplan(session, parseResult.getErrorMessage());
            sessionStore.appendEvent(SessionEventFactory.securityEvent(session.getSessionId(), parseResult.getErrorMessage()));
            return FinalResponseEvaluation.invalid();
        }

        FinalResult finalResult = parseResult.getFinalResult();
        ResponseValidationResult parsedResponseValidation = responseContractValidator.validateFinalResult(finalResult);
        if (!parsedResponseValidation.isPassed()) {
            replanService.appendFeedbackAndRequestReplan(session, parsedResponseValidation.getMessage());
            sessionStore.appendEvent(SessionEventFactory.securityEvent(session.getSessionId(), parsedResponseValidation.getMessage()));
            return FinalResponseEvaluation.invalid();
        }
        return FinalResponseEvaluation.valid(finalResult);
    }

    private ModelRequest buildModelRequest(final AgentSession session,
                                           PromptPackage promptPackage,
                                           List<String> visibleTools) {
        ModelRequest request = new ModelRequest();
        final String sessionId = session == null ? null : session.getSessionId();
        request.setSessionId(sessionId);
        request.setMessages(promptPackage == null ? null : promptPackage.getMessages());
        request.setTools(toolRegistry.getToolDefinitions(visibleTools));
        request.setStreamListener(new ModelStreamListener() {
            @Override
            public void onTextDelta(String delta) {
                sessionStore.appendEvent(SessionEventFactory.modelTextDelta(sessionId, delta));
            }

            @Override
            public void onThinkingDelta(String delta) {
                sessionStore.appendEvent(SessionEventFactory.modelThinkingDelta(sessionId, delta));
            }

            @Override
            public void onToolCallStarted(ModelToolCall toolCall) {
                sessionStore.appendEvent(SessionEventFactory.modelToolCallStarted(sessionId, toolCall));
            }
        });
        return request;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class ModelTurnExecution {
        private final boolean valid;
        private final boolean terminalFailure;
        private final ModelResponse modelResponse;
        private final String errorMessage;

        private ModelTurnExecution(boolean valid, boolean terminalFailure, ModelResponse modelResponse, String errorMessage) {
            this.valid = valid;
            this.terminalFailure = terminalFailure;
            this.modelResponse = modelResponse;
            this.errorMessage = errorMessage;
        }

        static ModelTurnExecution valid(ModelResponse modelResponse) {
            return new ModelTurnExecution(true, false, modelResponse, null);
        }

        static ModelTurnExecution invalid(ModelResponse modelResponse) {
            return new ModelTurnExecution(false, false, modelResponse, null);
        }

        static ModelTurnExecution failed(String errorMessage) {
            return new ModelTurnExecution(false, true, null, errorMessage);
        }

        boolean isValid() {
            return valid;
        }

        boolean isTerminalFailure() {
            return terminalFailure;
        }

        ModelResponse getModelResponse() {
            return modelResponse;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }

    static final class FinalResponseEvaluation {
        private final boolean valid;
        private final FinalResult finalResult;

        private FinalResponseEvaluation(boolean valid, FinalResult finalResult) {
            this.valid = valid;
            this.finalResult = finalResult;
        }

        static FinalResponseEvaluation valid(FinalResult finalResult) {
            return new FinalResponseEvaluation(true, finalResult);
        }

        static FinalResponseEvaluation invalid() {
            return new FinalResponseEvaluation(false, null);
        }

        boolean isValid() {
            return valid;
        }

        FinalResult getFinalResult() {
            return finalResult;
        }
    }
}
