package com.codey.mcp;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.AbstractWorkspaceTool;
import com.codey.tools.WorkspaceToolContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 JSON 文件中按字段名或值搜索，返回命中节点的 pointer、类型和格式化预览。
 * 保留 AI 的搜索效应，避免对 JSON 结构盲目精确定位。
 */
public class SearchJsonTool extends AbstractWorkspaceTool {
    private static final int MAX_MATCHES = 50;
    private static final int MAX_PREVIEW_LINES = 10;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "search_json";
    }

    @Override
    public String displayName() {
        return "格式化搜索文件";
    }

    @Override
    public String description() {
        return "Primary search tool for .json files. Prefer this over search_content for JSON. Search by key name or value and return matching pointers with formatted previews.";
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
            String keyQuery = readString(request, "key");
            String valueQuery = readString(request, "value");
            if ((keyQuery == null || keyQuery.trim().isEmpty())
                    && (valueQuery == null || valueQuery.trim().isEmpty())) {
                throw new IllegalArgumentException("at least one of key or value must be provided");
            }

            Path target = context.resolvePath(pathValue);
            String originalContent = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(originalContent);
            if (root == null) {
                throw new IllegalArgumentException("target json is empty");
            }

            List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
            searchNode(root, "", keyQuery, valueQuery, matches, 0);

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("path", relativize(context, target));
            payload.put("keyQuery", keyQuery);
            payload.put("valueQuery", valueQuery);
            payload.put("totalMatches", matches.size());
            payload.put("truncated", matches.size() >= MAX_MATCHES);
            payload.put("matches", matches);
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return ToolResult.ok("Search json success:\n" + result, "已返回格式化搜索结果");
        } catch (Exception exception) {
            return ToolResult.fail("格式化搜索失败：" + exception.getMessage());
        }
    }

    /**
     * 递归遍历 JSON 节点，按 key 或 value 命中时记录 pointer 和预览。
     */
    private void searchNode(JsonNode node, String currentPointer,
                            String keyQuery, String valueQuery,
                            List<Map<String, Object>> matches, int depth) {
        if (node == null || node.isMissingNode() || matches.size() >= MAX_MATCHES) {
            return;
        }

        if (node.isObject()) {
            // 遍历子字段
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext() && matches.size() < MAX_MATCHES) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode child = field.getValue();
                String childPointer = currentPointer + "/" + escapePointerToken(fieldName);

                if (matchesKey(fieldName, keyQuery)) {
                    matches.add(buildMatch(childPointer, child));
                }
                searchNode(child, childPointer, keyQuery, valueQuery, matches, depth + 1);
            }
            return;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int index = 0; index < arrayNode.size() && matches.size() < MAX_MATCHES; index++) {
                JsonNode child = arrayNode.get(index);
                String childPointer = currentPointer + "/" + index;
                searchNode(child, childPointer, keyQuery, valueQuery, matches, depth + 1);
            }
            return;
        }

        // 叶子节点：按 value 匹配
        if (matchesValue(node, valueQuery)) {
            matches.add(buildMatch(currentPointer, node));
        }
    }

    /**
     * 判断字段名是否匹配搜索条件。
     */
    private boolean matchesKey(String fieldName, String keyQuery) {
        if (keyQuery == null || keyQuery.trim().isEmpty()) {
            return false;
        }
        return fieldName.toLowerCase().contains(keyQuery.toLowerCase().trim());
    }

    /**
     * 判断叶子节点值是否匹配搜索条件。
     */
    private boolean matchesValue(JsonNode node, String valueQuery) {
        if (valueQuery == null || valueQuery.trim().isEmpty()) {
            return false;
        }
        String query = valueQuery.toLowerCase().trim();
        // 按文本形式匹配
        String textValue = node.asText();
        if (textValue != null && textValue.toLowerCase().contains(query)) {
            return true;
        }
        // 按格式化 JSON 形式匹配
        try {
            String formatted = objectMapper.writeValueAsString(node);
            if (formatted != null && formatted.toLowerCase().contains(query)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 构造单条命中结果，包含 pointer、类型和格式化预览。
     */
    private Map<String, Object> buildMatch(String pointer, JsonNode node) {
        Map<String, Object> match = new LinkedHashMap<String, Object>();
        match.put("pointer", pointer);
        match.put("nodeType", nodeType(node));

        String formatted;
        try {
            formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception exception) {
            formatted = node.asText();
        }
        List<String> previewLines = buildPreviewLines(formatted);
        match.put("preview", previewLines);
        return match;
    }

    /**
     * 截取格式化内容的前 MAX_PREVIEW_LINES 行作为预览。
     */
    private List<String> buildPreviewLines(String formatted) {
        if (formatted == null || formatted.isEmpty()) {
            return new ArrayList<String>();
        }
        String[] lines = formatted.split("\\R", -1);
        List<String> result = new ArrayList<String>();
        int limit = Math.min(lines.length, MAX_PREVIEW_LINES);
        for (int index = 0; index < limit; index++) {
            result.add((index + 1) + ": " + lines[index]);
        }
        return result;
    }

    /**
     * 转义 JSON Pointer token 中的特殊字符。
     */
    private String escapePointerToken(String token) {
        return token.replace("~", "~0").replace("/", "~1");
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

    private String relativize(WorkspaceToolContext context, Path target) {
        if (context == null) {
            return target == null ? "." : target.toString();
        }
        return context.relativize(target);
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
        properties.put("key", stringProperty("Optional field name to search for. Case-insensitive substring match."));
        properties.put("value", stringProperty("Optional value to search for. Case-insensitive substring match against leaf nodes."));

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

    private String readString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
