package com.codey.skill;

import com.codey.meta.IdentityMatchMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能的静态定义。
 * 第一版先在代码里维护，后续可切到 YAML 加载。
 */
public class SkillDefinition {
    private String name;
    private String description;
    private String systemPromptTemplate;
    private List<String> supportedIdentities = new ArrayList<String>();
    private IdentityMatchMode identityMatchMode = IdentityMatchMode.ANY;
    private List<String> allowedToolGroups = new ArrayList<String>();
    private List<String> allowedToolBundles = new ArrayList<String>();
    private String outputContract;
    private int maxLoopCount;
    /** 写工具执行成功且通过校验后，直接结束循环，跳过模型再输出一次 FINISH 的往返。 */
    private boolean autoCompleteOnVerifiedWrite;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSystemPromptTemplate() {
        return systemPromptTemplate;
    }

    public void setSystemPromptTemplate(String systemPromptTemplate) {
        this.systemPromptTemplate = systemPromptTemplate;
    }

    public List<String> getSupportedIdentities() {
        return supportedIdentities;
    }

    public void setSupportedIdentities(List<String> supportedIdentities) {
        this.supportedIdentities = copyList(supportedIdentities);
    }

    public IdentityMatchMode getIdentityMatchMode() {
        return identityMatchMode;
    }

    public void setIdentityMatchMode(IdentityMatchMode identityMatchMode) {
        this.identityMatchMode = identityMatchMode == null ? IdentityMatchMode.ANY : identityMatchMode;
    }

    public List<String> getAllowedToolGroups() {
        return allowedToolGroups;
    }

    public void setAllowedToolGroups(List<String> allowedToolGroups) {
        this.allowedToolGroups = copyList(allowedToolGroups);
    }

    public List<String> getAllowedToolBundles() {
        return allowedToolBundles;
    }

    public void setAllowedToolBundles(List<String> allowedToolBundles) {
        this.allowedToolBundles = copyList(allowedToolBundles);
    }

    public String getOutputContract() {
        return outputContract;
    }

    public void setOutputContract(String outputContract) {
        this.outputContract = outputContract;
    }

    public int getMaxLoopCount() {
        return maxLoopCount;
    }

    public void setMaxLoopCount(int maxLoopCount) {
        this.maxLoopCount = maxLoopCount;
    }

    public boolean isAutoCompleteOnVerifiedWrite() {
        return autoCompleteOnVerifiedWrite;
    }

    public void setAutoCompleteOnVerifiedWrite(boolean autoCompleteOnVerifiedWrite) {
        this.autoCompleteOnVerifiedWrite = autoCompleteOnVerifiedWrite;
    }

    /**
     * 判断当前定义是否包含目标技能。复合技能的名称会用逗号连接多个技能名，
     * 因此这里按逗号拆分后逐项匹配，避免复合技能无法被识别为其中某个子技能。
     */
    public boolean hasSkill(String targetName) {
        if (name == null || targetName == null) {
            return false;
        }
        for (String token : name.split(",")) {
            if (targetName.equals(token.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }
}
