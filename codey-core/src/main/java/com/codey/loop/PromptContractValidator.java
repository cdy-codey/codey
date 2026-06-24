package com.codey.loop;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;

/**
 * 在请求发给模型前校验 prompt 的结构契约和预算。
 */
public class PromptContractValidator {
    private final PromptContractDefinition contractDefinition;
    private final PromptDebugViewRenderer debugViewRenderer;
    private final PromptBudgetEstimator promptBudgetEstimator;
    private final ToolExposurePlanner toolExposurePlanner;

    public PromptContractValidator() {
        this(null);
    }

    public PromptContractValidator(com.codey.tools.ToolRegistry toolRegistry) {
        this.contractDefinition = new PromptContractDefinition();
        this.debugViewRenderer = new PromptDebugViewRenderer();
        this.promptBudgetEstimator = new PromptBudgetEstimator();
        this.toolExposurePlanner = new ToolExposurePlanner(toolRegistry);
    }

    public PromptValidationResult validate(PromptPackage promptPackage, AgentSession session, SkillDefinition skill) {
        if (promptPackage == null) {
            return PromptValidationResult.failed("Prompt contract failed: prompt package is empty");
        }
        String prompt = debugViewRenderer.render(promptPackage);
        PromptBudgetReport budgetReport = promptBudgetEstimator.estimate(promptPackage, contractDefinition);
        budgetReport.setDebugViewChars(length(prompt));
        String budgetDiagnostic = budgetReport.toDiagnosticString();
        if (prompt == null || prompt.trim().isEmpty()) {
            return PromptValidationResult.failed("Prompt contract failed: prompt is empty", budgetDiagnostic);
        }

        if (promptPackage.getMessages() == null || promptPackage.getMessages().isEmpty()) {
            return PromptValidationResult.failed("Prompt contract failed: messages are empty", budgetDiagnostic);
        }

        if (!hasLeadingSystemMessage(promptPackage)) {
            return PromptValidationResult.failed("Prompt contract failed: leading system message is missing", budgetDiagnostic);
        }

        if (hasMisplacedSystemMessage(promptPackage)) {
            return PromptValidationResult.failed("Prompt contract failed: system message must stay in the leading system block", budgetDiagnostic);
        }

        if (hasIllegalSyntheticUserMessage(promptPackage)) {
            return PromptValidationResult.failed("Prompt contract failed: user message must only contain real user input", budgetDiagnostic);
        }

        if (hasIllegalAssistantMessage(promptPackage)) {
            return PromptValidationResult.failed("Prompt contract failed: assistant message must only contain historical assistant output", budgetDiagnostic);
        }

        if (hasIllegalToolMessage(promptPackage)) {
            return PromptValidationResult.failed("Prompt contract failed: tool message must only contain actual tool result content", budgetDiagnostic);
        }

        for (String section : contractDefinition.getRequiredSections()) {
            if (!prompt.contains(section)) {
                return PromptValidationResult.failed("Prompt contract failed: missing section " + section, budgetDiagnostic);
            }
        }

        String lengthNotice = buildLengthNotice(budgetReport);

        if (isBlank(session.getUserGoal()) || !prompt.contains(session.getUserGoal())) {
            return PromptValidationResult.failed("Prompt contract failed: user goal missing", budgetDiagnostic);
        }

        if (!isBlank(session.getWorkingDirectory()) && !prompt.contains(session.getWorkingDirectory())) {
            return PromptValidationResult.failed("Prompt contract failed: working directory missing", budgetDiagnostic);
        }

        if (skill != null && toolExposurePlanner.selectVisibleTools(session, skill).isEmpty()) {
            return PromptValidationResult.failed(
                    "Prompt contract failed: visible tools are empty; configure allowedToolGroups or allowedToolBundles",
                    budgetDiagnostic
            );
        }

        // 预算超限先只做诊断告警，不在 preflight 阶段硬拦截主流程。
        if (budgetReport.isOverInputBudget()) {
            return PromptValidationResult.passed(buildPreflightOverflowMessage(budgetReport), budgetDiagnostic);
        }

        return PromptValidationResult.passed("Prompt contract passed" + lengthNotice, budgetDiagnostic);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean hasLeadingSystemMessage(PromptPackage promptPackage) {
        if (promptPackage == null || promptPackage.getMessages() == null) {
            return false;
        }
        for (com.codey.infra.ModelMessage message : promptPackage.getMessages()) {
            if (message == null) {
                continue;
            }
            return message.isSystem() && !isBlank(message.getContent());
        }
        return false;
    }

    /**
     * 允许多个连续的前置 system 消息组成“基础规约 + 运行时上下文”块，但不允许 system 出现在首个非 system 消息之后。
     */
    private boolean hasMisplacedSystemMessage(PromptPackage promptPackage) {
        if (promptPackage == null || promptPackage.getMessages() == null) {
            return false;
        }
        boolean seenLeadingSystem = false;
        boolean leftLeadingSystemBlock = false;
        for (com.codey.infra.ModelMessage message : promptPackage.getMessages()) {
            if (message == null) {
                continue;
            }
            if (!leftLeadingSystemBlock) {
                if (message.isSystem()) {
                    seenLeadingSystem = true;
                    continue;
                }
                leftLeadingSystemBlock = true;
                continue;
            }
            if (message.isSystem()) {
                return true;
            }
        }
        return !seenLeadingSystem;
    }

    private boolean hasIllegalSyntheticUserMessage(PromptPackage promptPackage) {
        if (promptPackage == null || promptPackage.getMessages() == null) {
            return false;
        }
        for (com.codey.infra.ModelMessage message : promptPackage.getMessages()) {
            if (message == null || !message.isUser() || isBlank(message.getContent())) {
                continue;
            }
            String content = message.getContent();
            if (content.contains("<conversation_memory>")
                    || content.contains("<context_snapshot>")
                    || content.contains("<replan>")
                    || content.contains("<current_turn>")
                    || content.contains("<recent_tool_results>")
                    || content.contains("<recent_edit_results>")
                    || content.contains("<recent_system_feedback>")
                    || content.contains("<recent_interactions>")
                    || containsLineStartingWith(content, "当前技能:")
                    || containsLineStartingWith(content, "当前暴露工具:")
                    || containsLineStartingWith(content, "当前轮重点:")
                    || containsLineStartingWith(content, "标准输出:")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIllegalToolMessage(PromptPackage promptPackage) {
        if (promptPackage == null || promptPackage.getMessages() == null) {
            return false;
        }
        for (com.codey.infra.ModelMessage message : promptPackage.getMessages()) {
            if (message == null || !message.isToolResult()) {
                continue;
            }
            if (isBlank(message.getToolCallId())
                    || isBlank(message.getToolName())
                    || isBlank(message.getContent())
                    || containsLegacySyntheticToolText(message.getContent())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIllegalAssistantMessage(PromptPackage promptPackage) {
        if (promptPackage == null || promptPackage.getMessages() == null) {
            return false;
        }
        for (com.codey.infra.ModelMessage message : promptPackage.getMessages()) {
            if (message == null || !message.isAssistant()) {
                continue;
            }
            if (containsSystemOrUserPromptScaffolding(message.getContent())
                    || containsSystemOrUserPromptScaffolding(message.getReasoningContent())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLegacySyntheticToolText(String content) {
        return containsLineStartingWith(content, "Tool call not executed:")
                || containsLineStartingWith(content, "Tool request not allowed by skill constraints:");
    }

    private boolean containsSystemOrUserPromptScaffolding(String content) {
        if (isBlank(content)) {
            return false;
        }
        return content.contains("<conversation_memory>")
                || content.contains("<context_snapshot>")
                || content.contains("<replan>")
                || content.contains("<current_turn>")
                || content.contains("<recent_tool_results>")
                || content.contains("<recent_edit_results>")
                || content.contains("<recent_system_feedback>")
                || content.contains("<recent_interactions>")
                || containsLineStartingWith(content, "用户目标:")
                || containsLineStartingWith(content, "工作目录:")
                || containsLineStartingWith(content, "补充文件:")
                || containsLineStartingWith(content, "补充说明:")
                || containsLineStartingWith(content, "当前技能:")
                || containsLineStartingWith(content, "当前暴露工具:")
                || containsLineStartingWith(content, "当前轮重点:")
                || containsLineStartingWith(content, "标准输出:")
                || containsLineStartingWith(content, "## Language")
                || containsLineStartingWith(content, "## Preamble Rhythm")
                || containsLineStartingWith(content, "## Toolbox")
                || containsLineStartingWith(content, "## Environment")
                || containsLineStartingWith(content, "## Runtime Guardrails");
    }

    private boolean containsLineStartingWith(String content, String prefix) {
        if (isBlank(content) || isBlank(prefix)) {
            return false;
        }
        for (String line : content.replace("\r", "").split("\n")) {
            if (line.trim().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String buildLengthNotice(PromptBudgetReport budgetReport) {
        if (budgetReport == null || !budgetReport.isOverInputBudget()) {
            return "";
        }
        int actualLength = budgetReport.getEstimatedTotalChars();
        int inputBudget = budgetReport.getInputBudgetChars();
        int overflow = actualLength - inputBudget;
        return " (length warning: estimatedTotal=" + actualLength
                + ", debugViewChars=" + budgetReport.getDebugViewChars()
                + ", contextWindow=" + budgetReport.getContextWindowChars()
                + ", reservedOutput=" + budgetReport.getReservedOutputChars()
                + ", headroom=" + budgetReport.getHeadroomChars()
                + ", inputBudget=" + inputBudget
                + ", overflow=" + overflow + ")";
    }

    private String buildPreflightOverflowMessage(PromptBudgetReport budgetReport) {
        int overflow = budgetReport.getEstimatedTotalChars() - budgetReport.getInputBudgetChars();
        return "Preflight context budget exceeded: estimatedTotal=" + budgetReport.getEstimatedTotalChars()
                + ", contextWindow=" + budgetReport.getContextWindowChars()
                + ", reservedOutput=" + budgetReport.getReservedOutputChars()
                + ", headroom=" + budgetReport.getHeadroomChars()
                + ", inputBudget=" + budgetReport.getInputBudgetChars()
                + ", overflow=" + overflow;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
