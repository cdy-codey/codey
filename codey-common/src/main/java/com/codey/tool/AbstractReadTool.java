package com.codey.tool;

/**
 * 只读工具抽象基类。
 */
public abstract class AbstractReadTool extends AbstractTool {
    @Override
    public ToolCapability capability() {
        return ToolCapability.readOnlyParallel();
    }
}
