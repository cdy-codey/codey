package com.codey.tool;

/**
 * 工具抽象基类。
 */
public abstract class AbstractTool implements ToolSpec {
    protected String requireString(ToolInvocation invocation, String key) {
        Object value = invocation == null || invocation.getArguments() == null ? null : invocation.getArguments().get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return String.valueOf(value).trim();
    }
}
