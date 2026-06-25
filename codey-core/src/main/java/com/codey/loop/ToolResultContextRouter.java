package com.codey.loop;

import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;

import java.nio.file.Path;

/**
 * 上下文压缩策略已下线，工具结果保持原样回放给后续链路。
 */
public class ToolResultContextRouter {
    public ToolResultContextRouter(Path spilloverRoot) {
    }

    public ToolResult route(String sessionId, ToolInvocation request, ToolResult result) {
        return result;
    }
}
