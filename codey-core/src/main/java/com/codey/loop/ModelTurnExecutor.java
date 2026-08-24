package com.codey.loop;

import com.codey.infra.ModelGateway;
import com.codey.infra.ModelRequest;
import com.codey.infra.ModelRequestType;
import com.codey.infra.ModelResponse;
import com.codey.infra.ModelStreamListener;
import com.codey.infra.ModelToolCall;
import com.codey.config.AgentSession;
import com.codey.config.ModelProperties;
import com.codey.session.SessionEventFactory;
import com.codey.session.SessionStore;
import com.codey.tools.ToolRegistry;

import java.util.List;

/**
 * 负责单轮模型请求构建、调用和响应契约校验。
 */
final class ModelTurnExecutor {
    // 未显式配置 max_tokens 时兜底为模型允许的最大值（DeepSeek V4 单次输出上限 384K），
    // 避免表单模式一次性写入大文件时因单次输出被截断导致 content 缺失。
    private static final int DEFAULT_MAX_TOKENS = 384000;

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
                                   List<String> visibleTools,
                                   int currentLoop,
                                   int callSequence) {
        ModelRequest modelRequest = buildModelRequest(session, promptPackage, visibleTools);
        modelRequest.setTimingLoopNumber(currentLoop);
        modelRequest.setTimingCallSequence(callSequence);
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
        request.setModelConfig(resolveRequestModelConfig(session));
        request.setIncludeThinking(session == null || session.isIncludeThinking());
        request.setRequestType(ModelRequestType.BUSINESS);
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

    /**
     * 复制会话模型配置为请求级独立副本，避免请求内修改污染共享的会话/启动配置对象。
     * max_tokens 未显式配置时兜底为模型最大输出上限，保证大文件写入不被单次输出截断；
     * 业务方仍可通过任务/会话配置显式覆盖（显式配置优先）。
     */
    private ModelProperties resolveRequestModelConfig(AgentSession session) {
        ModelProperties source = session == null ? null : session.getModelConfig();
        ModelProperties copy = new ModelProperties();
        if (source != null) {
            copy.setProvider(source.getProvider());
            copy.setEndpoint(source.getEndpoint());
            copy.setModelName(source.getModelName());
            copy.setApiKey(source.getApiKey());
            copy.setApiKeyEnv(source.getApiKeyEnv());
            copy.setTemperature(source.getTemperature());
            copy.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
            copy.setReadTimeoutMillis(source.getReadTimeoutMillis());
            copy.setMaxRetries(source.getMaxRetries());
            copy.setMaxTokens(source.getMaxTokens());
        }
        if (copy.getMaxTokens() == null) {
            copy.setMaxTokens(DEFAULT_MAX_TOKENS);
        }
        return copy;
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
