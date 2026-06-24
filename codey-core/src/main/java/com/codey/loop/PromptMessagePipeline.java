package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.config.AgentSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统一收口动态消息构建逻辑，按管道阶段依次追加历史摘要、本轮用户输入和当前轮 transcript。
 * 这样模型看到的始终是“历史 -> 当前用户诉求 -> 当前轮执行轨迹”的自然时间线。
 */
final class PromptMessagePipeline {
    private final List<PromptMessageStage> stages = Arrays.<PromptMessageStage>asList(
            new ChatHistoryMessageStage(20, 500),
            new CurrentUserInputMessageStage(),
            new TranscriptMessageStage()
    );

    List<ModelMessage> build(AgentSession session) {
        List<ModelMessage> messages = new ArrayList<ModelMessage>();
        PromptMessageBuildContext context = new PromptMessageBuildContext(session);
        for (PromptMessageStage stage : stages) {
            stage.apply(messages, context);
        }
        return messages;
    }
}
