package com.codey.config;

import com.codey.infra.ModelMessage;
import com.codey.infra.ModelToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 承载工具结果、编辑结果和模型 transcript 等执行期状态。
 */
final class ExecutionState {
    private final int maxModelTranscript;
    private final List<String> toolResults = new ArrayList<String>();
    private final List<String> editResults = new ArrayList<String>();
    private final List<String> systemFeedback = new ArrayList<String>();
    private final List<ModelMessage> modelTranscript = new ArrayList<ModelMessage>();

    ExecutionState(int maxModelTranscript) {
        this.maxModelTranscript = maxModelTranscript;
    }

    List<String> getToolResults() {
        return Collections.unmodifiableList(toolResults);
    }

    List<String> getEditResults() {
        return Collections.unmodifiableList(editResults);
    }

    List<String> getSystemFeedback() {
        return Collections.unmodifiableList(systemFeedback);
    }

    List<ModelMessage> getModelTranscript() {
        return Collections.unmodifiableList(modelTranscript);
    }

    List<ModelMessage> getTranscriptBeforeLatestBundle() {
        int cutoff = latestBundleStartIndex();
        if (cutoff <= 0) {
            return new ArrayList<ModelMessage>();
        }
        return new ArrayList<ModelMessage>(modelTranscript.subList(0, cutoff));
    }

    void discardTranscriptBeforeLatestBundle() {
        int cutoff = latestBundleStartIndex();
        if (cutoff <= 0) {
            return;
        }
        modelTranscript.subList(0, cutoff).clear();
    }

    void appendToolResult(String content) {
        toolResults.add(content);
    }

    void appendEditResult(String content) {
        editResults.add(content);
    }

    void appendSystemFeedback(String content) {
        systemFeedback.add(content);
    }

    String getLatestAssistantResponseContent() {
        for (int index = modelTranscript.size() - 1; index >= 0; index--) {
            ModelMessage message = modelTranscript.get(index);
            if (message == null || !message.isAssistant()) {
                continue;
            }
            String content = safe(message.getContent());
            if (!content.isEmpty()) {
                return content;
            }
        }
        return "";
    }

    void appendAssistantToolCalls(List<ModelToolCall> toolCalls) {
        appendAssistantToolCalls("", "", toolCalls);
    }

    void appendAssistantToolCalls(String content, String reasoningContent, List<ModelToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        appendModelTranscript(ModelMessage.assistantToolCalls(content, reasoningContent, toolCalls));
    }

    void appendAssistantMessage(String content, String reasoningContent) {
        if ((content == null || content.trim().isEmpty())
                && (reasoningContent == null || reasoningContent.trim().isEmpty())) {
            return;
        }
        appendModelTranscript(ModelMessage.assistant(content, reasoningContent));
    }

    void appendToolResultMessage(String toolCallId, String toolName, String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        // 工具角色只承载真实工具返回，避免把系统反馈混入转录记录。
        appendModelTranscript(ModelMessage.toolResult(toolCallId, toolName, content));
    }

    /**
     * 结束一轮 chat turn 后清空执行期产物，避免下一轮继续回放上一轮的工具细节。
     */
    void resetForNextTurn() {
        toolResults.clear();
        editResults.clear();
        systemFeedback.clear();
        modelTranscript.clear();
    }

    private void appendModelTranscript(ModelMessage message) {
        if (message == null) {
            return;
        }
        modelTranscript.add(message);
        trimModelTranscript();
    }

    /**
     * 裁剪 transcript 时保持 tool_calls 与 tool 结果成对，避免回放孤儿消息。
     */
    private void trimModelTranscript() {
        while (modelTranscript.size() > maxModelTranscript) {
            modelTranscript.remove(0);
            dropLeadingOrphanToolMessages();
        }
        dropLeadingOrphanToolMessages();
    }

    private void dropLeadingOrphanToolMessages() {
        while (!modelTranscript.isEmpty()) {
            ModelMessage first = modelTranscript.get(0);
            if (first == null || !first.isToolResult()) {
                break;
            }
            modelTranscript.remove(0);
        }
        removeOrphanToolMessagesInsideTranscript();
    }

    private void removeOrphanToolMessagesInsideTranscript() {
        Set<String> activeToolCallIds = new HashSet<String>();
        List<ModelMessage> sanitized = new ArrayList<ModelMessage>();
        for (ModelMessage message : modelTranscript) {
            if (message == null) {
                continue;
            }
            if (message.hasToolCalls()) {
                activeToolCallIds.clear();
                for (ModelToolCall toolCall : message.getToolCalls()) {
                    if (toolCall != null && toolCall.getId() != null && !toolCall.getId().trim().isEmpty()) {
                        activeToolCallIds.add(toolCall.getId());
                    }
                }
                sanitized.add(message);
                continue;
            }
            if (message.isToolResult()) {
                if (message.getToolCallId() != null && activeToolCallIds.contains(message.getToolCallId())) {
                    sanitized.add(message);
                }
                continue;
            }
            activeToolCallIds.clear();
            sanitized.add(message);
        }
        modelTranscript.clear();
        modelTranscript.addAll(sanitized);
    }

    /**
     * 保留最近一次助手输出及其对应 tool 结果，前面的 transcript 允许被摘要折叠。
     */
    private int latestBundleStartIndex() {
        if (modelTranscript.isEmpty()) {
            return 0;
        }
        int lastIndex = modelTranscript.size() - 1;
        ModelMessage lastMessage = modelTranscript.get(lastIndex);
        if (lastMessage == null) {
            return lastIndex;
        }
        if (lastMessage.isToolResult()) {
            int start = lastIndex;
            while (start >= 0) {
                ModelMessage current = modelTranscript.get(start);
                if (current == null || !current.isToolResult()) {
                    break;
                }
                start--;
            }
            if (start >= 0) {
                ModelMessage candidate = modelTranscript.get(start);
                if (candidate != null && candidate.hasToolCalls()) {
                    return start;
                }
            }
            return Math.max(0, start + 1);
        }
        return lastIndex;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
