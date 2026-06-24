package com.codey.client;

/**
 * 统一任务/会话执行结果。
 */
public class RunResult {
    private final boolean success;
    private final String sessionId;
    private final String summary;
    private final String errorMessage;

    private RunResult(boolean success, String sessionId, String summary, String errorMessage) {
        this.success = success;
        this.sessionId = sessionId;
        this.summary = summary;
        this.errorMessage = errorMessage;
    }

    public static RunResult success(String sessionId, String summary) {
        return new RunResult(true, sessionId, summary, null);
    }

    public static RunResult failed(String sessionId, String errorMessage) {
        return new RunResult(false, sessionId, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSummary() {
        return summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
