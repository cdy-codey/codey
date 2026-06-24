package com.codey.tool;

/**
 * 对外统一工具接口。
 */
public interface ToolSpec {
    ToolDescriptor descriptor();

    /**
     * 元数据仅用于运行时筛选和编排，不直接暴露给模型。
     */
    default ToolMetadata metadata() {
        return ToolMetadata.standard();
    }

    default ToolCapability capability() {
        return ToolCapability.standard();
    }

    ToolResult execute(ToolInvocation invocation, ToolContext context);
}
