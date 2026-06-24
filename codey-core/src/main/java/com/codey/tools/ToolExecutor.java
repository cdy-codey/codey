package com.codey.tools;

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
        return registry.findByName(invocation.getToolName())
                .map(tool -> ToolRegistry.adaptResult(tool.execute(invocation, context)))
                .orElseGet(() -> ToolResult.fail("Unknown tool: " + invocation.getToolName()));
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
        if (invocations == null || invocations.isEmpty()) {
            return Collections.emptyList();
        }
        if (invocations.size() == 1) {
            ToolInvocation invocation = invocations.get(0);
            return Collections.singletonList(new ToolExecutionRecord(invocation, execute(invocation)));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(invocations.size());
        try {
            List<Callable<ToolExecutionRecord>> tasks = new ArrayList<Callable<ToolExecutionRecord>>();
            for (final ToolInvocation invocation : invocations) {
                tasks.add(new Callable<ToolExecutionRecord>() {
                    @Override
                    public ToolExecutionRecord call() {
                        return new ToolExecutionRecord(invocation, execute(invocation));
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
