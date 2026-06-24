package com.codey.client;

/**
 * 会话事件监听器。
 * 接入方可通过实现该接口订阅统一事件流，再转发给 WebSocket、SSE 或业务日志系统。
 */
public interface SessionEventListener {
    void onEvent(SessionEvent event);
}
