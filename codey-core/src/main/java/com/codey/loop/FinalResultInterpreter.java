package com.codey.loop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将模型输出解析为 `FinalResult`，并兼容围栏 JSON 与纯文本回退。
 */
public class FinalResultInterpreter {
    private static final Pattern FENCED_JSON_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
    private final ObjectMapper objectMapper;

    public FinalResultInterpreter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FinalResultParseResult parse(String rawContent) {
        try {
            String normalized = normalize(rawContent);
            if (!looksLikeJsonObject(normalized)) {
                return parsePlainText(normalized);
            }
            if (normalized.contains("\"_view_type\"") && !normalized.contains("\"status\"")) {
                return parseRawView(normalized);
            }
            FinalResult finalResult = objectMapper.readValue(normalized, FinalResult.class);
            if (isBlank(finalResult.getStatus())) {
                return FinalResultParseResult.failure("Parse error: missing status");
            }
            if (finalResult.getView() == null) {
                return FinalResultParseResult.failure("Parse error: missing view");
            }
            return FinalResultParseResult.success(finalResult);
        } catch (Exception exception) {
            // 结构化解析失败时，降级到松散提取，避免因 JSON 中个别字段格式不合法导致整个响应被丢弃
            FinalResultParseResult fallbackResult = parseLoose(rawContent);
            if (fallbackResult != null && fallbackResult.isSuccess()) {
                return fallbackResult;
            }
            return FinalResultParseResult.failure("Parse error: " + exception.getMessage());
        }
    }

    /**
     * 松散解析：当 Jackson 结构化反序列化失败时（如 view 字段中包含未转义的特殊字符），
     * 通过正则表达式逐字段提取 status / summary / view，保证响应不丢失。
     */
    private FinalResultParseResult parseLoose(String rawContent) {
        String normalized = normalize(rawContent);
        if (!looksLikeJsonObject(normalized)) {
            return parsePlainText(normalized);
        }
        // 去掉最外层花括号，提取字段级内容
        String inner = unwrapOuterBraces(normalized);
        if (inner == null) {
            return null;
        }
        String status = extractTopLevelStringField(inner, "status");
        if (isBlank(status)) {
            status = extractTopLevelStringField(inner, "Status");
        }
        if (isBlank(status)) {
            return null;
        }
        String summary = extractTopLevelStringField(inner, "summary");
        if (isBlank(summary)) {
            summary = extractTopLevelStringField(inner, "Summary");
        }
        Object view = extractViewField(inner, normalized);
        if (view == null) {
            return null;
        }
        FinalResult finalResult = new FinalResult();
        finalResult.setStatus(status.trim());
        finalResult.setSummary(isBlank(summary) ? null : summary.trim());
        finalResult.setView(view);
        return FinalResultParseResult.success(finalResult);
    }

    /**
     * 提取顶层字符串字段（"fieldName": "value"），支持值中包含转义引号。
     */
    private String extractTopLevelStringField(String inner, String fieldName) {
        // 匹配 "fieldName": "..." 模式，兼容字段名前后的空格
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(inner);
        if (!matcher.find()) {
            return null;
        }
        int valueStart = matcher.end();
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = valueStart; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (escaping) {
                value.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                // 检查下一个非空白字符是否为 , 或 }，以确认这是字段值的结束引号
                int next = findNextNonWhitespace(inner, i + 1);
                if (next < 0 || inner.charAt(next) == ',' || inner.charAt(next) == '}') {
                    return value.toString();
                }
                // 引号出现在值中间（如中文引号），保留原字符
                value.append(ch);
                continue;
            }
            value.append(ch);
        }
        return value.toString();
    }

    /**
     * 从 JSON 文本中提取 view 字段的值，先尝试结构化解析，失败则作为纯文本处理。
     */
    private Object extractViewField(String inner, String fullJson) {
        // 先尝试找到 "view": 的位置，提取其 JSON 值
        String viewRaw = extractRawFieldValue(inner, "view");
        if (viewRaw == null) {
            viewRaw = extractRawFieldValue(inner, "View");
        }
        if (viewRaw == null) {
            return createTextView("（解析结果）");
        }
        // 尝试将提取出的 view 值解析为结构化对象
        try {
            return objectMapper.readValue(viewRaw, Object.class);
        } catch (Exception ignored) {
            // 结构化解析失败，作为纯文本视图处理
        }
        // 去除可能的引号包裹
        String trimmed = viewRaw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return createTextView(trimmed);
    }

    /**
     * 提取指定字段的原始 JSON 值字符串（不含字段名），支持对象 {}、数组 []、字符串 "..." 三种类型。
     */
    private String extractRawFieldValue(String inner, String fieldName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(inner);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.end();
        if (start >= inner.length()) {
            return null;
        }
        char first = inner.charAt(start);
        if (first == '{') {
            return extractBalanced(inner, start, '{', '}');
        }
        if (first == '[') {
            return extractBalanced(inner, start, '[', ']');
        }
        if (first == '"') {
            return extractQuotedString(inner, start);
        }
        // 处理未加引号的简单值（如 true / false / null / 数字）
        StringBuilder literal = new StringBuilder();
        for (int i = start; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (ch == ',' || ch == '}') {
                break;
            }
            literal.append(ch);
        }
        String value = literal.toString().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 提取平衡的括号内容（{} 或 []），正确处理嵌套和字符串内的转义。
     */
    private String extractBalanced(String source, int start, char open, char close) {
        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (inString && ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        // 括号不匹配，返回从 start 到结尾的内容
        return source.substring(start);
    }

    /**
     * 提取双引号包裹的字符串内容，正确处理转义。
     */
    private String extractQuotedString(String source, int start) {
        // start 指向开头的双引号
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = start + 1; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (escaping) {
                value.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                return value.toString();
            }
            value.append(ch);
        }
        return value.toString();
    }

    /**
     * 去掉最外层花括号，返回内部内容。
     */
    private String unwrapOuterBraces(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    /**
     * 从指定位置开始查找下一个非空白字符的索引。
     */
    private int findNextNonWhitespace(String source, int start) {
        for (int i = start; i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private FinalResultParseResult parsePlainText(String content) {
        if (isBlank(content)) {
            return FinalResultParseResult.failure("Parse error: empty content");
        }
        FinalResult finalResult = new FinalResult();
        finalResult.setStatus("FINISH");
        finalResult.setView(createTextView(content.trim()));
        return FinalResultParseResult.success(finalResult);
    }

    private FinalResultParseResult parseRawView(String content) throws JsonProcessingException {
        FinalResult finalResult = new FinalResult();
        finalResult.setStatus("FINISH");
        finalResult.setView(objectMapper.readValue(content, Object.class));
        return FinalResultParseResult.success(finalResult);
    }

    private String normalize(String rawContent) {
        if (rawContent == null) {
            return "";
        }
        String normalized = rawContent.trim();
        String fullyWrapped = unwrapWholeFence(normalized);
        if (looksLikeJsonObject(fullyWrapped)) {
            return fullyWrapped;
        }
        String fencedJson = extractLastFencedJson(normalized);
        if (looksLikeJsonObject(fencedJson)) {
            return fencedJson;
        }
        String trailingJson = extractLastJsonObject(normalized);
        if (looksLikeJsonObject(trailingJson)) {
            return trailingJson;
        }
        return fullyWrapped;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String unwrapWholeFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring("```json".length()).trim();
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring("```".length()).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private String extractLastFencedJson(String content) {
        Matcher matcher = FENCED_JSON_PATTERN.matcher(content);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last == null ? null : last.trim();
    }

    private String extractLastJsonObject(String content) {
        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        int start = -1;
        String last = null;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (inString && current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
                continue;
            }
            if (current == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    last = content.substring(start, index + 1);
                    start = -1;
                }
            }
        }
        return last == null ? null : last.trim();
    }

    private boolean looksLikeJsonObject(String value) {
        return value != null && value.trim().startsWith("{") && value.trim().endsWith("}");
    }

    private Map<String, Object> createTextView(String content) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("_view_type", "text");
        view.put("content", content);
        return view;
    }
}
