package com.codey.tool;

import java.util.Map;

/**
 * 一次工具调用请求。
 */
public class ToolInvocation {
    private String toolName;
    private Map<String, Object> arguments;

    public ToolInvocation() {
    }

    public ToolInvocation(String toolName, Map<String, Object> arguments) {
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
