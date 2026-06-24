package com.codey.verify;

/**
 * 校验结果。
 */
public class VerifyResult {
    public enum Status {
        PASSED,
        FAILED,
        NOT_APPLICABLE
    }

    private final Status status;
    private final String message;

    private VerifyResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public static VerifyResult passed(String message) {
        return new VerifyResult(Status.PASSED, message);
    }

    public static VerifyResult failed(String message) {
        return new VerifyResult(Status.FAILED, message);
    }

    public static VerifyResult notApplicable(String message) {
        return new VerifyResult(Status.NOT_APPLICABLE, message);
    }

    public boolean isPassed() {
        return status == Status.PASSED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isApplicable() {
        return status != Status.NOT_APPLICABLE;
    }

    public boolean isNotApplicable() {
        return status == Status.NOT_APPLICABLE;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
