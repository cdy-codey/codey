package com.codey.loop;

import com.fasterxml.jackson.databind.ObjectMapper;

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
            FinalResult finalResult = objectMapper.readValue(normalized, FinalResult.class);
            if (isBlank(finalResult.getStatus())) {
                return FinalResultParseResult.failure("Parse error: missing status");
            }
            if (isBlank(finalResult.getSummary())) {
                return FinalResultParseResult.failure("Parse error: missing summary");
            }
            return FinalResultParseResult.success(finalResult);
        } catch (Exception exception) {
            return FinalResultParseResult.failure("Parse error: " + exception.getMessage());
        }
    }

    private FinalResultParseResult parsePlainText(String content) {
        if (isBlank(content)) {
            return FinalResultParseResult.failure("Parse error: empty content");
        }
        FinalResult finalResult = new FinalResult();
        finalResult.setStatus("FINISH");
        finalResult.setSummary(content.trim());
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
}
