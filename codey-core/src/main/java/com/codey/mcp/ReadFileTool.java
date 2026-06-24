package com.codey.mcp;

import com.codey.tools.*;

import com.codey.infra.ModelToolDefinition;
import com.codey.infra.ReadFileRequest;
import com.codey.infra.ReadFileResult;
import com.codey.infra.WorkspaceGateway;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读取文件内容，并支持按行范围截取结果。
 */
public class ReadFileTool extends AbstractWorkspaceTool {
    private final WorkspaceGateway workspaceGateway;
    private final WorkspaceToolPayloadFormatter payloadFormatter = new WorkspaceToolPayloadFormatter();

    public ReadFileTool(WorkspaceGateway workspaceGateway) {
        this.workspaceGateway = workspaceGateway;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String displayName() {
        return "读取文件";
    }

    @Override
    public String description() {
        return "Read file content with optional offset and limit.";
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
            ReadFileRequest readRequest = new ReadFileRequest();
            readRequest.setPath(readRequiredString(request, "path"));
            readRequest.setOffset(readInteger(request, "offset"));
            readRequest.setLimit(readInteger(request, "limit"));

            ReadFileResult result = workspaceGateway.readFileResult(readRequest);
            return ToolResult.ok(
                    "Read file success:\n" + payloadFormatter.formatReadFileResult(result),
                    "已读取文件内容"
            );
        } catch (Exception exception) {
            return ToolResult.fail("读取文件失败：" + exception.getMessage());
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
        properties.put("path", stringProperty("File path to read."));
        properties.put("offset", integerProperty("Start line number, minimum 1."));
        properties.put("limit", integerProperty("Maximum number of lines to read."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private String readRequiredString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return String.valueOf(value);
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
}

