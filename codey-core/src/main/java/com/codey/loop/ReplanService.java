package com.codey.loop;

import com.codey.infra.ModelToolCall;
import com.codey.config.AgentSession;

import java.util.List;

/**
 * 统一处理重规划提示和 assistant transcript 回写。
 */
final class ReplanService {
    void appendFeedbackAndRequestReplan(AgentSession session, String reason) {
        if (session == null) {
            return;
        }
        if (!isBlank(reason)) {
            session.appendSystemFeedback(reason);
        }
        requestReplan(session, reason);
    }

    void requestReplan(AgentSession session, String reason) {
        if (session == null) {
            return;
        }
        session.enterReplanMode(reason);
        session.appendSystemFeedback("请基于规范状态重新规划：优先复用已确认事实，不要重复相同工具请求。");
    }

    void appendAssistantResponse(AgentSession session,
                                 String content,
                                 String reasoningContent,
                                 List<ModelToolCall> toolCalls) {
        if (session == null) {
            return;
        }
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        if (hasToolCalls) {
            session.appendAssistantToolCalls(content, reasoningContent, toolCalls);
            return;
        }
        session.appendAssistantMessage(content, reasoningContent);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
