package com.codey.loop;

/**
 * 人工确认编辑提案后的决策结果。
 */
public class HumanDecision {
    private final boolean approved;
    private final String decisionLabel;
    private final String feedback;

    private HumanDecision(boolean approved, String decisionLabel, String feedback) {
        this.approved = approved;
        this.decisionLabel = decisionLabel;
        this.feedback = feedback;
    }

    public static HumanDecision approve(String feedback) {
        return new HumanDecision(true, "approve", feedback);
    }

    public static HumanDecision reject(String feedback) {
        return new HumanDecision(false, "reject", feedback);
    }

    public boolean isApproved() {
        return approved;
    }

    public String getDecisionLabel() {
        return decisionLabel;
    }

    public String getFeedback() {
        return feedback;
    }
}
