package com.codey.client;

/**
 * 统一会话事件 DTO。
 * 用于在控制台、接口返回和日志存储之间共享同一份事件语义。
 */
public class SessionEvent {
    private final String sessionId;
    private final SessionEventType type;
    private final String stage;
    private final String message;
    private final Object payload;

    public SessionEvent(String sessionId,
                             SessionEventType type,
                             String stage,
                             String message,
                             Object payload) {
        this.sessionId = sessionId;
        this.type = type;
        this.stage = stage;
        this.message = message;
        this.payload = payload;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionEventType getType() {
        return type;
    }

    public String getStage() {
        return stage;
    }

    public String getMessage() {
        return message;
    }

    public Object getPayload() {
        return payload;
    }
}
