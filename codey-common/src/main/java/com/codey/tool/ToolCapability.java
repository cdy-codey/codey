package com.codey.tool;

/**
 * 工具能力声明。
 */
public class ToolCapability {
    private final boolean readOnly;
    private final boolean supportsParallelExecution;

    protected ToolCapability(boolean readOnly, boolean supportsParallelExecution) {
        this.readOnly = readOnly;
        this.supportsParallelExecution = supportsParallelExecution;
    }

    public static ToolCapability standard() {
        return new ToolCapability(false, false);
    }

    public static ToolCapability readOnlyParallel() {
        return new ToolCapability(true, true);
    }

    public static ToolCapability readOnly() {
        return new ToolCapability(true, false);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean supportsParallelExecution() {
        return supportsParallelExecution;
    }
}
