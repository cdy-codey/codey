package com.codey.infra;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 从本地 YAML 配置中读取的应用级配置。
 */
public class AppConfig {
    // console 历史上使用 defaultWorkingDirectory；同时兼容用户更直观的 working-directory 写法。
    @JsonAlias({"workingDirectory", "working-directory"})
    private String defaultWorkingDirectory;
    private LoggingConfig logging = new LoggingConfig();

    public String getDefaultWorkingDirectory() {
        return defaultWorkingDirectory;
    }

    public void setDefaultWorkingDirectory(String defaultWorkingDirectory) {
        this.defaultWorkingDirectory = defaultWorkingDirectory;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging == null ? new LoggingConfig() : logging;
    }

    /**
     * 控制台/核心模块共用的日志配置。
     */
    public static class LoggingConfig {
        // 默认不落盘，只有显式开启才会写文件。
        @JsonAlias({"fileEnabled", "file-enabled"})
        private boolean fileEnabled;

        @JsonAlias({"directory", "dir"})
        private String directory = "logs";

        @JsonAlias({"fileName", "file-name"})
        private String fileName = "codey.log";

        // 通过 %d 模式控制按天/小时等周期滚动，默认按天切分。
        @JsonAlias({
                "archiveFileNamePattern",
                "archive-file-name-pattern",
                "rollingPattern",
                "rolling-pattern"
        })
        private String archiveFileNamePattern = "codey.%d{yyyy-MM-dd}.log";

        @JsonAlias({"maxHistoryDays", "max-history-days", "maxHistory", "max-history"})
        private Integer maxHistoryDays = Integer.valueOf(30);

        // 控制台与文件统一使用带日期的日志格式，便于排查问题。
        @JsonAlias({"pattern", "layout-pattern"})
        private String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

        public boolean isFileEnabled() {
            return fileEnabled;
        }

        public void setFileEnabled(boolean fileEnabled) {
            this.fileEnabled = fileEnabled;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getArchiveFileNamePattern() {
            return archiveFileNamePattern;
        }

        public void setArchiveFileNamePattern(String archiveFileNamePattern) {
            this.archiveFileNamePattern = archiveFileNamePattern;
        }

        public Integer getMaxHistoryDays() {
            return maxHistoryDays;
        }

        public void setMaxHistoryDays(Integer maxHistoryDays) {
            this.maxHistoryDays = maxHistoryDays;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}
