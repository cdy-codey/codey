package com.codey.loop;

import com.codey.infra.ModelMessage;

import java.util.List;

/**
 * 仅用于调试/测试的人类可读视图，不代表真正发给模型的协议字段。
 */
public class PromptDebugViewRenderer {

    public String render(PromptPackage promptPackage) {
        if (promptPackage == null) {
            return "";
        }
        return render(promptPackage.getMessages());
    }

    public String render(List<ModelMessage> messages) {
        StringBuilder builder = new StringBuilder();
        if (messages != null) {
            for (ModelMessage message : messages) {
                if (message == null || (isBlank(message.getContent())
                        && isBlank(message.getReasoningContent())
                        && !message.hasToolCalls()
                        && !message.isToolResult())) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("[").append(message.getRole()).append("]\n");
                if (message.hasToolCalls()) {
                    if (!isBlank(message.getReasoningContent())) {
                        builder.append("reasoning=").append(message.getReasoningContent().trim()).append("\n");
                    }
                    builder.append("tool_calls=").append(String.valueOf(message.getToolCalls()));
                } else if (message.isToolResult()) {
                    builder.append("tool_call_id=").append(valueOrFallback(message.getToolCallId(), "(missing)"));
                    if (!isBlank(message.getToolName())) {
                        builder.append(", tool_name=").append(message.getToolName());
                    }
                    if (!isBlank(message.getContent())) {
                        builder.append("\n").append(message.getContent().trim());
                    }
                } else {
                    builder.append(message.getContent().trim());
                    if (!isBlank(message.getReasoningContent())) {
                        builder.append("\n[reasoning]\n").append(message.getReasoningContent().trim());
                    }
                }
            }
        }
        return builder.toString().trim();
    }

    private String valueOrFallback(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
