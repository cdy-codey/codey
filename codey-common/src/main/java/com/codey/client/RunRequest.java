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
    private String tenantId;
    private ModelProperties modelConfig;
    private boolean includeThinking = false;
    private String coreRules;
    /** 单表模式：工作目录仅包含单个或少量文件，默认开启 */
    private boolean singleFileMode = true;
    /** 表单模式：字段信息已完整提供，AI 直接输出 JSON 结果，不进行多轮文件读写 */
    private boolean formMode = false;
    /** 表单名称：表单模式下用于匹配业务实现的表单定义（FormProvider） */
    private String formName;
    /** 表单可见字段名称列表：非空时仅序列化列表内的字段，用于过滤表单中未展示字段的噪音 */
    private List<String> formVisibleFields = new ArrayList<String>();

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

    public boolean isIncludeThinking() {
        return includeThinking;
    }

    public void setIncludeThinking(boolean includeThinking) {
        this.includeThinking = includeThinking;
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

    public boolean isFormMode() {
        return formMode;
    }

    public void setFormMode(boolean formMode) {
        this.formMode = formMode;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public List<String> getFormVisibleFields() {
        return copyList(formVisibleFields);
    }

    public void setFormVisibleFields(List<String> formVisibleFields) {
        this.formVisibleFields = copyList(formVisibleFields);
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
