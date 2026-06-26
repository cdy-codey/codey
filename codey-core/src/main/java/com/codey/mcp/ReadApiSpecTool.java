package com.codey.mcp;

import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.*;

import com.codey.infra.ModelToolDefinition;
import com.codey.infra.NumberedLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取 API 规格文件，并支持按行范围截取。
 */
public class ReadApiSpecTool extends AbstractWorkspaceTool {
    private final WorkspaceToolPayloadFormatter payloadFormatter = new WorkspaceToolPayloadFormatter();

    public ReadApiSpecTool(com.codey.infra.WorkspaceGateway workspaceGateway) {
    }

    @Override
    public String name() {
        return "read_api_spec";
    }

    @Override
    public String displayName() {
        return "读取接口规格";
    }

    @Override
    public String description() {
        return "Read an API specification file with optional line range slicing.";
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
            String pathValue = readRequiredString(request, "path");
            Path target = context.resolvePath(pathValue);
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            int totalLines = lines.size();
            int startLine = normalizePositive(readInteger(request, "offset"), 1);
            int maxLines = readInteger(request, "limit") == null
                    ? Math.max(totalLines - startLine + 1, 0)
                    : Math.max(0, readInteger(request, "limit"));
            int fromIndex = Math.min(Math.max(startLine - 1, 0), totalLines);
            int toIndex = Math.min(fromIndex + maxLines, totalLines);

            com.codey.infra.ReadFileResult result = new com.codey.infra.ReadFileResult();
            result.setPath(context.relativize(target));
            result.setStartLine(totalLines == 0 ? 0 : fromIndex + 1);
            result.setEndLine(totalLines == 0 ? 0 : toIndex);
            result.setTotalLines(totalLines);
            result.setTruncated(toIndex < totalLines);
            result.setLines(toNumberedLines(lines.subList(fromIndex, toIndex), fromIndex + 1));
            return ToolResult.ok(
                    "Read API spec success:\n" + payloadFormatter.formatReadFileResult(result),
                    "已读取接口规格内容"
            );
        } catch (Exception exception) {
            return ToolResult.fail("读取接口规格失败：" + exception.getMessage());
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
        properties.put("path", stringProperty("API specification file path relative to the current working directory."));
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

    private int normalizePositive(Integer value, int defaultValue) {
        return value == null || value.intValue() <= 0 ? defaultValue : value.intValue();
    }

    private List<NumberedLine> toNumberedLines(List<String> lines, int startLine) {
        List<NumberedLine> numberedLines = new ArrayList<NumberedLine>();
        int lineNumber = startLine;
        for (String line : lines) {
            NumberedLine item = new NumberedLine();
            item.setLineNumber(lineNumber++);
            item.setContent(line);
            numberedLines.add(item);
        }
        return numberedLines;
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
