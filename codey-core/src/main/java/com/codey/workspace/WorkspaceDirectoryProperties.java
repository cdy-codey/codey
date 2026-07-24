package com.codey.workspace;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 通用工作区目录配置。
 * 统一收敛 working-directory、session-directory 及可见路径裁剪规则。
 */
public class WorkspaceDirectoryProperties {
    /**
     * 当前进程工作目录。
     * 对外展示路径时统一相对这个目录裁剪，避免暴露过长的绝对路径。
     */
    private final Path applicationRoot = Paths.get("").toAbsolutePath().normalize();

    private String workingDirectory;
    private String sessionDirectory;

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getSessionDirectory() {
        return sessionDirectory;
    }

    public void setSessionDirectory(String sessionDirectory) {
        this.sessionDirectory = sessionDirectory;
    }

    /**
     * 工作区根目录，默认 ./workspace。
     * 上层可通过 codey.working-directory 显式覆盖。
     */
    public Path resolveWorkingDirectoryRoot() {
        String dir = isBlank(workingDirectory) ? "workspace" : workingDirectory;
        Path resolved = Paths.get(dir);
        return resolved.isAbsolute() ? resolved.normalize() : applicationRoot.resolve(resolved).normalize();
    }

    /**
     * session-directory 与 working-directory 是平级配置。
     * 日志根目录默认统一落到应用启动目录下的 codey/sessions，不再跟随 working-directory 推断。
     */
    public Path resolveSessionDirectoryRoot(Path workspaceRoot) {
        if (isBlank(sessionDirectory)) {
            return applicationRoot.resolve("codey").resolve("sessions").normalize();
        }
        Path configured = Paths.get(sessionDirectory);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        return applicationRoot.resolve(configured).normalize();
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

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    protected String normalizeSeparators(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }
}
