package com.codey.mcp;

import com.codey.infra.ModelToolDefinition;
import com.codey.infra.NumberedLine;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.AbstractWorkspaceTool;
import com.codey.tools.WorkspaceToolContext;
import com.codey.tools.WorkspaceToolPayloadFormatter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 JSON Pointer 读取局部节点，并返回格式化后的按行内容。
 */
public class ReadJsonTool extends AbstractWorkspaceTool {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceToolPayloadFormatter payloadFormatter = new WorkspaceToolPayloadFormatter();

    @Override
    public String name() {
        return "read_json";
    }

    @Override
    public String displayName() {
        return "格式化读取文件";
    }

    @Override
    public String description() {
        return "Primary reading tool for .json files. Prefer this over read_file for JSON. Read a JSON file or subtree by pointer and return formatted numbered lines.";
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
            String pointer = normalizePointer(readString(request, "pointer"));
            Path target = context.resolvePath(pathValue);
            String originalContent = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(originalContent);
            if (root == null) {
                throw new IllegalArgumentException("target json is empty");
            }
            JsonNode targetNode = resolveNode(root, pointer);
            if (targetNode == null || targetNode.isMissingNode()) {
                throw new IllegalArgumentException("target pointer does not exist: " + pointer);
            }

            String formattedContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(targetNode);
            List<String> lines = Arrays.asList(formattedContent.split("\\R", -1));
            int totalLines = lines.size();
            int startLine = normalizePositive(readInteger(request, "offset"), 1);
            int maxLines = readInteger(request, "limit") == null
                    ? Math.max(totalLines - startLine + 1, 0)
                    : Math.max(0, readInteger(request, "limit"));
            int fromIndex = Math.min(Math.max(startLine - 1, 0), totalLines);
            int toIndex = Math.min(fromIndex + maxLines, totalLines);

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("path", relativize(context, target));
            payload.put("pointer", pointer);
            payload.put("nodeType", nodeType(targetNode));
            payload.put("startLine", totalLines == 0 ? 0 : fromIndex + 1);
            payload.put("endLine", totalLines == 0 ? 0 : toIndex);
            payload.put("totalLines", totalLines);
            payload.put("truncated", toIndex < totalLines);
            payload.put("content", payloadFormatter.formatNumberedLines(toNumberedLines(lines.subList(fromIndex, toIndex), fromIndex + 1)));
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return ToolResult.ok("Read json success:\n" + result, "已读取格式化内容");
        } catch (Exception exception) {
            return ToolResult.fail("格式化读取失败：" + exception.getMessage());
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
        properties.put("path", stringProperty("Target JSON file path relative to the current working directory."));
        properties.put("pointer", stringProperty("Optional JSON Pointer path. Use empty string or omit it to read the root node."));
        properties.put("offset", integerProperty("Start line number in the formatted result, minimum 1."));
        properties.put("limit", integerProperty("Maximum number of formatted lines to read."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private JsonNode resolveNode(JsonNode root, String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return root;
        }
        return root.at(pointer);
    }

    private String normalizePointer(String pointer) {
        String trimmed = pointer == null ? "" : pointer.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException("json pointer must start with '/' or be empty for root");
        }
        return trimmed;
    }

    private String nodeType(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "missing";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isNull()) {
            return "null";
        }
        return node.getNodeType().name().toLowerCase();
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

    private int normalizePositive(Integer value, int defaultValue) {
        return value == null || value.intValue() <= 0 ? defaultValue : value.intValue();
    }

    private String relativize(WorkspaceToolContext context, Path target) {
        if (context == null) {
            return target == null ? "." : target.toString();
        }
        return context.relativize(target);
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
