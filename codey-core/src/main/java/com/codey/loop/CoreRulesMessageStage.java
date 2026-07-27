package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.config.AgentSession;

import java.util.List;

/**
 * 每轮对话前将铁律（core rules）以 user 消息注入，确保即使上下文变长，
 * AI 也不会遗忘必须遵守的最高优先级规则。
 * 铁律以 {@code <core_rules>} 标记包裹，与用户真实输入明确区分。
 */
final class CoreRulesMessageStage implements PromptMessageStage {

    @Override
    public void apply(List<ModelMessage> messages, PromptMessageBuildContext context) {
        AgentSession session = context == null ? null : context.getSession();
        if (session == null) {
            return;
        }
        String coreRules = session.getCoreRules();
        if (PromptMessageTextSupport.isBlank(coreRules)) {
            return;
        }
        String formatted = formatCoreRules(coreRules);
        messages.add(ModelMessage.user(formatted));
    }

    /**
     * 将原始铁律文本包装为带标记的 user 消息，便于 AI 识别为最高优先级。
     */
    private String formatCoreRules(String rawCoreRules) {
        StringBuilder builder = new StringBuilder();
        builder.append("<core_rules>\n");
        builder.append("以下是你必须始终遵守的最高优先级规则（铁律），任何情况下都不得违反：\n\n");
        builder.append(rawCoreRules.trim());
        builder.append("\n</core_rules>");
        return builder.toString();
    }
}
