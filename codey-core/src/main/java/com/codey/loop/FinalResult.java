package com.codey.loop;

import java.util.Map;

/**
 * 模型在不调用工具时返回的最终结果对象。
 */
public class FinalResult {
    private String status;
    private String summary;
    private Object view;
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

    public Object getView() {
        return view;
    }

    public void setView(Object view) {
        this.view = view;
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

    /**
     * 是否处于“等待用户决策”状态：user_choice 视图或显式 requiresHumanConfirmation。
     * 该状态表示任务尚未真正完成，只是暂停等待用户输入，不应套用业务完成校验。
     */
    public boolean isAwaitingHumanDecision() {
        if (Boolean.TRUE.equals(requiresHumanConfirmation)) {
            return true;
        }
        return isUserChoiceView();
    }

    private boolean isUserChoiceView() {
        if (!(view instanceof Map<?, ?>)) {
            return false;
        }
        Object rawType = ((Map<?, ?>) view).get("_view_type");
        return rawType != null && "user_choice".equalsIgnoreCase(String.valueOf(rawType).trim());
    }

    public String toDisplayText() {
        String textViewContent = extractTextViewContent();
        if (textViewContent != null && !textViewContent.trim().isEmpty()) {
            return textViewContent.trim();
        }
        if (view instanceof Map<?, ?>) {
            Object rawType = ((Map<?, ?>) view).get("_view_type");
            String viewType = rawType == null ? "" : String.valueOf(rawType).trim();
            if (!viewType.isEmpty()) {
                return "已生成结构化结果（" + viewType + "）";
            }
        }
        if (summary != null && !summary.trim().isEmpty()) {
            return summary.trim();
        }
        return "";
    }

    private String extractTextViewContent() {
        if (!(view instanceof Map<?, ?>)) {
            return null;
        }
        Map<?, ?> viewMap = (Map<?, ?>) view;
        Object rawType = viewMap.get("_view_type");
        if (rawType == null || !"text".equalsIgnoreCase(String.valueOf(rawType).trim())) {
            return null;
        }
        Object rawContent = viewMap.get("content");
        return rawContent == null ? null : String.valueOf(rawContent);
    }
}
