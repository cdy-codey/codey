package com.codey.console.common;

import com.codey.client.AgentClient;
import com.codey.client.ChatSession;
import com.codey.client.RunRequest;
import com.codey.client.RunResult;
import com.codey.console.chat.ChatTaskExecutor;

/**
 * 复用同一个会话标识来执行多轮对话任务。
 */
public class StatefulChatTaskExecutor implements ChatTaskExecutor {
    private final AgentClient agentClient;
    private ChatSession chatSession;

    public StatefulChatTaskExecutor(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    @Override
    public RunResult run(RunRequest request) {
        if (chatSession == null) {
            chatSession = agentClient.openSession(request);
        }
        return agentClient.runTurn(chatSession.getSessionId(), request);
    }

    @Override
    public void reset() {
        if (chatSession != null) {
            agentClient.closeSession(chatSession.getSessionId());
            chatSession = null;
        }
    }
}
