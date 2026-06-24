package com.codey.tool;

import java.util.Map;

/**
 * 工具调用上下文。
 * 扩展接口仅暴露调用元信息，不暴露工作目录和路径控制能力。
 */
public interface ToolContext {
    String getRequestId();

    String getSessionId();

    Map<String, Object> getAttributes();

    default Object getAttribute(String key) {
        Map<String, Object> attributes = getAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.get(key);
    }
}
