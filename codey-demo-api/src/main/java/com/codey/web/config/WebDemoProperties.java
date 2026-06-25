package com.codey.web.config;

import com.codey.workspace.WorkspaceDirectoryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web Demo 业务配置。
 * 当前主要作为 Spring Boot 绑定入口，工作区目录规则已下沉到底层通用配置对象。
 */
@ConfigurationProperties(prefix = "codey")
public class WebDemoProperties extends WorkspaceDirectoryProperties {
    public WebDemoProperties() {
        // Demo 场景保留明确默认值，避免本地独立启动时还依赖额外配置。
        setSessionDirectory("./codey/sessions");
        setWorkingDirectory("./workspace/project");
    }
}
