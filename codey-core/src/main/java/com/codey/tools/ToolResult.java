package com.codey.tools;

/**
 * 内核侧工具结果类型。
 */
public class ToolResult extends com.codey.tool.ToolResult {

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
     * 保持链式调用时返回 core 侧结果类型。
     */
    @Override
    public ToolResult withContextContent(String compactedContent, String spilloverPath, int originalContentLength) {
        super.withContextContent(compactedContent, spilloverPath, originalContentLength);
        return this;
    }
}
