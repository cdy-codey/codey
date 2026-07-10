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
    private SessionLogProperties sessionLog = new SessionLogProperties();

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

    public SessionLogProperties getSessionLog() {
        return sessionLog;
    }

    public void setSessionLog(SessionLogProperties sessionLog) {
        this.sessionLog = sessionLog == null ? new SessionLogProperties() : sessionLog;
    }

    public ModelProperties getModel() {
        return model;
    }

    public void setModel(ModelProperties model) {
        this.model = model == null ? new ModelProperties() : model;
    }

    /**
     * 控制 core 会话日志是否写入 session-directory。
     */
    public static class SessionLogProperties {
        private boolean enabled = true;
        private Integer maxHistoryDays = Integer.valueOf(30);
        private Integer cleanupIntervalMinutes = Integer.valueOf(24 * 60);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getMaxHistoryDays() {
            return maxHistoryDays;
        }

        public void setMaxHistoryDays(Integer maxHistoryDays) {
            this.maxHistoryDays = maxHistoryDays;
        }

        public Integer getCleanupIntervalMinutes() {
            return cleanupIntervalMinutes;
        }

        public void setCleanupIntervalMinutes(Integer cleanupIntervalMinutes) {
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
        }
    }
}
