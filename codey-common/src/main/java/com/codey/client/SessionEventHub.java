package com.codey.client;

import java.util.List;

/**
 * 会话事件中心。
 * 业务侧可按 sessionId 订阅事件，并读取当前会话已缓存的事件快照。
 */
public interface SessionEventHub {
    SessionEventSubscription subscribe(String sessionId, SessionEventListener listener);

    List<SessionEvent> snapshot(String sessionId);

    void clear(String sessionId);
}
