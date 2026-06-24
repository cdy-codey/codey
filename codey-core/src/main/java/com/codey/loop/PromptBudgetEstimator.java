package com.codey.loop;

import com.codey.infra.ModelMessage;
import com.codey.infra.ModelToolCall;

/**
 * 基于结构化 messages 估算当前轮输入预算。
 */
public class PromptBudgetEstimator {

    public PromptBudgetReport estimate(PromptPackage promptPackage, PromptContractDefinition contractDefinition) {
        PromptBudgetReport report = new PromptBudgetReport();
        if (contractDefinition != null) {
            report.setContextWindowChars(contractDefinition.getContextWindowChars());
            report.setReservedOutputChars(contractDefinition.getReservedOutputChars());
            report.setHeadroomChars(contractDefinition.getHeadroomChars());
        }
        if (promptPackage == null) {
            return report;
        }
        if (promptPackage.getMessages() == null) {
            return report;
        }
        report.setMessageCount(promptPackage.getMessages().size());
        for (ModelMessage message : promptPackage.getMessages()) {
            if (message == null) {
                continue;
            }
            accumulateRoleCounters(report, message);
            int messageChars = length(message.getRole())
                    + length(message.getContent())
                    + length(message.getReasoningContent())
                    + length(message.getToolCallId())
                    + length(message.getToolName());
            if (message.isSystem()) {
                report.setSystemMessageChars(report.getSystemMessageChars() + messageChars);
            } else {
                report.setMessageContentChars(report.getMessageContentChars() + messageChars);
            }
            if (message.hasToolCalls()) {
                report.setAssistantToolCallCount(report.getAssistantToolCallCount() + message.getToolCalls().size());
                report.setToolCallChars(report.getToolCallChars() + estimateToolCallsLength(message));
            }
        }
        return report;
    }

    private void accumulateRoleCounters(PromptBudgetReport report, ModelMessage message) {
        if (message.isSystem()) {
            report.setSystemMessageCount(report.getSystemMessageCount() + 1);
            return;
        }
        if (message.isUser()) {
            report.setUserMessageCount(report.getUserMessageCount() + 1);
            return;
        }
        if (message.isAssistant()) {
            report.setAssistantMessageCount(report.getAssistantMessageCount() + 1);
            return;
        }
        if (message.isToolResult()) {
            report.setToolMessageCount(report.getToolMessageCount() + 1);
        }
    }

    private int estimateToolCallsLength(ModelMessage message) {
        int total = 0;
        for (ModelToolCall toolCall : message.getToolCalls()) {
            if (toolCall == null) {
                continue;
            }
            total += length(toolCall.getId());
            total += length(toolCall.getName());
            total += length(String.valueOf(toolCall.getArguments()));
        }
        return total;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
