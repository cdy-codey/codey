package com.codey.loop;

import com.codey.infra.ModelMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 收敛消息构建阶段共用的文本与 transcript 处理逻辑，避免各阶段各自复制细节。
 */
final class PromptMessageTextSupport {
    private PromptMessageTextSupport() {
    }

    static List<String> selectRecentUnique(List<String> items, int maxItems) {
        List<String> selected = new ArrayList<String>();
        if (items == null || items.isEmpty()) {
            return selected;
        }
        Set<String> seen = new HashSet<String>();
        for (int index = items.size() - 1; index >= 0 && selected.size() < maxItems; index--) {
            String item = items.get(index);
            String normalized = normalizeDuplicateKey(item);
            if (seen.add(normalized)) {
                selected.add(0, item);
            }
        }
        return selected;
    }

    static String detectHistoryRole(String line) {
        if (line != null && line.startsWith("助手:")) {
            return "assistant";
        }
        return "user";
    }

    static String stripHistoryRole(String line) {
        if (isBlank(line)) {
            return "";
        }
        int index = line.indexOf(':');
        if (index < 0 || index + 1 >= line.length()) {
            return line.trim();
        }
        return line.substring(index + 1).trim();
    }

    static String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", "").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength)).trim();
    }

    static void appendUserProvidedFiles(StringBuilder builder, List<String> files) {
        List<String> selected = selectRecentUnique(files, 8);
        if (selected.isEmpty()) {
            return;
        }
        builder.append("补充文件:\n");
        for (String file : selected) {
            builder.append("- ").append(file).append("\n");
        }
    }

    static void appendUserProvidedNotes(StringBuilder builder, List<String> notes) {
        List<String> selected = selectRecentUnique(notes, 8);
        if (selected.isEmpty()) {
            return;
        }
        builder.append("补充说明:\n");
        for (String note : selected) {
            builder.append("- ").append(note).append("\n");
        }
    }

    static ModelMessage sanitizeTranscriptForReplay(ModelMessage transcriptMessage) {
        if (transcriptMessage == null) {
            return null;
        }
        if (transcriptMessage.hasToolCalls() || transcriptMessage.isToolResult()) {
            return transcriptMessage;
        }
        if (transcriptMessage.isAssistant()
                && (!isBlank(transcriptMessage.getContent())
                || !isBlank(transcriptMessage.getReasoningContent()))) {
            return ModelMessage.assistant(
                    isBlank(transcriptMessage.getContent()) ? "" : transcriptMessage.getContent(),
                    isBlank(transcriptMessage.getReasoningContent()) ? "" : transcriptMessage.getReasoningContent()
            );
        }
        return null;
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalizeDuplicateKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "").replaceAll("\\s+", " ").trim();
    }
}
