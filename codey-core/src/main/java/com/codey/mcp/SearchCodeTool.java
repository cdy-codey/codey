package com.codey.mcp;

import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.*;

import com.codey.infra.ModelToolDefinition;
import com.codey.infra.SearchCodeRequest;
import com.codey.infra.SearchCodeResult;
import com.codey.infra.WorkspaceGateway;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在工作区内按关键字或正则搜索代码内容。
 */
public class SearchCodeTool extends AbstractWorkspaceTool {
    private final WorkspaceGateway workspaceGateway;
    private final WorkspaceToolPayloadFormatter payloadFormatter = new WorkspaceToolPayloadFormatter();

    public SearchCodeTool(WorkspaceGateway workspaceGateway) {
        this.workspaceGateway = workspaceGateway;
    }

    @Override
    public String name() {
        return "search_code";
    }

    @Override
    public String displayName() {
        return "搜索代码";
    }

    @Override
    public String description() {
        return "Search code content in the workspace with regex, limits and context lines.";
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
            searchRequest.setFilePattern(readString(request, "filePattern"));

            SearchCodeResult result = workspaceGateway.searchCodeResult(searchRequest);
            return ToolResult.ok(
                    "Search code result:\n" + payloadFormatter.formatSearchCodeResult(result),
                    "已返回搜索结果"
            );
        } catch (Exception exception) {
            return ToolResult.fail("搜索代码失败：" + exception.getMessage());
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
        properties.put("keyword", stringProperty("Keyword or regular expression to search for."));
        properties.put("pathHint", stringProperty("Optional subdirectory path hint. Defaults to the whole workspace."));
        properties.put("regex", booleanProperty("Whether the keyword is treated as a regular expression."));
        properties.put("caseSensitive", booleanProperty("Whether the search is case sensitive."));
        properties.put("contextLines", integerProperty("Number of context lines to keep around each match."));
        properties.put("maxResults", integerProperty("Maximum number of matches to return."));
        properties.put("filePattern", stringProperty("Optional file glob, for example **/*.vue or *.json."));

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

