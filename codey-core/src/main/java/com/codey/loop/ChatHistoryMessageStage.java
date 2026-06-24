package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.config.AgentSession;

import java.util.List;

/**
 * 已完成轮次只回放摘要问答，避免和当前轮 transcript 混成两套时间线。
 */
final class ChatHistoryMessageStage implements PromptMessageStage {
    private final int maxHistory;
    private final int maxMessageLength;

    ChatHistoryMessageStage(int maxHistory, int maxMessageLength) {
        this.maxHistory = maxHistory;
        this.maxMessageLength = maxMessageLength;
    }

    @Override
    public void apply(List<ModelMessage> messages, PromptMessageBuildContext context) {
        AgentSession session = context == null ? null : context.getSession();
        if (session == null) {
            return;
        }
        List<String> history = PromptMessageTextSupport.selectRecentUnique(session.getChatHistory(), maxHistory);
        for (String line : history) {
            String role = PromptMessageTextSupport.detectHistoryRole(line);
            String content = PromptMessageTextSupport.stripHistoryRole(line);
            if (PromptMessageTextSupport.isBlank(content)) {
                continue;
            }
            String trimmed = PromptMessageTextSupport.trimToLength(content, maxMessageLength);
            if ("assistant".equals(role)) {
                messages.add(ModelMessage.assistant(trimmed));
            } else {
                messages.add(ModelMessage.user(trimmed));
            }
        }
    }
}
