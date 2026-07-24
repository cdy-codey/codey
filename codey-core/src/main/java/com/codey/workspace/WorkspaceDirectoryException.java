package com.codey.workspace;

/**
 * 通用工作区目录服务异常。
 * 底层只表达错误语义，不直接依赖 Web 层状态码。
 */
public class WorkspaceDirectoryException extends RuntimeException {
    private final ErrorType errorType;

    public WorkspaceDirectoryException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType == null ? ErrorType.INTERNAL_ERROR : errorType;
    }

    public WorkspaceDirectoryException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType == null ? ErrorType.INTERNAL_ERROR : errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public enum ErrorType {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
        INTERNAL_ERROR
    }
}
