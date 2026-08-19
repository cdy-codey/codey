package com.codey.web.config;

import com.codey.workspace.WorkspaceDirectoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 工作区目录服务装配。
 * 统一暴露 WorkspaceDirectoryService Bean，供业务服务与校验工具注入。
 */
@Configuration
public class WorkspaceConfig {

    private static final Set<String> STANDARD_IGNORED_ENTRY_NAMES = new HashSet<String>(Arrays.asList(
            ".git", ".idea", "node_modules", "target", "dist"
    ));

    @Bean
    public WorkspaceDirectoryService workspaceDirectoryService(WebDemoProperties properties) {
        Set<String> ignoredNames = new HashSet<String>(STANDARD_IGNORED_ENTRY_NAMES);
        Path normalizedSessionDirectory = Paths.get(properties.getSessionDirectory()).normalize();
        Path fileName = normalizedSessionDirectory.getFileName();
        if (fileName != null) {
            ignoredNames.add(fileName.toString());
        }
        return new WorkspaceDirectoryService(
                properties.resolveWorkingDirectoryRoot(),
                ignoredNames,
                properties::toVisiblePath
        );
    }
}
