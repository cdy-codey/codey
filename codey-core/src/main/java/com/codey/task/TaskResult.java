package com.codey.task;

/**
 * 任务执行后的统一返回结果。
 */
public class TaskResult {
    private final boolean success;
    private final String sessionId;
    private final String summary;
    private final String errorMessage;

    private TaskResult(boolean success, String sessionId, String summary, String errorMessage) {
        this.success = success;
        this.sessionId = sessionId;
        this.summary = summary;
        this.errorMessage = errorMessage;
    }

    public static TaskResult finished(String sessionId, String summary) {
        return new TaskResult(true, sessionId, summary, null);
    }

    public static TaskResult failed(String sessionId, String errorMessage) {
        return new TaskResult(false, sessionId, null, errorMessage);
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
