package com.codey.session;

import com.codey.infra.ModelToolCall;
import com.codey.loop.HumanDecision;
import com.codey.client.SessionEvent;
import com.codey.client.SessionEventType;
import com.codey.tools.ToolInvocation;
import com.codey.verify.VerifyResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一构建会话事件 DTO，避免控制台和日志层各自拼装事件结构。
 */
public final class SessionEventFactory {
    private static final int DEBUG_TRACE_PREVIEW_LIMIT = 400;

    private SessionEventFactory() {
    }

    public static SessionEvent modelInput(String sessionId, Object input) {
        return new SessionEvent(
                sessionId,
                SessionEventType.MODEL_INPUT,
                null,
                null,
                input
        );
    }

    public static SessionEvent modelOutput(String sessionId, String rawOutput) {
        return new SessionEvent(
                sessionId,
                SessionEventType.MODEL_OUTPUT,
                null,
                rawOutput,
                singletonPayload("rawOutput", rawOutput)
        );
    }

    public static SessionEvent modelTextDelta(String sessionId, String delta) {
        return new SessionEvent(
                sessionId,
                SessionEventType.MODEL_TEXT_DELTA,
                null,
                delta,
                singletonPayload("delta", delta)
        );
    }

    public static SessionEvent modelThinkingDelta(String sessionId, String delta) {
        return new SessionEvent(
                sessionId,
                SessionEventType.MODEL_THINKING_DELTA,
                null,
                delta,
                singletonPayload("delta", delta)
        );
    }

    public static SessionEvent modelToolCallStarted(String sessionId, ModelToolCall toolCall) {
        String toolName = toolCall == null ? null : toolCall.getName();
        return new SessionEvent(
                sessionId,
                SessionEventType.MODEL_TOOL_CALL_STARTED,
                null,
                toolName,
                toolCall
        );
    }

    public static SessionEvent toolExecutionStarted(String sessionId, Object request) {
        String toolName = request instanceof ToolInvocation ? ((ToolInvocation) request).getToolName() : null;
        return new SessionEvent(
                sessionId,
                SessionEventType.TOOL_EXECUTION_STARTED,
                null,
                toolName,
                request
        );
    }

    public static SessionEvent debugTrace(String sessionId, String stage, String content) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("stage", stage);
        payload.put("content", abbreviate(content));
        return new SessionEvent(
                sessionId,
                SessionEventType.DEBUG_TRACE,
                stage,
                abbreviate(content),
                payload
        );
    }

    public static SessionEvent toolCall(String sessionId, Object request, Object result) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("request", request);
        payload.put("result", result);
        String toolName = request instanceof ToolInvocation ? ((ToolInvocation) request).getToolName() : null;
        return new SessionEvent(
                sessionId,
                SessionEventType.TOOL_CALL,
                null,
                toolName,
                payload
        );
    }

    public static SessionEvent humanDecision(String sessionId, Object decision) {
        return new SessionEvent(
                sessionId,
                SessionEventType.HUMAN_DECISION,
                null,
                buildHumanDecisionMessage(decision),
                decision
        );
    }

    public static SessionEvent verification(String sessionId, Object verifyResult) {
        return new SessionEvent(
                sessionId,
                SessionEventType.VERIFICATION,
                null,
                buildVerificationMessage(verifyResult),
                verifyResult
        );
    }

    public static SessionEvent securityEvent(String sessionId, String message) {
        return new SessionEvent(
                sessionId,
                SessionEventType.SECURITY_EVENT,
                null,
                message,
                singletonPayload("message", message)
        );
    }

    public static SessionEvent finalSummary(String sessionId, String summary) {
        return new SessionEvent(
                sessionId,
                SessionEventType.FINAL_SUMMARY,
                null,
                summary,
                singletonPayload("summary", summary)
        );
    }

    @SuppressWarnings("unchecked")
    public static Object payloadValue(SessionEvent event, String key) {
        if (event == null || !(event.getPayload() instanceof Map<?, ?>)) {
            return null;
        }
        return ((Map<String, Object>) event.getPayload()).get(key);
    }

    private static String buildHumanDecisionMessage(Object decision) {
        if (!(decision instanceof HumanDecision)) {
            return null;
        }
        HumanDecision humanDecision = (HumanDecision) decision;
        StringBuilder message = new StringBuilder();
        if (humanDecision.isApproved()) {
            message.append("人工已确认本次修改");
        } else {
            message.append("人工拒绝本次修改");
        }
        if (!isBlank(humanDecision.getFeedback())) {
            message.append("：").append(humanDecision.getFeedback());
        }
        return message.toString();
    }

    private static String buildVerificationMessage(Object verifyResult) {
        if (!(verifyResult instanceof VerifyResult)) {
            return null;
        }
        VerifyResult result = (VerifyResult) verifyResult;
        if (!result.isFailed()) {
            return "校验通过";
        }
        if (isBlank(result.getMessage()) || "ok".equalsIgnoreCase(result.getMessage().trim())) {
            return "校验未通过";
        }
        return "校验未通过：" + result.getMessage();
    }

    private static Map<String, Object> singletonPayload(String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(key, value);
        return payload;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= DEBUG_TRACE_PREVIEW_LIMIT) {
            return value;
        }
        return value.substring(0, DEBUG_TRACE_PREVIEW_LIMIT) + "...(truncated)";
    }
}
