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
        // 工具调用与工具结果原样回放，保持 tool_calls 与 tool 结果成对。
        if (transcriptMessage.hasToolCalls() || transcriptMessage.isToolResult()) {
            return transcriptMessage;
        }
        // assistant 消息保留最终答复与思考（reasoning），思考作为跨轮上下文回传。
        if (transcriptMessage.isAssistant()
                && (!isBlank(transcriptMessage.getContent())
                || !isBlank(transcriptMessage.getReasoningContent()))) {
            return ModelMessage.assistant(
                    isBlank(transcriptMessage.getContent()) ? "" : transcriptMessage.getContent(),
                    isBlank(transcriptMessage.getReasoningContent()) ? "" : transcriptMessage.getReasoningContent()
            );
        }
        // user 消息作为完整时间线中的用户输入回放。
        if (transcriptMessage.isUser() && !isBlank(transcriptMessage.getContent())) {
            return ModelMessage.user(transcriptMessage.getContent());
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
