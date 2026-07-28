package com.codey.task;

import com.codey.config.ModelProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次代码代理任务的最小输入。
 * 目标是主输入，页面和接口说明只作为可选辅助上下文。
 */
public class GenerateTask {
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
    private String tenantId;
    private ModelProperties modelConfig;
    private String coreRules;
    /** 单表模式：工作目录仅包含单个或少量文件，默认开启 */
    private boolean singleFileMode = true;

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
        return skillNames == null ? new ArrayList<String>() : new ArrayList<String>(skillNames);
    }

    public void setSkillNames(List<String> skillNames) {
        this.skillNames = skillNames == null ? new ArrayList<String>() : new ArrayList<String>(skillNames);
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
        this.contextFiles = contextFiles == null ? new ArrayList<String>() : new ArrayList<String>(contextFiles);
    }

    public List<String> getContextNotes() {
        return contextNotes;
    }

    public void setContextNotes(List<String> contextNotes) {
        this.contextNotes = contextNotes == null ? new ArrayList<String>() : new ArrayList<String>(contextNotes);
    }

    public List<String> getChatHistory() {
        return chatHistory;
    }

    public void setChatHistory(List<String> chatHistory) {
        this.chatHistory = chatHistory == null ? new ArrayList<String>() : new ArrayList<String>(chatHistory);
    }

    public List<String> getIdentities() {
        return identities;
    }

    public void setIdentities(List<String> identities) {
        this.identities = identities == null ? new ArrayList<String>() : new ArrayList<String>(identities);
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public ModelProperties getModelConfig() {
        return copyModelConfig(modelConfig);
    }

    public void setModelConfig(ModelProperties modelConfig) {
        this.modelConfig = copyModelConfig(modelConfig);
    }

    public String getCoreRules() {
        return coreRules;
    }

    public void setCoreRules(String coreRules) {
        this.coreRules = coreRules;
    }

    public boolean isSingleFileMode() {
        return singleFileMode;
    }

    public void setSingleFileMode(boolean singleFileMode) {
        this.singleFileMode = singleFileMode;
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
