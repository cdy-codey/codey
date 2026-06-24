package com.codey.skill;

import com.codey.meta.IdentityMatchMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技能配置文件对应的映射对象。
 */
public class SkillFileDefinition {
    private String name;
    private String version;
    private String description;
    private List<String> supportedIdentities = new ArrayList<String>();
    private IdentityMatchMode identityMatchMode = IdentityMatchMode.ANY;
    private List<String> allowedToolGroups = new ArrayList<String>();
    private List<String> allowedToolBundles = new ArrayList<String>();
    private Integer maxLoopCount;
    private String systemPromptTemplate;
    private Map<String, Object> outputContract;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getSupportedIdentities() {
        return supportedIdentities;
    }

    public void setSupportedIdentities(List<String> supportedIdentities) {
        this.supportedIdentities = supportedIdentities;
    }

    public IdentityMatchMode getIdentityMatchMode() {
        return identityMatchMode;
    }

    public void setIdentityMatchMode(IdentityMatchMode identityMatchMode) {
        this.identityMatchMode = identityMatchMode;
    }

    public List<String> getAllowedToolGroups() {
        return allowedToolGroups;
    }

    public void setAllowedToolGroups(List<String> allowedToolGroups) {
        this.allowedToolGroups = allowedToolGroups;
    }

    public List<String> getAllowedToolBundles() {
        return allowedToolBundles;
    }

    public void setAllowedToolBundles(List<String> allowedToolBundles) {
        this.allowedToolBundles = allowedToolBundles;
    }

    public Integer getMaxLoopCount() {
        return maxLoopCount;
    }

    public void setMaxLoopCount(Integer maxLoopCount) {
        this.maxLoopCount = maxLoopCount;
    }

    public String getSystemPromptTemplate() {
        return systemPromptTemplate;
    }

    public void setSystemPromptTemplate(String systemPromptTemplate) {
        this.systemPromptTemplate = systemPromptTemplate;
    }

    public Map<String, Object> getOutputContract() {
        return outputContract;
    }

    public void setOutputContract(Map<String, Object> outputContract) {
        this.outputContract = outputContract;
    }
}
