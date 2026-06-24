package com.codey.loop;

/**
 * 结构化消息视角下的一次输入预算快照。
 */
public class PromptBudgetReport {
    private int systemMessageCount;
    private int systemMessageChars;
    private int messageCount;
    private int userMessageCount;
    private int assistantMessageCount;
    private int toolMessageCount;
    private int assistantToolCallCount;
    private int messageContentChars;
    private int toolCallChars;
    private int debugViewChars;
    private int contextWindowChars;
    private int reservedOutputChars;
    private int headroomChars;

    public int getSystemMessageCount() {
        return systemMessageCount;
    }

    public void setSystemMessageCount(int systemMessageCount) {
        this.systemMessageCount = systemMessageCount;
    }

    public int getSystemMessageChars() {
        return systemMessageChars;
    }

    public void setSystemMessageChars(int systemMessageChars) {
        this.systemMessageChars = systemMessageChars;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public int getUserMessageCount() {
        return userMessageCount;
    }

    public void setUserMessageCount(int userMessageCount) {
        this.userMessageCount = userMessageCount;
    }

    public int getAssistantMessageCount() {
        return assistantMessageCount;
    }

    public void setAssistantMessageCount(int assistantMessageCount) {
        this.assistantMessageCount = assistantMessageCount;
    }

    public int getToolMessageCount() {
        return toolMessageCount;
    }

    public void setToolMessageCount(int toolMessageCount) {
        this.toolMessageCount = toolMessageCount;
    }

    public int getAssistantToolCallCount() {
        return assistantToolCallCount;
    }

    public void setAssistantToolCallCount(int assistantToolCallCount) {
        this.assistantToolCallCount = assistantToolCallCount;
    }

    public int getMessageContentChars() {
        return messageContentChars;
    }

    public void setMessageContentChars(int messageContentChars) {
        this.messageContentChars = messageContentChars;
    }

    public int getToolCallChars() {
        return toolCallChars;
    }

    public void setToolCallChars(int toolCallChars) {
        this.toolCallChars = toolCallChars;
    }

    public int getDebugViewChars() {
        return debugViewChars;
    }

    public void setDebugViewChars(int debugViewChars) {
        this.debugViewChars = debugViewChars;
    }

    public int getContextWindowChars() {
        return contextWindowChars;
    }

    public void setContextWindowChars(int contextWindowChars) {
        this.contextWindowChars = contextWindowChars;
    }

    public int getReservedOutputChars() {
        return reservedOutputChars;
    }

    public void setReservedOutputChars(int reservedOutputChars) {
        this.reservedOutputChars = reservedOutputChars;
    }

    public int getHeadroomChars() {
        return headroomChars;
    }

    public void setHeadroomChars(int headroomChars) {
        this.headroomChars = headroomChars;
    }

    public int getEstimatedTotalChars() {
        return systemMessageChars + messageContentChars + toolCallChars;
    }

    public int getInputBudgetChars() {
        return contextWindowChars - reservedOutputChars - headroomChars;
    }

    public int getRemainingInputBudgetChars() {
        return getInputBudgetChars() - getEstimatedTotalChars();
    }

    public int getContextWindowHeadroomChars() {
        return contextWindowChars - getEstimatedTotalChars();
    }

    public boolean isOverInputBudget() {
        return getEstimatedTotalChars() > getInputBudgetChars();
    }

    public String toDiagnosticString() {
        StringBuilder builder = new StringBuilder();
        builder.append("context_budget");
        builder.append(" messages=").append(messageCount);
        builder.append(" system=").append(systemMessageCount);
        builder.append(" user=").append(userMessageCount);
        builder.append(" assistant=").append(assistantMessageCount);
        builder.append(" tool=").append(toolMessageCount);
        builder.append(" assistantToolCalls=").append(assistantToolCallCount);
        builder.append(" systemChars=").append(systemMessageChars);
        builder.append(" messageChars=").append(messageContentChars);
        builder.append(" toolCallChars=").append(toolCallChars);
        builder.append(" estimatedTotal=").append(getEstimatedTotalChars());
        builder.append(" debugViewChars=").append(debugViewChars);
        builder.append(" contextWindow=").append(contextWindowChars);
        builder.append(" reservedOutput=").append(reservedOutputChars);
        builder.append(" headroom=").append(headroomChars);
        builder.append(" inputBudget=").append(getInputBudgetChars());
        builder.append(" remainingInputBudget=").append(getRemainingInputBudgetChars());
        builder.append(" contextWindowHeadroom=").append(getContextWindowHeadroomChars());
        return builder.toString();
    }
}
