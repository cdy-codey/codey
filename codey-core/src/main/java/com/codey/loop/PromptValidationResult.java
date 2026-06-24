package com.codey.loop;

/**
 * 提示词契约校验结果。
 */
public class PromptValidationResult {
    private final boolean passed;
    private final String message;
    private final String diagnostic;

    private PromptValidationResult(boolean passed, String message, String diagnostic) {
        this.passed = passed;
        this.message = message;
        this.diagnostic = diagnostic;
    }

    public static PromptValidationResult passed(String message) {
        return new PromptValidationResult(true, message, null);
    }

    public static PromptValidationResult passed(String message, String diagnostic) {
        return new PromptValidationResult(true, message, diagnostic);
    }

    public static PromptValidationResult failed(String message) {
        return new PromptValidationResult(false, message, null);
    }

    public static PromptValidationResult failed(String message, String diagnostic) {
        return new PromptValidationResult(false, message, diagnostic);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
