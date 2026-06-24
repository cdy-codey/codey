package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.config.AgentSession;

import java.util.List;

/**
 * 当前用户输入永远作为最后阶段追加，确保模型看到的是“历史 -> 当前输入”的时间顺序。
 * 这里只保留用户真实输入与用户主动补充的上下文，不把运行时脚手架塞进 user 角色。
 */
final class CurrentUserInputMessageStage implements PromptMessageStage {
    @Override
    public void apply(List<ModelMessage> messages, PromptMessageBuildContext context) {
        AgentSession session = context == null ? null : context.getSession();
        if (session == null) {
            return;
        }
        if (!PromptMessageTextSupport.isBlank(session.getUserGoal())) {
            messages.add(ModelMessage.user(session.getUserGoal().trim()));
        }
        StringBuilder builder = new StringBuilder();
        PromptMessageTextSupport.appendUserProvidedFiles(builder, session.getUserContextFiles());
        PromptMessageTextSupport.appendUserProvidedNotes(builder, session.getUserContextNotes());
        if (builder.length() > 0) {
            messages.add(ModelMessage.user(builder.toString().trim()));
        }
    }
}
