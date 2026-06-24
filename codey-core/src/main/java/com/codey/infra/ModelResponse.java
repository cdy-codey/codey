package com.codey.infra;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准 chat completions 响应的本地封装。
 */
public class ModelResponse {
    private String content;
    private String reasoningContent;
    private String finishReason;
    private String rawResponse;
    private List<ModelToolCall> toolCalls = new ArrayList<ModelToolCall>();

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public List<ModelToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ModelToolCall> toolCalls) {
        this.toolCalls = toolCalls == null ? new ArrayList<ModelToolCall>() : new ArrayList<ModelToolCall>(toolCalls);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
