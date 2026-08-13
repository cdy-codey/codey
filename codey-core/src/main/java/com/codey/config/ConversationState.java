package com.codey.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 承载多轮对话历史、交互记录和待选择项状态。
 */
final class ConversationState {
    private final int maxChatHistory;
    private final List<String> chatHistory = new ArrayList<String>();
    private final List<String> interactionHistory = new ArrayList<String>();
    private final Map<String, String> pendingChoiceOptions = new HashMap<String, String>();
    private String pendingChoicePrompt;
    private boolean formFillAuthorized = false;

    ConversationState(int maxChatHistory) {
        this.maxChatHistory = maxChatHistory;
    }

    List<String> getChatHistory() {
        return Collections.unmodifiableList(chatHistory);
    }

    List<String> getChatHistoryBeforeRecentTail(int keepRecentCount) {
        if (keepRecentCount < 0) {
            keepRecentCount = 0;
        }
        int cutoff = Math.max(0, chatHistory.size() - keepRecentCount);
        return new ArrayList<String>(chatHistory.subList(0, cutoff));
    }

    void discardChatHistoryBeforeRecentTail(int keepRecentCount) {
        if (keepRecentCount < 0) {
            keepRecentCount = 0;
        }
        int removable = Math.max(0, chatHistory.size() - keepRecentCount);
        for (int index = 0; index < removable; index++) {
            chatHistory.remove(0);
        }
    }

    List<String> getInteractionHistory() {
        return Collections.unmodifiableList(interactionHistory);
    }

    void appendChatHistory(String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        chatHistory.add(content);
        while (chatHistory.size() > maxChatHistory) {
            chatHistory.remove(0);
        }
    }

    void recordChatTurn(String userMessage, String assistantMessage) {
        String normalizedUser = safe(userMessage);
        if (!normalizedUser.isEmpty()) {
            appendChatHistory("用户: " + normalizedUser);
        }
        String normalizedAssistant = safe(assistantMessage);
        if (!normalizedAssistant.isEmpty()) {
            appendChatHistory("助手: " + normalizedAssistant);
            capturePendingChoiceFromAssistant(normalizedAssistant);
        }
    }

    boolean hasPendingChoice() {
        return !pendingChoiceOptions.isEmpty();
    }

    /**
     * 判断用户输入是否命中待选项：优先按 key（A/1），其次按 label 文本。
     * 前端点击选项按钮时发送的是 label 而非 key，缺少 label 匹配会导致选择无法被识别。
     */
    boolean matchesPendingChoice(String input) {
        if (input == null) {
            return false;
        }
        String normalized = input.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        String upper = normalized.toUpperCase();
        if (pendingChoiceOptions.containsKey(upper)) {
            return true;
        }
        if (pendingChoiceOptions.containsKey(normalizeNumericChoice(upper))) {
            return true;
        }
        return findOptionByLabel(normalized) != null;
    }

    String resolvePendingChoice(String rawUserInput) {
        if (rawUserInput == null) {
            return "";
        }
        String input = rawUserInput.trim();
        if (input.isEmpty()) {
            return "";
        }
        String upperInput = input.toUpperCase();
        String matchedKey = upperInput;
        String option = pendingChoiceOptions.get(upperInput);
        if (option == null) {
            String numericKey = normalizeNumericChoice(upperInput);
            option = pendingChoiceOptions.get(numericKey);
            matchedKey = numericKey;
        }
        if (option == null) {
            // 前端点击选项按钮时发送的是 label 文本而非 key，按 label 精确匹配一次。
            option = findOptionByLabel(input);
            matchedKey = input;
        }
        if (option == null) {
            return "";
        }
        if (isFormFillChoice(option)) {
            formFillAuthorized = true;
        }
        String prompt = pendingChoicePrompt;
        clearPendingChoice();
        if (prompt == null || prompt.trim().isEmpty()) {
            return option;
        }
        return prompt.trim()
                + "\n用户选择: " + matchedKey
                + "\n请按这个选项继续执行: " + option;
    }

    boolean isFormFillAuthorized() {
        return formFillAuthorized;
    }

    private boolean isFormFillChoice(String option) {
        if (option == null) {
            return false;
        }
        String lower = option.toLowerCase();
        return lower.contains("填入") || lower.contains("回填") || lower.contains("填充");
    }

    private String findOptionByLabel(String input) {
        if (input == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : pendingChoiceOptions.entrySet()) {
            String value = entry.getValue();
            if (value != null && (value.equals(input) || value.equalsIgnoreCase(input))) {
                return value;
            }
        }
        return null;
    }

    void clearPendingChoice() {
        pendingChoiceOptions.clear();
        pendingChoicePrompt = null;
    }

    void appendInteraction(String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        interactionHistory.add(content);
    }

    private void capturePendingChoiceFromAssistant(String assistantMessage) {
        if (assistantMessage == null) {
            return;
        }
        String text = assistantMessage.replace("\r", "");
        Map<String, String> parsed = parseChoiceOptions(text);
        if (parsed.isEmpty()) {
            return;
        }
        pendingChoiceOptions.clear();
        pendingChoiceOptions.putAll(parsed);
        pendingChoicePrompt = extractChoicePrompt(text);
    }

    private Map<String, String> parseChoiceOptions(String text) {
        Map<String, String> options = new HashMap<String, String>();
        if (text == null) {
            return options;
        }
        String normalized = text.replace("\r", "");
        Pattern letterPattern = Pattern.compile("(?m)^\\s*([A-Da-d])\\s*[\\.|\\)|、:：-]\\s*(.+?)\\s*$");
        Matcher letterMatcher = letterPattern.matcher(normalized);
        while (letterMatcher.find()) {
            String key = letterMatcher.group(1);
            String value = letterMatcher.group(2);
            if (key != null && value != null) {
                options.put(key.trim().toUpperCase(), value.trim());
            }
        }
        Pattern numberPattern = Pattern.compile("(?m)^\\s*(\\d{1,2})\\s*[\\.|\\)|、:：-]\\s*(.+?)\\s*$");
        Matcher numberMatcher = numberPattern.matcher(normalized);
        while (numberMatcher.find()) {
            String key = numberMatcher.group(1);
            String value = numberMatcher.group(2);
            if (key != null && value != null) {
                options.put(key.trim(), value.trim());
            }
        }
        // JSON user_choice 视图的 options 数组（{"key":"A","label":"..."}）不会被上面的文本正则命中。
        // 缺少这一支会导致待选项丢失，用户后续的选择无法被解析，模型只能重新提取并重复询问。
        Pattern jsonKeyLabelPattern = Pattern.compile("\"key\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"label\"\\s*:\\s*\"([^\"]+)\"");
        Matcher jsonKeyLabelMatcher = jsonKeyLabelPattern.matcher(normalized);
        while (jsonKeyLabelMatcher.find()) {
            String key = jsonKeyLabelMatcher.group(1);
            String value = jsonKeyLabelMatcher.group(2);
            if (key != null && value != null && !value.trim().isEmpty()) {
                options.put(key.trim().toUpperCase(), value.trim());
            }
        }
        if (options.isEmpty()) {
            return options;
        }
        if (options.containsKey("A") && !options.containsKey("1")) {
            options.put("1", options.get("A"));
        }
        if (options.containsKey("B") && !options.containsKey("2")) {
            options.put("2", options.get("B"));
        }
        if (options.containsKey("C") && !options.containsKey("3")) {
            options.put("3", options.get("C"));
        }
        return options;
    }

    private String extractChoicePrompt(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r", "").trim();
        // JSON user_choice 视图：优先取 description，其次 title，作为询问文案，
        // 避免把整段 JSON（含 status:FINISH）当成目标回灌给模型。
        String jsonDescription = extractJsonStringField(normalized, "description");
        if (!jsonDescription.isEmpty()) {
            return jsonDescription;
        }
        String jsonTitle = extractJsonStringField(normalized, "title");
        if (!jsonTitle.isEmpty()) {
            return jsonTitle;
        }
        int index = normalized.lastIndexOf('\n');
        if (index < 0) {
            return normalized;
        }
        String tail = normalized.substring(index + 1).trim();
        return tail.isEmpty() ? normalized : tail;
    }

    private String extractJsonStringField(String text, String fieldName) {
        if (text == null || fieldName == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String normalizeNumericChoice(String key) {
        if (key == null) {
            return "";
        }
        String normalized = key.trim();
        if (normalized.matches("^\\d+$")) {
            return normalized;
        }
        if ("A".equalsIgnoreCase(normalized)) {
            return "1";
        }
        if ("B".equalsIgnoreCase(normalized)) {
            return "2";
        }
        if ("C".equalsIgnoreCase(normalized)) {
            return "3";
        }
        if ("D".equalsIgnoreCase(normalized)) {
            return "4";
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
