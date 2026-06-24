package com.codey.tools;

/**
 * 内核侧工具能力声明。
 */
public class ToolCapability extends com.codey.tool.ToolCapability {

    private ToolCapability(boolean readOnly, boolean supportsParallelExecution) {
        super(readOnly, supportsParallelExecution);
    }

    public static ToolCapability of(boolean readOnly, boolean supportsParallelExecution) {
        return new ToolCapability(readOnly, supportsParallelExecution);
    }

    public static ToolCapability standard() {
        return new ToolCapability(false, false);
    }

    public static ToolCapability readOnlyParallel() {
        return new ToolCapability(true, true);
    }
}
