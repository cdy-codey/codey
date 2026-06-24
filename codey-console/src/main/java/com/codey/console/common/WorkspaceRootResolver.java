package com.codey.console.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 根据 console 参数、配置项和默认值解析工作区根目录。
 */
public class WorkspaceRootResolver {
    static final String FALLBACK_WORKSPACE = "workspace";

    public Path resolve(String cliWorkingDirectory, String configuredDefaultWorkingDirectory) {
        String candidate = firstNonBlank(cliWorkingDirectory, configuredDefaultWorkingDirectory, FALLBACK_WORKSPACE);
        Path workspaceRoot = Paths.get(candidate).toAbsolutePath().normalize();
        ensureExists(workspaceRoot);
        return workspaceRoot;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (!isBlank(first)) {
            return first.trim();
        }
        if (!isBlank(second)) {
            return second.trim();
        }
        return fallback;
    }

    /**
     * 确保解析出的工作区目录已经存在。
     */
    private void ensureExists(Path workspaceRoot) {
        try {
            Files.createDirectories(workspaceRoot);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare workspace root: " + workspaceRoot, exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
