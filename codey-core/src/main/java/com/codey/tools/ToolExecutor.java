package com.codey.tools;

import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 统一执行工具，并基于能力声明决定是否允许并行。
 */
public class ToolExecutor {
    private final ToolRegistry registry;
    private final WorkspaceToolContext context;

    public ToolExecutor(ToolRegistry registry) {
        this(registry, new WorkspaceToolContext(null, null));
    }

    public ToolExecutor(ToolRegistry registry, WorkspaceToolContext context) {
        this.registry = registry;
        this.context = context;
    }

    public ToolResult execute(ToolInvocation invocation) {
        return execute(invocation, null);
    }

    public ToolResult execute(ToolInvocation invocation, String workingDirectory) {
        return execute(invocation, workingDirectory, null);
    }

    /**
     * 执行工具并注入租户 ID 到上下文，供调用层工具按租户维度做业务操作。
     */
    public ToolResult execute(ToolInvocation invocation, String workingDirectory, String tenantId) {
        WorkspaceToolContext effectiveContext = context == null
                ? null
                : context.withWorkingDirectory(workingDirectory).withTenantId(tenantId);
        return registry.findByName(invocation.getToolName())
                .map(tool -> ToolRegistry.adaptResult(tool.execute(invocation, effectiveContext)))
                .orElseGet(() -> ToolResult.fail("Unknown tool: " + invocation.getToolName()));
    }

    public Path getWorkspaceRoot() {
        return context == null ? null : context.getWorkspaceRoot();
    }

    public boolean canRunInParallel(ToolInvocation invocation) {
        return registry.findByName(invocation.getToolName())
                .map(tool -> tool.capability().isReadOnly() && tool.capability().supportsParallelExecution())
                .orElse(false);
    }

    public boolean isReadOnly(ToolInvocation invocation) {
        return registry.findByName(invocation.getToolName())
                .map(tool -> tool.capability().isReadOnly())
                .orElse(canRunInParallel(invocation));
    }

    public List<ToolExecutionRecord> executeBatch(List<ToolInvocation> invocations) {
        return executeBatch(invocations, null);
    }

    public List<ToolExecutionRecord> executeBatch(List<ToolInvocation> invocations, String workingDirectory) {
        return executeBatch(invocations, workingDirectory, null);
    }

    /**
     * 批量执行工具并注入租户 ID 到上下文。
     */
    public List<ToolExecutionRecord> executeBatch(List<ToolInvocation> invocations, String workingDirectory, String tenantId) {
        if (invocations == null || invocations.isEmpty()) {
            return Collections.emptyList();
        }
        if (invocations.size() == 1) {
            ToolInvocation invocation = invocations.get(0);
            return Collections.singletonList(new ToolExecutionRecord(invocation, execute(invocation, workingDirectory, tenantId)));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(invocations.size());
        try {
            final String capturedTenantId = tenantId;
            List<Callable<ToolExecutionRecord>> tasks = new ArrayList<Callable<ToolExecutionRecord>>();
            for (final ToolInvocation invocation : invocations) {
                tasks.add(new Callable<ToolExecutionRecord>() {
                    @Override
                    public ToolExecutionRecord call() {
                        return new ToolExecutionRecord(invocation, execute(invocation, workingDirectory, capturedTenantId));
                    }
                });
            }

            List<Future<ToolExecutionRecord>> futures = executorService.invokeAll(tasks);
            List<ToolExecutionRecord> records = new ArrayList<ToolExecutionRecord>();
            for (Future<ToolExecutionRecord> future : futures) {
                records.add(future.get());
            }
            return records;
        } catch (Exception exception) {
            List<ToolExecutionRecord> failed = new ArrayList<ToolExecutionRecord>();
            for (ToolInvocation invocation : invocations) {
                failed.add(new ToolExecutionRecord(
                        invocation,
                        ToolResult.fail("batch execution failed: " + exception.getMessage())
                ));
            }
            return failed;
        } finally {
            executorService.shutdownNow();
        }
    }
}
