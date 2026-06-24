package com.codey.mcp;

import com.codey.tools.*;

import com.codey.infra.ModelToolDefinition;
import com.codey.infra.SearchCodeRequest;
import com.codey.infra.SearchCodeResult;
import com.codey.infra.WorkspaceGateway;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在 JSON、YAML 等 API 描述文件中搜索接口信息。
 */
public class QueryApiInfoTool extends AbstractWorkspaceTool {
    private final WorkspaceGateway workspaceGateway;
    private final WorkspaceToolPayloadFormatter payloadFormatter = new WorkspaceToolPayloadFormatter();

    public QueryApiInfoTool(WorkspaceGateway workspaceGateway) {
        this.workspaceGateway = workspaceGateway;
    }

    @Override
    public String name() {
        return "query_api_info";
    }

    @Override
    public String displayName() {
        return "查询接口信息";
    }

    @Override
    public String description() {
        return "Search API information in json, yaml and similar spec files.";
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
            SearchCodeRequest searchRequest = new SearchCodeRequest();
            searchRequest.setKeyword(readRequiredString(request, "keyword"));
            searchRequest.setPathHint(readString(request, "pathHint"));
            searchRequest.setRegex(readBoolean(request, "regex"));
            searchRequest.setCaseSensitive(readBoolean(request, "caseSensitive"));
            searchRequest.setContextLines(readInteger(request, "contextLines"));
            searchRequest.setMaxResults(readInteger(request, "maxResults"));
            searchRequest.setFilePattern(isBlank(readString(request, "filePattern"))
                    ? "**/*.{json,yaml,yml}"
                    : readString(request, "filePattern"));

            SearchCodeResult result = workspaceGateway.searchCodeResult(searchRequest);
            return ToolResult.ok(
                    "API info result:\n" + payloadFormatter.formatSearchCodeResult(result),
                    "已返回接口相关信息"
            );
        } catch (Exception exception) {
            return ToolResult.fail("查询接口信息失败：" + exception.getMessage());
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
        properties.put("keyword", stringProperty("Keyword to search, for example users, pageNum, records."));
        properties.put("pathHint", stringProperty("Optional subdirectory path hint. Defaults to the whole workspace."));
        properties.put("regex", booleanProperty("Whether to treat keyword as a regular expression."));
        properties.put("caseSensitive", booleanProperty("Whether the search is case sensitive."));
        properties.put("contextLines", integerProperty("How many context lines to keep around each hit."));
        properties.put("maxResults", integerProperty("Maximum number of matches to return."));
        properties.put("filePattern", stringProperty("Optional file glob. Defaults to json/yaml/yml files."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("keyword"));
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

