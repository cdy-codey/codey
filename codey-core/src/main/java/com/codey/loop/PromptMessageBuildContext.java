package com.codey.loop;

import com.codey.config.AgentSession;

/**
 * 消息构建阶段共享的只读上下文。
 * 后续如果阶段需要更多稳定输入，可继续集中放在这里，避免阶段之间直接耦合。
 */
final class PromptMessageBuildContext {
    private final AgentSession session;

    PromptMessageBuildContext(AgentSession session) {
        this.session = session;
    }

    AgentSession getSession() {
        return session;
    }
}
