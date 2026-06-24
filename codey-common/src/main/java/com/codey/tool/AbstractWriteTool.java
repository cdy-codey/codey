package com.codey.tool;

/**
 * 写工具抽象基类。
 */
public abstract class AbstractWriteTool extends AbstractTool {
    @Override
    public ToolCapability capability() {
        return ToolCapability.standard();
    }
}
