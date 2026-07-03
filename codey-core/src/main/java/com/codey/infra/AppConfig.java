package com.codey.infra;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 从本地 YAML 配置中读取的应用级配置。
 */
public class AppConfig {
    // console 历史上使用 defaultWorkingDirectory；同时兼容用户更直观的 working-directory 写法。
    @JsonAlias({"workingDirectory", "working-directory"})
    private String defaultWorkingDirectory;

    public String getDefaultWorkingDirectory() {
        return defaultWorkingDirectory;
    }

    public void setDefaultWorkingDirectory(String defaultWorkingDirectory) {
        this.defaultWorkingDirectory = defaultWorkingDirectory;
    }
}
