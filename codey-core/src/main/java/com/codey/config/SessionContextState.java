package com.codey.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 承载任务目标、工作目录和用户补充上下文等稳定会话信息。
 */
final class SessionContextState {
    private static final int MAX_CONTEXT_ITEMS = 8;

    private String skillName;
    private String workingDirectory;
    private String targetPagePath;
    private String apiSpecPath;
    private String userGoal;
    private String lastEditedFilePath;
    private final List<String> contextFiles = new ArrayList<String>();
    private final List<String> contextNotes = new ArrayList<String>();
    private final List<String> userContextFiles = new ArrayList<String>();
    private final List<String> userContextNotes = new ArrayList<String>();
    private final List<String> identities = new ArrayList<String>();
    private String tenantId;
    private String runtimeContextSummary;
    private String coreRules;

    String getSkillName() {
        return skillName;
    }

    void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    String getWorkingDirectory() {
        return workingDirectory;
    }

    void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    String getTargetPagePath() {
        return targetPagePath;
    }

    void setTargetPagePath(String targetPagePath) {
        this.targetPagePath = targetPagePath;
    }

    String getApiSpecPath() {
        return apiSpecPath;
    }

    void setApiSpecPath(String apiSpecPath) {
        this.apiSpecPath = apiSpecPath;
    }

    String getUserGoal() {
        return userGoal;
    }

    void setUserGoal(String userGoal) {
        this.userGoal = userGoal;
    }

    String getLastEditedFilePath() {
        return lastEditedFilePath;
    }

    void setLastEditedFilePath(String lastEditedFilePath) {
        this.lastEditedFilePath = lastEditedFilePath;
    }

    List<String> getContextFiles() {
        return Collections.unmodifiableList(contextFiles);
    }

    List<String> getContextNotes() {
        return Collections.unmodifiableList(contextNotes);
    }

    List<String> getUserContextFiles() {
        return Collections.unmodifiableList(userContextFiles);
    }

    List<String> getUserContextNotes() {
        return Collections.unmodifiableList(userContextNotes);
    }

    List<String> getIdentities() {
        return Collections.unmodifiableList(identities);
    }

    void replaceIdentities(List<String> newIdentities) {
        identities.clear();
        appendIdentities(newIdentities);
    }

    void appendIdentities(List<String> newIdentities) {
        if (newIdentities == null) {
            return;
        }
        for (String identity : newIdentities) {
            appendIdentity(identity);
        }
    }

    String getTenantId() {
        return tenantId;
    }

    void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    String getRuntimeContextSummary() {
        return runtimeContextSummary;
    }

    void setRuntimeContextSummary(String runtimeContextSummary) {
        this.runtimeContextSummary = normalizeText(runtimeContextSummary);
    }

    String getCoreRules() {
        return coreRules;
    }

    void setCoreRules(String coreRules) {
        this.coreRules = normalizeText(coreRules);
    }

    void appendContextFile(String path) {
        if (path == null) {
            return;
        }
        String normalized = path.trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (contextFiles.contains(normalized)) {
            contextFiles.remove(normalized);
        }
        contextFiles.add(normalized);
        trimToSize(contextFiles);
    }

    void appendUserContextFile(String path) {
        String normalized = normalizeText(path);
        if (normalized.isEmpty()) {
            return;
        }
        if (userContextFiles.contains(normalized)) {
            userContextFiles.remove(normalized);
        }
        userContextFiles.add(normalized);
        trimToSize(userContextFiles);
        appendContextFile(normalized);
    }

    void appendContextNote(String note) {
        String normalized = normalizeText(note);
        if (normalized.isEmpty()) {
            return;
        }
        if (contextNotes.contains(normalized)) {
            contextNotes.remove(normalized);
        }
        contextNotes.add(normalized);
        trimToSize(contextNotes);
    }

    void appendUserContextNote(String note) {
        String normalized = normalizeText(note);
        if (normalized.isEmpty()) {
            return;
        }
        userContextNotes.add(normalized);
        trimToSize(userContextNotes);
        appendContextNote(normalized);
    }

    private void appendIdentity(String identity) {
        String normalized = normalizeText(identity);
        if (normalized.isEmpty()) {
            return;
        }
        if (identities.contains(normalized)) {
            return;
        }
        identities.add(normalized);
        trimToSize(identities);
    }

    private void trimToSize(List<String> items) {
        while (items.size() > MAX_CONTEXT_ITEMS) {
            items.remove(0);
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
