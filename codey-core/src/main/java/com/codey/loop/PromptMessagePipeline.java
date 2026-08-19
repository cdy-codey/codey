package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.config.AgentSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统一收口动态消息构建逻辑，按管道阶段依次追加完整会话 transcript 和铁律。
 * 模型看到的始终是“完整时间线（历史用户输入 / 助手轨迹与思考 / 当前输入）-> 铁律提醒”的自然顺序：
 * transcript 跨轮保留，铁律作为最后一条消息注入，紧贴当前输入，避免上下文变长后被遗忘。
 */
final class PromptMessagePipeline {
    private final List<PromptMessageStage> stages = Arrays.<PromptMessageStage>asList(
            new TranscriptMessageStage(),
            new CoreRulesMessageStage()
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
