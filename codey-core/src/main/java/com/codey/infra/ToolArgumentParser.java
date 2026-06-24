package com.codey.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析工具参数文本，并尽量修复格式不完整的 JSON。
 */
public final class ToolArgumentParser {

    private ToolArgumentParser() {
    }

    public static Map<String, Object> parse(ObjectMapper objectMapper, String text) throws Exception {
        Map<String, Object> parsed = tryParse(objectMapper, text);
        return parsed == null ? new LinkedHashMap<String, Object>() : parsed;
    }

    public static Map<String, Object> tryParse(ObjectMapper objectMapper, String text) {
        if (objectMapper == null || isBlank(text)) {
            return null;
        }
        String trimmed = sanitize(text);
        Map<String, Object> direct = tryParseObjectNode(objectMapper, trimmed);
        if (direct != null) {
            return direct;
        }

        String repaired = repairJson(trimmed);
        Map<String, Object> repairedParsed = tryParseObjectNode(objectMapper, repaired);
        if (repairedParsed != null) {
            return repairedParsed;
        }

        String stripped = stripCodeFences(trimmed);
        Map<String, Object> strippedParsed = tryParseObjectNode(objectMapper, stripped);
        if (strippedParsed != null) {
            return strippedParsed;
        }

        String inner = parseDoubleEncodedString(objectMapper, trimmed);
        Map<String, Object> innerParsed = tryParseObjectNode(objectMapper, inner);
        if (innerParsed != null) {
            return innerParsed;
        }

        String segment = extractJsonSegment(trimmed);
        return tryParseObjectNode(objectMapper, segment);
    }

    private static Map<String, Object> tryParseObjectNode(ObjectMapper objectMapper, String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node != null && node.isObject()) {
                return objectMapper.convertValue(node,
                        objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String parseDoubleEncodedString(ObjectMapper objectMapper, String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node != null && node.isTextual()) {
                return sanitize(node.asText());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String stripCodeFences(String text) {
        if (isBlank(text) || !text.contains("```")) {
            return null;
        }
        String[] lines = text.split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return sanitize(builder.toString());
    }

    private static String extractJsonSegment(String text) {
        String objectSegment = extractBalancedSegment(text, '{', '}');
        if (!isBlank(objectSegment)) {
            return objectSegment;
        }
        return extractBalancedSegment(text, '[', ']');
    }

    private static String extractBalancedSegment(String text, char open, char close) {
        if (isBlank(text)) {
            return null;
        }
        int start = text.indexOf(open);
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String repairJson(String text) {
        if (isBlank(text)) {
            return null;
        }
        String normalized = text.replaceAll(",\\s*([}\\]])", "$1");
        int missingBraces = countMissingClosers(normalized, '{', '}');
        int missingBrackets = countMissingClosers(normalized, '[', ']');
        StringBuilder builder = new StringBuilder(normalized);
        for (int i = 0; i < missingBrackets; i++) {
            builder.append(']');
        }
        for (int i = 0; i < missingBraces; i++) {
            builder.append('}');
        }
        return builder.toString();
    }

    private static int countMissingClosers(String text, char open, char close) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == open) {
                depth++;
            } else if (current == close && depth > 0) {
                depth--;
            }
        }
        return depth;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\r' || current == '\n' || current == '\t' || current >= 0x20) {
                builder.append(current);
            }
        }
        return builder.toString().trim();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
