package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组装每轮发送给模型的 prompt。
 * 模型侧保留工具原始结果，避免控制台摘要污染推理上下文。
 */
public class PromptAssembler {
    private final PromptLayerComposer promptLayerComposer = new PromptLayerComposer();
    private final PromptDebugViewRenderer debugViewRenderer = new PromptDebugViewRenderer();
    private final PromptMessagePipeline promptMessagePipeline = new PromptMessagePipeline();

    public String build(AgentSession session, SkillDefinition skill) {
        return build(session, skill, new ArrayList<String>());
    }

    public String build(AgentSession session, SkillDefinition skill, List<String> visibleTools) {
        return renderDebugView(buildPackage(session, skill, visibleTools));
    }

    public String buildSystemPrompt(SkillDefinition skill, List<String> visibleTools) {
        return buildSystemPrompt(null, skill, visibleTools);
    }

    public String buildSystemPrompt(AgentSession session, SkillDefinition skill, List<String> visibleTools) {
        StringBuilder builder = new StringBuilder();
        builder.append(promptLayerComposer.compose(skill, session)).append("\n");
        appendRuntimeGuardrails(builder);
        return builder.toString().trim();
    }

    public PromptPackage buildPackage(AgentSession session, SkillDefinition skill, List<String> visibleTools) {
        PromptPackage promptPackage = new PromptPackage();
        List<String> safeVisibleTools = safeTools(visibleTools, skill);
        String systemPrompt = buildSystemPrompt(session, skill, safeVisibleTools);
        List<ModelMessage> messages = new ArrayList<ModelMessage>();
        if (!isBlank(systemPrompt)) {
            messages.add(ModelMessage.system(systemPrompt));
        }
        String runtimeSystemMessage = buildRuntimeSystemMessage(session, skill);
        if (!isBlank(runtimeSystemMessage)) {
            // 频繁变动的运行时上下文单独挂到追加的 system 消息上，不写进基础 system prompt。
            messages.add(ModelMessage.system(runtimeSystemMessage));
        }
        messages.addAll(buildMessages(session, skill, safeVisibleTools));
        promptPackage.setMessages(messages);
        return promptPackage;
    }

    public String renderDebugView(PromptPackage promptPackage) {
        return debugViewRenderer.render(promptPackage);
    }

    public List<ModelMessage> buildMessages(AgentSession session, SkillDefinition skill, List<String> visibleTools) {
        return new ArrayList<ModelMessage>(promptMessagePipeline.build(session));
    }

    public String buildUserPrompt(AgentSession session, SkillDefinition skill, List<String> visibleTools) {
        List<ModelMessage> messages = buildMessages(session, skill, visibleTools);
        StringBuilder builder = new StringBuilder();
        builder.append("当前采用结构化 messages 输入。以下为发给模型的动态消息视图:\n");
        for (ModelMessage message : messages) {
            builder.append("\n[").append(message.getRole()).append("]\n");
            builder.append(message.getContent()).append("\n");
        }
        return builder.toString().trim();
    }

    private List<String> safeTools(List<String> visibleTools, SkillDefinition skill) {
        if (visibleTools == null || visibleTools.isEmpty()) {
            return new ArrayList<String>();
        }
        return visibleTools;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 基础 system prompt 只保留稳定规约，避免被频繁变化的运行时信息污染。
     */
    private void appendRuntimeGuardrails(StringBuilder builder) {
        if (builder == null) {
            return;
        }
        if (!builder.toString().contains("## Runtime Guardrails")) {
            builder.append("\n## Runtime Guardrails\n\n")
                    .append("- system 只承载全局规约、身份设定和稳定指令。\n")
                    .append("- user 只承载真实用户输入，不承载运行时脚手架。\n")
                    .append("- assistant 只承载历史助手输出、思考和 tool_calls。\n")
                    .append("- tool 只承载真实工具返回结果。\n");
        }
    }

    /**
     * 运行时上下文经常变化，应以追加的 system 消息参与会话，而不是固化在基础 system prompt 中。
     */
    private String buildRuntimeSystemMessage(AgentSession session,
                                             SkillDefinition skill) {
        StringBuilder builder = new StringBuilder();
        // tool schema 已通过 request.tools 单独传给模型，这里只保留必要运行时上下文。
        builder.append("当前技能: ").append(skill == null ? "" : skill.getName()).append("\n");
        if (session != null && session.getIdentities() != null && !session.getIdentities().isEmpty()) {
            builder.append("当前会话身份: ").append(String.join(", ", session.getIdentities())).append("\n");
        }
        if (session != null && !isBlank(session.getWorkingDirectory())) {
            builder.append("当前工作目录: ").append(session.getWorkingDirectory().trim()).append("\n");
        }
        if (session != null) {
            appendStableRuntimeContext(builder, session);
        }
        return builder.toString().trim();
    }

    /**
     * 稳定运行上下文统一进入 system prompt，避免被模型误判成用户自然语言输入。
     */
    private void appendStableRuntimeContext(StringBuilder builder, AgentSession session) {
        if (builder == null || session == null) {
            return;
        }
        // system prompt 只暴露前端/用户显式给出的上下文，不回灌工具执行过程中出现的路径。
        List<String> contextFiles = distinctNonBlank(session.getUserContextFiles());
        if (!contextFiles.isEmpty()) {
            builder.append("\n补充文件:\n");
            for (String file : contextFiles) {
                builder.append("- ").append(file).append("\n");
            }
        }
        List<String> contextNotes = distinctNonBlank(session.getUserContextNotes());
        if (!contextNotes.isEmpty()) {
            builder.append("补充说明:\n");
            for (String note : contextNotes) {
                builder.append("- ").append(note).append("\n");
            }
        }
    }

    private List<String> distinctNonBlank(List<String> values) {
        Set<String> deduplicated = new LinkedHashSet<String>();
        if (values == null) {
            return new ArrayList<String>();
        }
        for (String value : values) {
            if (!isBlank(value)) {
                deduplicated.add(value.trim());
            }
        }
        return new ArrayList<String>(deduplicated);
    }

}
