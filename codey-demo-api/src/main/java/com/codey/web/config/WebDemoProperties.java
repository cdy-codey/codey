package com.codey.web.config;

import com.codey.workspace.WorkspaceDirectoryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web Demo 业务配置。
 * 当前主要作为 Spring Boot 绑定入口，并为 demo 提供默认目录配置。
 */
@ConfigurationProperties(prefix = "codey")
public class WebDemoProperties extends WorkspaceDirectoryProperties {
}
