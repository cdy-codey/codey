package com.codey.starter;

import com.codey.config.ModelProperties;
import com.codey.workspace.WorkspaceDirectoryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 基于 Spring 的接入配置项。
 */
@ConfigurationProperties(prefix = "codey")
public class SpringProperties extends WorkspaceDirectoryProperties {
    private String skillsDirectory;
    private String defaultSkillName;
    private Integer eventBufferSize = Integer.valueOf(200);

    @NestedConfigurationProperty
    private ModelProperties model = new ModelProperties();

    public String getSkillsDirectory() {
        return skillsDirectory;
    }

    public void setSkillsDirectory(String skillsDirectory) {
        this.skillsDirectory = skillsDirectory;
    }

    public String getDefaultSkillName() {
        return defaultSkillName;
    }

    public void setDefaultSkillName(String defaultSkillName) {
        this.defaultSkillName = defaultSkillName;
    }

    public Integer getEventBufferSize() {
        return eventBufferSize;
    }

    public void setEventBufferSize(Integer eventBufferSize) {
        this.eventBufferSize = eventBufferSize;
    }

    public ModelProperties getModel() {
        return model;
    }

    public void setModel(ModelProperties model) {
        this.model = model == null ? new ModelProperties() : model;
    }
}
