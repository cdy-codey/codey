package com.codey.loop;

/**
 * 模型响应 contract 校验结果。
 */
public class ResponseValidationResult {
    private final boolean passed;
    private final String message;

    private ResponseValidationResult(boolean passed, String message) {
        this.passed = passed;
        this.message = message;
    }

    public static ResponseValidationResult passed(String message) {
        return new ResponseValidationResult(true, message);
    }

    public static ResponseValidationResult failed(String message) {
        return new ResponseValidationResult(false, message);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}
