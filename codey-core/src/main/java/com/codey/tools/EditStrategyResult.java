package com.codey.tools;

import com.codey.tools.*;

/**
 * 封装一次编辑策略执行后的内容、摘要和差异说明。
 */
public class EditStrategyResult {
    private final String updatedContent;
    private final String summary;
    private final boolean changed;
    private final String diffSummary;

    public EditStrategyResult(String updatedContent, String summary, boolean changed, String diffSummary) {
        this.updatedContent = updatedContent;
        this.summary = summary;
        this.changed = changed;
        this.diffSummary = diffSummary;
    }

    public String getUpdatedContent() {
        return updatedContent;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isChanged() {
        return changed;
    }

    public String getDiffSummary() {
        return diffSummary;
    }
}

