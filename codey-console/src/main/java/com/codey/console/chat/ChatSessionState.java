package com.codey.console.chat;

import com.codey.client.RunRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在聊天模式下维护当前工作目录和补充上下文。
 */
public class ChatSessionState {
    private static final int MAX_CONTEXT_NOTES = 6;
    private static final Pattern ABSOLUTE_PATH_PATTERN =
            Pattern.compile("([A-Za-z]:[\\\\/][^\\s\"'`，。；,;\\)\\]\\}]+\\.(?:vue|java|js|ts|tsx|jsx|json|ya?ml|xml|md))(?=$|[\\s，。；,;\\)\\]\\}])");
    private static final Pattern RELATIVE_PATH_PATTERN =
            Pattern.compile("((?:[\\w.-]+[\\\\/])+[\\w.-]+\\.(?:vue|java|js|ts|tsx|jsx|json|ya?ml|xml|md))(?=$|[\\s，。；,;\\)\\]\\}])");

    private final String skillName;
    private final Set<String> contextFiles = new LinkedHashSet<String>();
    private final List<String> contextNotes = new ArrayList<String>();
    private final Set<String> identities = new LinkedHashSet<String>();
    private String workingDirectory;

    public ChatSessionState(String skillName) {
        this(skillName, null);
    }

    public ChatSessionState(String skillName, List<String> identities) {
        this.skillName = skillName;
        addIdentities(identities);
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = blankToNull(workingDirectory);
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * 兼容旧入口：页面文件现在只作为辅助文件处理。
     */
    public void setPagePath(String pagePath) {
        addContextFile(pagePath);
    }

    /**
     * 兼容旧入口：API 文件现在只作为辅助文件处理。
     */
    public void setApiPath(String apiPath) {
        addContextFile(apiPath);
    }

    public void addContextFile(String path) {
        String normalized = blankToNull(path);
        if (normalized == null) {
            return;
        }
        contextFiles.add(normalized);
    }

    public void clearContext() {
        contextFiles.clear();
        contextNotes.clear();
    }

    public void absorbPathsFromMessage(String message) {
        if (isBlank(message)) {
            return;
        }
        List<String> paths = extractPaths(message);
        for (String path : paths) {
            classifyPath(path);
        }
    }

    public void addContextNote(String note) {
        appendContextNote("备注: " + safe(note));
    }

    public void addIdentity(String identity) {
        String normalized = blankToNull(identity);
        if (normalized == null) {
            return;
        }
        identities.add(normalized);
    }

    public RunRequest buildRequest(String goal) {
        RunRequest request = new RunRequest();
        request.setSkillName(skillName);
        request.setGoal(goal);
        request.setWorkingDirectory(workingDirectory);
        request.setContextFiles(new ArrayList<String>(contextFiles));
        request.setContextNotes(new ArrayList<String>(contextNotes));
        request.setIdentities(new ArrayList<String>(identities));
        return request;
    }

    public String describeContext() {
        StringBuilder builder = new StringBuilder();
        builder.append("当前会话上下文").append("\n");
        builder.append("- 工作目录: ").append(valueOrUnset(workingDirectory)).append("\n");
        builder.append("- 会话身份: ");
        if (identities.isEmpty()) {
            builder.append("(空)").append("\n");
        } else {
            builder.append(String.join(", ", new ArrayList<String>(identities))).append("\n");
        }
        builder.append("- 辅助文件: ");
        if (contextFiles.isEmpty()) {
            builder.append("(空)");
        } else {
            builder.append(String.join(", ", new ArrayList<String>(contextFiles)));
        }
        return builder.toString();
    }

    private void classifyPath(String path) {
        String lower = path.toLowerCase();
        if (isSourcePath(lower) || isApiPath(lower)) {
            addContextFile(path);
        }
    }

    private List<String> extractPaths(String message) {
        LinkedHashSet<String> uniquePaths = new LinkedHashSet<String>();
        collectMatches(uniquePaths, ABSOLUTE_PATH_PATTERN, message);
        collectMatches(uniquePaths, RELATIVE_PATH_PATTERN, message);
        return new ArrayList<String>(uniquePaths);
    }

    private void collectMatches(Set<String> results, Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String path = blankToNull(matcher.group(1));
            if (path != null) {
                results.add(trimTrailingPunctuation(path));
            }
        }
    }

    private String trimTrailingPunctuation(String path) {
        String trimmed = path;
        while (!trimmed.isEmpty()) {
            char tail = trimmed.charAt(trimmed.length() - 1);
            if (tail == ','
                    || tail == '，'
                    || tail == '。'
                    || tail == ';'
                    || tail == '；'
                    || tail == ')'
                    || tail == ']'
                    || tail == '}'
                    || tail == '"'
                    || tail == '\'') {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
                continue;
            }
            break;
        }
        return trimmed;
    }

    private boolean isApiPath(String lower) {
        return lower.endsWith(".json") && (lower.contains("api") || lower.contains("swagger") || lower.contains("openapi"));
    }

    private boolean isSourcePath(String lower) {
        return lower.endsWith(".vue")
                || lower.endsWith(".java")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx")
                || lower.endsWith(".jsx")
                || lower.endsWith(".xml");
    }

    private void appendContextNote(String note) {
        String normalized = blankToNull(note);
        if (normalized == null) {
            return;
        }
        contextNotes.add(normalized);
        while (contextNotes.size() > MAX_CONTEXT_NOTES) {
            contextNotes.remove(0);
        }
    }

    private void addIdentities(List<String> identities) {
        if (identities == null) {
            return;
        }
        for (String identity : identities) {
            addIdentity(identity);
        }
    }

    private String valueOrUnset(String value) {
        return isBlank(value) ? "(未设置)" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
