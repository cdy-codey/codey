package com.codey.task;

import com.codey.config.AgentSession;

/**
 * 负责把本轮真实问答写回会话历史，避免 TaskRunner 继续持有历史回放细节。
 */
public class ChatHistoryRecorder {

    public void recordTurn(AgentSession session, GenerateTask task, TaskResult result) {
        if (session == null) {
            return;
        }
        session.recordChatTurn(
                task == null ? null : task.getGoal(),
                resolveAssistantReplyForHistory(session, result)
        );
        // 当前轮已经写成稳定摘要，下一轮不再回放这一轮的工具 transcript。
        session.resetForNextTurn();
    }

    private String resolveAssistantReplyForHistory(AgentSession session, TaskResult result) {
        if (session == null) {
            return "";
        }
        String assistantReply = session.getLatestAssistantResponseContent();
        if (assistantReply == null || assistantReply.trim().isEmpty()) {
            return "";
        }
        return assistantReply;
    }
}
