package com.codey.tools;

import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;

/**
 * 记录一次工具调用及其执行结果。
 */
public class ToolExecutionRecord {
    private final ToolInvocation request;
    private final ToolResult result;

    public ToolExecutionRecord(ToolInvocation request, ToolResult result) {
        this.request = request;
        this.result = result;
    }

    public ToolInvocation getRequest() {
        return request;
    }

    public ToolResult getResult() {
        return result;
    }
}
