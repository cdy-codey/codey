package com.codey.verify;

/**
 * 封装当前会话解析出的页面目标和 API 规格文件。
 */
final class VerificationTarget {
    private final String targetFile;
    private final String apiSpecFile;

    VerificationTarget(String targetFile, String apiSpecFile) {
        this.targetFile = targetFile;
        this.apiSpecFile = apiSpecFile;
    }

    String getTargetFile() {
        return targetFile;
    }

    String getApiSpecFile() {
        return apiSpecFile;
    }

    boolean hasTargetFile() {
        return !isBlank(targetFile);
    }

    boolean hasApiSpecFile() {
        return !isBlank(apiSpecFile);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
