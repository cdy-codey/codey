package com.codey.tool;

import java.util.Map;

/**
 * 工具调用上下文。
 * 对外只暴露通用元信息和必要字段，不在 common 层绑定具体的路径解析行为。
 */
public interface ToolContext {
    String getRequestId();

    String getSessionId();

    /**
     * 当前工具调用看到的工作目录，通常是相对工作区根目录的可见路径。
     */
    default String getWorkingDirectory() {
        return null;
    }

    Map<String, Object> getAttributes();

    default Object getAttribute(String key) {
        Map<String, Object> attributes = getAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.get(key);
    }
}
