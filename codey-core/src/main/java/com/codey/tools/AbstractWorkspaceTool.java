package com.codey.tools;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolContext;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolMetadata;
import com.codey.tool.ToolResult;
import com.codey.tool.ToolSpec;

/**
 * 内核内置工具的抽象基类。
 * 统一保留旧的工作区上下文执行方式，同时只对外暴露 common ToolSpec 接口。
 */
public abstract class AbstractWorkspaceTool implements ToolSpec {

    public abstract String name();

    /**
     * 所有工具都必须显式提供展示名称，避免 UI 继续依赖工具真实标识。
     */
    public abstract String displayName();

    public abstract String description();

    public abstract ModelToolDefinition toModelToolDefinition();

    public abstract ToolResult execute(ToolInvocation invocation, WorkspaceToolContext context);

    /**
     * 兼容内核侧历史调用点，默认在无工作区上下文时执行。
     */
    public ToolResult execute(ToolInvocation invocation) {
        return execute(invocation, null);
    }

    @Override
    public ToolDescriptor descriptor() {
        ModelToolDefinition definition = toModelToolDefinition();
        ToolDescriptor descriptor = new ToolDescriptor();
        descriptor.setName(definition == null ? name() : definition.getName());
        descriptor.setDisplayName(displayName());
        descriptor.setDescription(definition == null ? description() : definition.getDescription());
        descriptor.setParameters(definition == null ? null : definition.getParameters());
        return descriptor;
    }

    @Override
    public ToolMetadata metadata() {
        ToolMetadata metadata = ToolMetadata.standard();
        // 核心工作区工具统一挂到 workspace-core bundle，供 skill 用 bundle/group 做单一口径筛选。
        metadata.setBundle("workspace-core");
        metadata.setGroup(resolveGroup(name()));
        return metadata;
    }

    @Override
    public com.codey.tool.ToolResult execute(com.codey.tool.ToolInvocation invocation,
                                             ToolContext context) {
        WorkspaceToolContext workspaceContext = context instanceof WorkspaceToolContext
                ? (WorkspaceToolContext) context
                : new WorkspaceToolContext(
                null,
                null,
                null,
                context == null ? null : context.getRequestId(),
                context == null ? null : context.getSessionId(),
                context == null ? null : context.getAttributes()
        );
        return execute(invocation, workspaceContext);
    }

    @Override
    public com.codey.tool.ToolCapability capability() {
        return ToolCapability.standard();
    }

    private String resolveGroup(String toolName) {
        if ("list_workspace".equals(toolName) || "project_map".equals(toolName)) {
            return "workspace";
        }
        if ("read_file".equals(toolName) || "search_code".equals(toolName)) {
            return "inspection";
        }
        if ("query_api_info".equals(toolName) || "read_api_spec".equals(toolName)) {
            return "api";
        }
        if ("write_file".equals(toolName)
                || "edit_file".equals(toolName)
                || "apply_structured_patch".equals(toolName)
                || "delete_file".equals(toolName)
                || "edit_code".equals(toolName)) {
            return "mutation";
        }
        return "workspace";
    }
}
