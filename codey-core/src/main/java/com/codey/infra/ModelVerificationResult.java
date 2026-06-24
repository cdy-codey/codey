package com.codey.infra;

/**
 * 模型接入验证结果。
 */
public class ModelVerificationResult {
    private final boolean success;
    private final String message;
    private final String rawOutput;

    private ModelVerificationResult(boolean success, String message, String rawOutput) {
        this.success = success;
        this.message = message;
        this.rawOutput = rawOutput;
    }

    public static ModelVerificationResult success(String message, String rawOutput) {
        return new ModelVerificationResult(true, message, rawOutput);
    }

    public static ModelVerificationResult failure(String message, String rawOutput) {
        return new ModelVerificationResult(false, message, rawOutput);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getRawOutput() {
        return rawOutput;
    }
}
