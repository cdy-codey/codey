package com.codey.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web Demo 业务配置。
 * 统一承接目录配置，避免分散读取导致键名不一致。
 */
@ConfigurationProperties(prefix = "codey")
public class WebDemoProperties {
    /**
     * Web Demo 进程当前工作目录。
     * 对外返回路径时统一相对这个目录裁剪，避免暴露绝对目录。
     */
    private final Path applicationRoot = Paths.get("").toAbsolutePath().normalize();

    /**
     * 会话目录，当前保留在配置对象里统一管理。
     */
    private String sessionDirectory = "./codey/sessions";

    /**
     * 单工作目录根路径。
     */
    private String workingDirectory = "./workspace/project";

    public String getSessionDirectory() {
        return sessionDirectory;
    }

    public void setSessionDirectory(String sessionDirectory) {
        this.sessionDirectory = sessionDirectory;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Path resolveWorkingDirectoryRoot() {
        return Paths.get(workingDirectory).toAbsolutePath().normalize();
    }

    /**
     * 对外只暴露相对目录，内部文件操作仍继续使用绝对路径。
     */
    public String toVisiblePath(Path path) {
        if (path == null) {
            return "";
        }
        Path normalized = path.toAbsolutePath().normalize();
        try {
            Path relative = applicationRoot.relativize(normalized);
            String displayPath = normalizeSeparators(relative.toString());
            return displayPath.isEmpty() ? "." : displayPath;
        } catch (IllegalArgumentException exception) {
            Path fileName = normalized.getFileName();
            return fileName == null ? "." : normalizeSeparators(fileName.toString());
        }
    }

    private String normalizeSeparators(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }
}
