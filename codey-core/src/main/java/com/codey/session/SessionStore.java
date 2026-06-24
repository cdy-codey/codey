package com.codey.session;

import com.codey.client.SessionEvent;

/**
 * 会话事件存储接口，用于记录提示词、工具执行、决策和最终摘要。
 */
public interface SessionStore {
    void appendEvent(SessionEvent event);

    default void completeModelText(String sessionId) {
    }

    default boolean consumeDisplayedFinalSummary(String sessionId) {
        return false;
    }
}
