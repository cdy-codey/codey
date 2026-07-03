package com.codey.web.common;

import java.util.Date;

/**
 * 统一 REST 返回体。
 * 供多个控制器与异常处理器复用，避免放在 api 包下造成职责混淆。
 * SSE 维持原始流输出，不走该包装。
 */
public class ApiResponse<T> {
    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    // 使用 java.util.Date 复用现有 JacksonConfig 的全局日期格式，避免输出时间戳。
    private final Date timestamp;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = new Date();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<T>(true, "OK", message, data);
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data) {
        return new ApiResponse<T>(false, code, message, data);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Date getTimestamp() {
        return timestamp;
    }
}
