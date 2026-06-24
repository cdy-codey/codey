package com.codey.verify;

/**
 * 在启发式校验前剥离明显的注释噪声。
 */
final class VerificationTextSanitizer {

    String stripComments(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String normalized = content.replace("\r", "");
        normalized = normalized.replaceAll("(?s)<!--.*?-->", " ");
        normalized = normalized.replaceAll("(?s)/\\*.*?\\*/", " ");
        normalized = normalized.replaceAll("(?m)^\\s*//.*$", " ");
        return normalized;
    }
}
