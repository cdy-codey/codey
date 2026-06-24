package com.codey.mcp;

import com.codey.tools.*;

import com.codey.infra.ModelToolDefinition;
import com.codey.infra.WorkspaceGateway;
import com.codey.infra.WorkspaceListRequest;
import com.codey.infra.WorkspaceListResult;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 列出工作区或指定子目录，并返回结构化目录信息。
 */
public class ListWorkspaceTool extends AbstractWorkspaceTool {
    private final WorkspaceGateway workspaceGateway;
    private final WorkspaceToolPayloadFormatter payloadFormatter = new WorkspaceToolPayloadFormatter();

    public ListWorkspaceTool(WorkspaceGateway workspaceGateway) {
        this.workspaceGateway = workspaceGateway;
    }

    @Override
    public String name() {
        return "list_workspace";
    }

    @Override
    public String displayName() {
        return "查看工作区";
    }

    @Override
    public String description() {
        return "List the workspace or a subdirectory and return structured directory information.";
    }

    @Override
    public ModelToolDefinition toModelToolDefinition() {
        ModelToolDefinition definition = new ModelToolDefinition();
        definition.setName(name());
        definition.setDescription(description());
        definition.setParameters(buildParameters());
        return definition;
    }

    @Override
    public ToolResult execute(ToolInvocation request, WorkspaceToolContext context) {
        try {
            WorkspaceListRequest workspaceRequest = new WorkspaceListRequest();
            workspaceRequest.setPathHint(readString(request, "pathHint"));
            workspaceRequest.setLimit(readInteger(request, "limit"));
            workspaceRequest.setMaxDepth(readInteger(request, "maxDepth"));
            workspaceRequest.setIncludeHidden(readBoolean(request, "includeHidden"));

            WorkspaceListResult result = workspaceGateway.listWorkspaceResult(workspaceRequest);
            return ToolResult.ok(
                    "Workspace list result:\n" + payloadFormatter.formatWorkspaceListResult(result),
                    "已返回目录内容"
            );
        } catch (Exception exception) {
            return ToolResult.fail("查看工作区失败：" + exception.getMessage());
        }
    }

    @Override
    public ToolCapability capability() {
        return ToolCapability.readOnlyParallel();
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("pathHint", stringProperty("Optional subdirectory path hint. Defaults to the current workspace."));
        properties.put("limit", integerProperty("Maximum number of entries to return."));
        properties.put("maxDepth", integerProperty("Maximum traversal depth. Default is 1."));
        properties.put("includeHidden", booleanProperty("Whether hidden files and directories are included."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList());
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private String readString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInteger(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Boolean readBoolean(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> booleanProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "boolean");
        property.put("description", description);
        return property;
    }
}


