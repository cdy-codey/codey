package com.codey.tool;

import java.util.Collections;
import java.util.Map;

/**
 * 工具执行结果。
 */
public class ToolResult {
    protected boolean success;
    protected String content;
    protected String errorMessage;
    protected String summary;
    protected String contentForModel;
    protected String spilloverPath;
    protected int originalContentLength;
    protected boolean compactedForContext;
    protected Map<String, Object> view;

    public static ToolResult ok(String content) {
        return ok(content, null);
    }

    public static ToolResult ok(String content, String summary) {
        ToolResult result = new ToolResult();
        result.success = true;
        result.content = content;
        result.summary = summary;
        return result;
    }

    public static ToolResult fail(String errorMessage) {
        return fail(errorMessage, null);
    }

    public static ToolResult fail(String errorMessage, String summary) {
        ToolResult result = new ToolResult();
        result.success = false;
        result.errorMessage = errorMessage;
        result.summary = summary;
        return result;
    }

    /**
     * 工具请求用户从选项中选择后才能继续（例如校验未通过时把问题列成选项）。
     * 该结果不视为普通失败，循环层检测到后会暂停并等待用户选择。
     */
    public static ToolResult userChoice(Map<String, Object> view, String contentForModel) {
        ToolResult result = new ToolResult();
        result.success = false;
        result.view = view;
        result.content = contentForModel;
        return result;
    }

    public Map<String, Object> getView() {
        return view;
    }

    public boolean isUserChoice() {
        if (view == null) {
            return false;
        }
        Object type = view.get("_view_type");
        return type != null && "user_choice".equalsIgnoreCase(String.valueOf(type).trim());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getSummary() {
        return summary;
    }

    public String getContentForModel() {
        return contentForModel == null ? content : contentForModel;
    }

    public String getSpilloverPath() {
        return spilloverPath;
    }

    public int getOriginalContentLength() {
        return originalContentLength;
    }

    public boolean isCompactedForContext() {
        return compactedForContext;
    }

    /**
     * 保留原始内容供日志使用，同时回灌模型压缩结果。
     */
    public ToolResult withContextContent(String compactedContent, String spilloverPath, int originalContentLength) {
        this.contentForModel = compactedContent;
        this.spilloverPath = spilloverPath;
        this.originalContentLength = originalContentLength;
        this.compactedForContext = true;
        return this;
    }

    public Map<String, Object> toMetadata() {
        return Collections.<String, Object>emptyMap();
    }
}
