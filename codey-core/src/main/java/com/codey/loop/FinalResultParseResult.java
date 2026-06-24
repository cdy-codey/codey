package com.codey.loop;

/**
 * 最终结果解析结果。
 */
public class FinalResultParseResult {
    private boolean success;
    private FinalResult finalResult;
    private String errorMessage;

    public static FinalResultParseResult success(FinalResult finalResult) {
        FinalResultParseResult result = new FinalResultParseResult();
        result.success = true;
        result.finalResult = finalResult;
        return result;
    }

    public static FinalResultParseResult failure(String errorMessage) {
        FinalResultParseResult result = new FinalResultParseResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public FinalResult getFinalResult() {
        return finalResult;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
