package com.codey.loop;

/**
 * 模型在不调用工具时返回的最终结果对象。
 */
public class FinalResult {
    private String status;
    private String summary;
    private Boolean requiresHumanConfirmation;
    private String uncertaintyReason;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Boolean getRequiresHumanConfirmation() {
        return requiresHumanConfirmation;
    }

    public void setRequiresHumanConfirmation(Boolean requiresHumanConfirmation) {
        this.requiresHumanConfirmation = requiresHumanConfirmation;
    }

    public String getUncertaintyReason() {
        return uncertaintyReason;
    }

    public void setUncertaintyReason(String uncertaintyReason) {
        this.uncertaintyReason = uncertaintyReason;
    }
}
