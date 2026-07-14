package com.codey.client;

import com.codey.config.ModelProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一任务/会话调用入参。
 */
public class RunRequest {
    private String sessionId;
    private String skillName;
    private List<String> skillNames = new ArrayList<String>();
    private String goal;
    private String workingDirectory;
    private String pagePath;
    private String apiSpecPath;
    private List<String> contextFiles = new ArrayList<String>();
    private List<String> contextNotes = new ArrayList<String>();
    private List<String> chatHistory = new ArrayList<String>();
    private List<String> identities = new ArrayList<String>();
    private ModelProperties modelConfig;
    private boolean includeThinking = true;

    public static RunRequest ofGoal(String goal) {
        RunRequest request = new RunRequest();
        request.setGoal(goal);
        return request;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public List<String> getSkillNames() {
        return copyList(skillNames);
    }

    public void setSkillNames(List<String> skillNames) {
        this.skillNames = copyList(skillNames);
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getPagePath() {
        return pagePath;
    }

    public void setPagePath(String pagePath) {
        this.pagePath = pagePath;
    }

    public String getApiSpecPath() {
        return apiSpecPath;
    }

    public void setApiSpecPath(String apiSpecPath) {
        this.apiSpecPath = apiSpecPath;
    }

    public List<String> getContextFiles() {
        return contextFiles;
    }

    public void setContextFiles(List<String> contextFiles) {
        this.contextFiles = copyList(contextFiles);
    }

    public List<String> getContextNotes() {
        return contextNotes;
    }

    public void setContextNotes(List<String> contextNotes) {
        this.contextNotes = copyList(contextNotes);
    }

    public List<String> getChatHistory() {
        return chatHistory;
    }

    public void setChatHistory(List<String> chatHistory) {
        this.chatHistory = copyList(chatHistory);
    }

    public List<String> getIdentities() {
        return identities;
    }

    public void setIdentities(List<String> identities) {
        this.identities = copyList(identities);
    }

    public ModelProperties getModelConfig() {
        return copyModelConfig(modelConfig);
    }

    public void setModelConfig(ModelProperties modelConfig) {
        this.modelConfig = copyModelConfig(modelConfig);
    }

    public boolean isIncludeThinking() {
        return includeThinking;
    }

    public void setIncludeThinking(boolean includeThinking) {
        this.includeThinking = includeThinking;
    }

    private List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }

    private ModelProperties copyModelConfig(ModelProperties source) {
        if (source == null) {
            return null;
        }
        ModelProperties copy = new ModelProperties();
        copy.setProvider(source.getProvider());
        copy.setEndpoint(source.getEndpoint());
        copy.setModelName(source.getModelName());
        copy.setApiKey(source.getApiKey());
        copy.setApiKeyEnv(source.getApiKeyEnv());
        copy.setTemperature(source.getTemperature());
        copy.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
        copy.setReadTimeoutMillis(source.getReadTimeoutMillis());
        copy.setMaxRetries(source.getMaxRetries());
        return copy;
    }
}
