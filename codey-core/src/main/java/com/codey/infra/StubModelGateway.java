package com.codey.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第一版占位模型实现。
 * 使用标准 tool_calls 与最终结果 JSON 模拟主循环。
 */
public class StubModelGateway implements ModelGateway {
    private static final Pattern JSON_STRING_PATTERN_TEMPLATE = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]+)\"");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ModelResponse chat(ModelRequest request) {
        String prompt = flattenMessages(request);
        String currentGoal = extractCurrentGoal(prompt);
        String workingDirectory = extractWorkingDirectory(prompt);
        String targetFile = extractFirstContextFile(prompt);
        String apiHintFile = extractApiHintFile(prompt);
        java.util.List<String> toolResults = extractSectionItems(prompt, "工具结果");
        java.util.List<String> editResults = extractSectionItems(prompt, "编辑结果");

        if (!editResults.isEmpty()) {
            return finish(buildEditSummary(currentGoal, workingDirectory));
        }

        if (containsPrefixedItem(toolResults, "API info result:") && !targetFile.isEmpty()) {
            String parsedApiPath = extractJsonField(prompt, "path");
            String apiMethod = extractJsonField(prompt, "method");
            return toolCall("edit_code", mapOf(
                    "file", targetFile,
                    "mode", "INSERT_BEFORE",
                    "targetMarker", "</script>",
                    "instruction", "请根据当前上下文补齐代码与接口的联动逻辑",
                    "generatedContent", buildGeneratedContent(parsedApiPath, apiMethod)
            ));
        }

        if (containsPrefixedItem(toolResults, "Read file success:")
                && !containsPrefixedItem(toolResults, "API info result:")) {
            if (apiHintFile.isEmpty()) {
                return finish(buildReadSummary(currentGoal, workingDirectory, targetFile));
            }
            return toolCall("query_api_info", mapOf("keyword", apiHintFile, "pathHint", apiHintFile));
        }

        if (asksAboutWorkspace(currentGoal)) {
            String workspaceListResult = findFirstPrefixedItem(toolResults, "Workspace list result:");
            if (isBlank(workspaceListResult)) {
                return toolCall("list_workspace", mapOf("pathHint", "."));
            }
            return finish(buildWorkspaceSummary(workingDirectory, workspaceListResult));
        }

        // 占位模型也要先看当前轮用户目标，避免在没有上下文文件时直接短路成固定回复。
        if (shouldReplyDirectly(currentGoal, targetFile)) {
            return finish(buildDirectReply(currentGoal, workingDirectory));
        }

        if (!targetFile.isEmpty()) {
            return toolCall("read_file", mapOf("path", targetFile));
        }

        return finish(buildGoalAwareSummary(currentGoal, workingDirectory));
    }

    /**
     * 测试桩仅用于本地测试，内部按旧逻辑读取拍平文本，
     * 但数据来源已经统一改成结构化消息。
     */
    private String flattenMessages(ModelRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request == null) {
            return "";
        }
        for (ModelMessage message : request.getMessages()) {
            if (message == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("[").append(safe(message.getRole())).append("]\n");
            if (message.hasToolCalls()) {
                if (!isBlank(message.getReasoningContent())) {
                    builder.append("reasoning=").append(message.getReasoningContent()).append("\n");
                }
                builder.append("tool_calls=").append(String.valueOf(message.getToolCalls()));
            } else if (message.isToolResult()) {
                builder.append("tool_call_id=").append(safe(message.getToolCallId()));
                if (!isBlank(message.getToolName())) {
                    builder.append(", tool_name=").append(message.getToolName());
                }
                if (!isBlank(message.getContent())) {
                    builder.append("\n").append(message.getContent());
                }
            } else if (!isBlank(message.getContent()) || !isBlank(message.getReasoningContent())) {
                if (!isBlank(message.getContent())) {
                    builder.append(message.getContent());
                }
                if (!isBlank(message.getReasoningContent())) {
                    if (!isBlank(message.getContent())) {
                        builder.append("\n");
                    }
                    builder.append("[reasoning]\n").append(message.getReasoningContent());
                }
            }
        }
        return builder.toString();
    }

    private String extractCurrentGoal(String prompt) {
        for (String line : prompt.split("\\R")) {
            if (line.startsWith("用户目标: ")) {
                return line.substring("用户目标: ".length()).trim();
            }
        }
        return "";
    }

    private String extractWorkingDirectory(String prompt) {
        for (String line : prompt.split("\\R")) {
            if (line.contains("工作目录: ")) {
                return line.substring(line.indexOf("工作目录: ") + "工作目录: ".length()).trim();
            }
        }
        return "";
    }

    private String extractFirstContextFile(String prompt) {
        return firstMatchingUserContextFile(prompt, false);
    }

    private String extractApiHintFile(String prompt) {
        return firstMatchingUserContextFile(prompt, true);
    }

    private String firstMatchingUserContextFile(String prompt, boolean apiOnly) {
        boolean inUserFiles = false;
        for (String line : prompt.split("\\R")) {
            String trimmed = line == null ? "" : line.trim();
            if (line.contains("上下文文件: ")) {
                String path = line.substring(line.indexOf("上下文文件: ") + "上下文文件: ".length()).trim();
                if (matchesContextFile(path, apiOnly)) {
                    return path;
                }
                continue;
            }
            if ("补充文件:".equals(trimmed)) {
                inUserFiles = true;
                continue;
            }
            if (inUserFiles) {
                if (trimmed.startsWith("- ")) {
                    String path = trimmed.substring(2).trim();
                    if (matchesContextFile(path, apiOnly)) {
                        return path;
                    }
                    continue;
                }
                if (!trimmed.isEmpty()) {
                    inUserFiles = false;
                }
            }
        }
        return "";
    }

    private boolean matchesContextFile(String path, boolean apiOnly) {
        if (isBlank(path)) {
            return false;
        }
        if (!apiOnly) {
            return true;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml");
    }

    private java.util.List<String> extractSectionItems(String prompt, String title) {
        java.util.List<String> items = new java.util.ArrayList<String>();
        String[] lines = prompt.split("\\R");
        boolean inSection = false;
        StringBuilder currentItem = null;
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (isSectionTitle(trimmed)) {
                if (inSection && currentItem != null) {
                    items.add(currentItem.toString().trim());
                    currentItem = null;
                }
                inSection = (title + ":").equals(trimmed);
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (line.startsWith("- ")) {
                if (currentItem != null) {
                    items.add(currentItem.toString().trim());
                }
                currentItem = new StringBuilder(line.substring(2).trim());
                continue;
            }
            if (currentItem != null) {
                currentItem.append("\n").append(line);
            }
        }
        if (inSection && currentItem != null) {
            items.add(currentItem.toString().trim());
        }
        return items;
    }

    private boolean isSectionTitle(String line) {
        if (isBlank(line) || line.startsWith("- ")) {
            return false;
        }
        return line.endsWith(":");
    }

    private boolean containsPrefixedItem(java.util.List<String> items, String prefix) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (String item : items) {
            if (item != null && item.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String findFirstPrefixedItem(java.util.List<String> items, String prefix) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        for (String item : items) {
            if (item != null && item.startsWith(prefix)) {
                return item;
            }
        }
        return "";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String extractJsonField(String content, String fieldName) {
        Pattern pattern = Pattern.compile(String.format(JSON_STRING_PATTERN_TEMPLATE.pattern(), fieldName));
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String buildGeneratedContent(String apiPath, String apiMethod) {
        return "// codey API binding patch\n"
                + "const apiMeta = { path: '" + apiPath + "', method: '" + apiMethod + "' };\n"
                + "const queryParams = { name: '', pageNum: 1, pageSize: 10 };\n"
                + "const pageState = { loading: false, total: 0, records: [] };";
    }

    private boolean shouldReplyDirectly(String currentGoal, String targetFile) {
        return targetFile.isEmpty()
                || isGreeting(currentGoal)
                || asksAboutCapability(currentGoal);
    }

    private String buildDirectReply(String currentGoal, String workingDirectory) {
        if (isGreeting(currentGoal)) {
            return finishJson("我在。直接告诉我任务即可；如果你已经知道工作目录或相关文件，也可以一起发过来。");
        }
        if (asksAboutCapability(currentGoal)) {
            return finishJson("我可以先帮你理解需求，再按需要查看代码、搜索位置、梳理接口信息，最后修改工作目录里的文件。你直接说目标即可。");
        }
        return buildGoalAwareSummary(currentGoal, workingDirectory);
    }

    private String buildReadSummary(String currentGoal, String workingDirectory, String targetFile) {
        if (asksAboutApi(currentGoal)) {
            return finishJson("我已经看到了相关代码文件。你可以继续给我接口关键词、接口定义文件，或者直接告诉我需要联动的接口。");
        }
        StringBuilder summary = new StringBuilder();
        summary.append("我已经看过");
        if (!isBlank(targetFile)) {
            summary.append(targetFile);
        } else {
            summary.append("相关代码文件");
        }
        if (!isBlank(workingDirectory)) {
            summary.append("，当前工作目录是 ").append(workingDirectory);
        }
        summary.append("。你可以继续告诉我想分析什么，或者直接说下一步要改哪里。");
        return finishJson(summary.toString());
    }

    private String buildWorkspaceSummary(String workingDirectory, String workspaceListResult) {
        String normalized = workspaceListResult.substring("Workspace list result:".length()).trim();
        java.util.List<String> entries = new java.util.ArrayList<String>();
        String listedPath = extractWorkspaceRoot(normalized, entries);
        if (".".equals(listedPath)) {
            listedPath = extractWorkspaceRootFromLegacyText(normalized, entries);
        }
        StringBuilder summary = new StringBuilder();
        if (!isBlank(workingDirectory)) {
            summary.append("当前工作目录是 ").append(workingDirectory).append("。");
        }
        summary.append("我先看了目录 ").append(isBlank(listedPath) ? "." : listedPath).append("，");
        if (entries.isEmpty()) {
            summary.append("里面暂时没有可列出的文件。");
            return finishJson(summary.toString());
        }
        summary.append("目前看到这些内容：");
        int limit = Math.min(6, entries.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                summary.append("、");
            }
            summary.append(entries.get(index));
        }
        if (entries.size() > limit) {
            summary.append(" 等");
        }
        summary.append("。如果你要，我可以继续深入某个目录或文件。");
        return finishJson(summary.toString());
    }

    private String extractWorkspaceRoot(String payload, java.util.List<String> entries) {
        if (isBlank(payload) || !payload.startsWith("{")) {
            return ".";
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode listedRoot = root.path("root");
            JsonNode tree = root.path("entries");
            if (!tree.isArray()) {
                tree = root.path("tree");
            }
            if (tree.isArray()) {
                for (JsonNode item : tree) {
                    String path = item.path("path").asText("");
                    if (!isBlank(path)) {
                        entries.add(path);
                    }
                }
            }
            return listedRoot.asText(".");
        } catch (Exception ignored) {
            return ".";
        }
    }

    private String extractWorkspaceRootFromLegacyText(String payload, java.util.List<String> entries) {
        String[] lines = payload.split("\\R");
        String listedPath = ".";
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.startsWith("path=")) {
                listedPath = trimmed.substring("path=".length()).trim();
                continue;
            }
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return listedPath;
    }

    private String buildEditSummary(String currentGoal, String workingDirectory) {
        StringBuilder summary = new StringBuilder();
        summary.append("我已经根据当前上下文完成了一版代码更新");
        if (!isBlank(workingDirectory)) {
            summary.append("，工作目录是 ").append(workingDirectory);
        }
        if (!isBlank(currentGoal)) {
            summary.append("。这轮主要是在处理“").append(currentGoal).append("”");
        }
        summary.append("。你可以继续告诉我下一步想调整什么。");
        return finishJson(summary.toString());
    }

    private String buildGoalAwareSummary(String currentGoal, String workingDirectory) {
        if (isBlank(currentGoal)) {
            return finishJson("你好，我可以帮你分析代码、查看目录里的文件上下文，或者一起定位问题。你直接告诉我想做什么就行。");
        }
        StringBuilder summary = new StringBuilder();
        summary.append("我先收到了你的需求：“").append(currentGoal).append("”");
        if (!isBlank(workingDirectory)) {
            summary.append("。当前工作目录是 ").append(workingDirectory);
        }
        summary.append("。如果你愿意，我可以继续根据这个目标往下分析。");
        return finishJson(summary.toString());
    }

    private boolean asksAboutWorkspace(String goal) {
        String normalized = safeLower(goal);
        return normalized.contains("工作目录")
                || normalized.contains("目录在哪")
                || normalized.contains("哪些文件")
                || normalized.contains("有什么文件")
                || normalized.contains("文件列表");
    }

    private boolean asksAboutCapability(String goal) {
        String normalized = safeLower(goal);
        return normalized.contains("你能做什么")
                || normalized.contains("可以做什么")
                || normalized.contains("怎么帮我")
                || normalized.contains("help");
    }

    private boolean asksAboutApi(String goal) {
        String normalized = safeLower(goal);
        return normalized.contains("接口")
                || normalized.contains("api");
    }

    private boolean isGreeting(String goal) {
        String normalized = safeLower(goal);
        return "你好".equals(normalized)
                || "你好啊".equals(normalized)
                || "hello".equals(normalized)
                || "hi".equals(normalized)
                || "你好？".equals(normalized)
                || "在吗".equals(normalized);
    }

    private String finishJson(String summary) {
        return "{\"status\":\"FINISH\",\"summary\":\"" + escapeJson(summary) + "\"}";
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ModelResponse toolCall(String name, java.util.Map<String, Object> arguments) {
        ModelToolCall toolCall = new ModelToolCall();
        toolCall.setId("stub-tool-call");
        toolCall.setName(name);
        toolCall.setArguments(arguments);

        ModelResponse response = new ModelResponse();
        java.util.List<ModelToolCall> toolCalls = new java.util.ArrayList<ModelToolCall>();
        toolCalls.add(toolCall);
        response.setToolCalls(toolCalls);
        response.setFinishReason("tool_calls");
        response.setRawResponse(name);
        return response;
    }

    private ModelResponse finish(String content) {
        ModelResponse response = new ModelResponse();
        response.setContent(content);
        response.setFinishReason("stop");
        response.setRawResponse(content);
        return response;
    }

    private java.util.Map<String, Object> mapOf(String key, Object value) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    private java.util.Map<String, Object> mapOf(String key1, Object value1,
                                                String key2, Object value2) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private java.util.Map<String, Object> mapOf(String key1, Object value1,
                                                String key2, Object value2,
                                                String key3, Object value3,
                                                String key4, Object value4,
                                                String key5, Object value5) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<String, Object>();
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        map.put(key4, value4);
        map.put(key5, value5);
        return map;
    }
}
