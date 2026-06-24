package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.config.AgentSession;

import java.util.List;

/**
 * 当前轮的工具轨迹和 reasoning 作为细粒度上下文单独回放。
 * 它们在会话结束后会被清空，因此不会再跨轮打乱历史顺序。
 */
final class TranscriptMessageStage implements PromptMessageStage {
    @Override
    public void apply(List<ModelMessage> messages, PromptMessageBuildContext context) {
        AgentSession session = context == null ? null : context.getSession();
        if (session == null || session.getModelTranscript().isEmpty()) {
            return;
        }
        for (ModelMessage transcriptMessage : session.getModelTranscript()) {
            ModelMessage replayMessage = PromptMessageTextSupport.sanitizeTranscriptForReplay(transcriptMessage);
            if (replayMessage != null) {
                messages.add(replayMessage);
            }
        }
    }
}
