package com.codey.client;

/**
 * 会话式调用返回的会话标识。
 */
public class ChatSession {
    private final String sessionId;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
