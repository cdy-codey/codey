package com.codey.loop;

import com.codey.config.AgentSession;
import com.codey.tool.ToolInvocation;

/**
 * 参考成熟引擎的 loop guard，阻止重复成功请求和连续失败抖动。
 */
public class LoopGuard {
    private static final int SAME_REQUEST_REPLAN_THRESHOLD = 2;
    private static final int CONSECUTIVE_FAILURE_HALT_THRESHOLD = 3;

    public LoopGuardDecision inspect(ToolInvocation request, boolean isEditCode, AgentSession session) {
        String requestSignature = ToolRequestSignature.from(request);
        if (isEditCode) {
            return LoopGuardDecision.allow(requestSignature);
        }

        if (session.hasSuccessfulToolRequest(requestSignature)) {
            session.recordDuplicateSuccessfulToolRequest(requestSignature);
            return LoopGuardDecision.skip(
                    "相同工具请求已经成功执行过，请直接复用已有结果，不要重复读取。",
                    requestSignature
            );
        }

        if (session.getToolRequestFailureCount(requestSignature) >= SAME_REQUEST_REPLAN_THRESHOLD) {
            return LoopGuardDecision.replan(
                    "相同工具请求已经连续失败多次，请先调整检索目标、补充上下文或直接给出最终结论，不要原样重试。",
                    requestSignature
            );
        }

        if (session.getConsecutiveToolFailureCount() >= CONSECUTIVE_FAILURE_HALT_THRESHOLD) {
            return LoopGuardDecision.replan(
                    "近期工具调用已经连续失败多次，请先重新审视当前上下文和方案，再决定是否继续调用工具。",
                    requestSignature
            );
        }

        return LoopGuardDecision.allow(requestSignature);
    }

    private String readStringArg(ToolInvocation request, String key) {
        if (request == null || request.getArguments() == null) {
            return "";
        }
        Object value = request.getArguments().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer readIntegerArg(ToolInvocation request, String key) {
        if (request == null || request.getArguments() == null) {
            return null;
        }
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

    public void recordSuccess(AgentSession session, String requestSignature, boolean isEditCode) {
        session.resetConsecutiveToolFailureCount();
        session.clearReplanMode();
        if (isEditCode) {
            session.invalidateToolRequestMemoryAfterEdit();
            return;
        }
        session.rememberSuccessfulToolRequest(requestSignature);
    }

    public void recordFailure(AgentSession session, String requestSignature) {
        session.recordToolFailure(requestSignature);
    }
}
