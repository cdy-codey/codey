package com.codey.tools;

import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutor.class);

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

    public ToolResult execute(ToolInvocation invocation, String workingDirectory, String tenantId) {
        return execute(invocation, workingDirectory, tenantId, false);
    }

    /**
     * 执行工具并注入租户 ID 与表单模式标记到上下文。
     * 表单模式下写文件工具会跳过 diff 对比直接整文件替换，降低大文件写入开销。
     * 工具执行失败（返回失败结果或抛异常）时统一输出日志，便于排查工具调用问题。
     */
    public ToolResult execute(ToolInvocation invocation, String workingDirectory, String tenantId, boolean formMode) {
        WorkspaceToolContext effectiveContext = context == null
                ? null
                : context.withWorkingDirectory(workingDirectory).withTenantId(tenantId).withFormMode(formMode);
        ToolResult result;
        try {
            result = registry.findByName(invocation.getToolName())
                    .map(tool -> ToolRegistry.adaptResult(tool.execute(invocation, effectiveContext)))
                    .orElseGet(() -> ToolResult.fail("Unknown tool: " + invocation.getToolName()));
        } catch (Exception exception) {
            // 工具自身抛出异常：记录日志后保持原行为（向上抛出），避免静默吞掉执行故障。
            LOGGER.error("[tool-execution-failed] tool={}, arguments={}, exception: {}",
                    invocation.getToolName(), limitArguments(invocation), exception.getMessage(), exception);
            throw exception;
        }
        if (result == null || !result.isSuccess()) {
            String errorMessage = result == null ? "tool returned null result" : result.getErrorMessage();
            LOGGER.warn("[tool-execution-failed] tool={}, arguments={}, error={}",
                    invocation.getToolName(), limitArguments(invocation), errorMessage);
        }
        return result;
    }

    // 限制参数长度，避免超长 arguments 撑爆日志。
    private String limitArguments(ToolInvocation invocation) {
        if (invocation == null || invocation.getArguments() == null) {
            return "{}";
        }
        String arguments = invocation.getArguments().toString();
        return arguments.length() <= 500 ? arguments : arguments.substring(0, 500) + "...";
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
        return executeBatch(invocations, workingDirectory, tenantId, false);
    }

    /**
     * 批量执行工具并注入租户 ID 与表单模式标记到上下文。
     */
    public List<ToolExecutionRecord> executeBatch(List<ToolInvocation> invocations, String workingDirectory, String tenantId, boolean formMode) {
        if (invocations == null || invocations.isEmpty()) {
            return Collections.emptyList();
        }
        if (invocations.size() == 1) {
            ToolInvocation invocation = invocations.get(0);
            return Collections.singletonList(new ToolExecutionRecord(invocation, execute(invocation, workingDirectory, tenantId, formMode)));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(invocations.size());
        try {
            final String capturedTenantId = tenantId;
            final boolean capturedFormMode = formMode;
            List<Callable<ToolExecutionRecord>> tasks = new ArrayList<Callable<ToolExecutionRecord>>();
            for (final ToolInvocation invocation : invocations) {
                tasks.add(new Callable<ToolExecutionRecord>() {
                    @Override
                    public ToolExecutionRecord call() {
                        return new ToolExecutionRecord(invocation, execute(invocation, workingDirectory, capturedTenantId, capturedFormMode));
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
            // 批量执行整体失败（线程池异常等）：记录日志，并为每个请求返回失败结果。
            LOGGER.error("[tool-batch-execution-failed] invocations={}, exception: {}",
                    invocations.size(), exception.getMessage(), exception);
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
