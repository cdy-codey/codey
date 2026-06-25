package com.codey.loop;

import com.codey.tool.ToolInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 为工具请求构建稳定签名，用于识别重复调用。
 */
public final class ToolRequestSignature {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ToolRequestSignature() {
    }

    public static String from(ToolInvocation request) {
        if (request == null) {
            return "";
        }
        String toolName = request.getToolName() == null ? "" : request.getToolName();
        JsonNode argumentsNode = normalize(OBJECT_MAPPER.valueToTree(request.getArguments()));
        try {
            return toolName + "|" + OBJECT_MAPPER.writeValueAsString(argumentsNode);
        } catch (Exception exception) {
            return toolName + "|" + String.valueOf(request.getArguments());
        }
    }

    private static JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return OBJECT_MAPPER.nullNode();
        }
        if (node.isObject()) {
            ObjectNode normalized = OBJECT_MAPPER.createObjectNode();
            Map<String, JsonNode> sortedFields = new TreeMap<String, JsonNode>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                sortedFields.put(entry.getKey(), normalize(entry.getValue()));
            }
            for (Map.Entry<String, JsonNode> entry : sortedFields.entrySet()) {
                normalized.set(entry.getKey(), entry.getValue());
            }
            return normalized;
        }
        if (node.isArray()) {
            ArrayNode normalized = OBJECT_MAPPER.createArrayNode();
            for (JsonNode item : node) {
                normalized.add(normalize(item));
            }
            return normalized;
        }
        return node;
    }
}
