package com.codey.infra;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 统一处理工作区内的相对路径与真实绝对路径转换。
 * 对外始终暴露相对工作区根目录的路径，只有实际访问文件系统时才还原为绝对路径。
 */
public final class WorkspacePathSupport {
    private WorkspacePathSupport() {
    }

    /**
     * 把工作目录文本收敛成对外可见的相对路径表示。
     */
    public static String sanitizeWorkingDirectory(String workingDirectory, Path workspaceRoot) {
        if (workspaceRoot == null) {
            return toDisplayPath(normalizeRelativeText(workingDirectory));
        }
        Path resolved = resolveWorkingDirectory(workspaceRoot, workingDirectory);
        return toDisplayPath(relativize(workspaceRoot, resolved));
    }

    /**
     * 解析工作区内的相对路径，显式拒绝绝对路径输入。
     */
    public static Path resolveRelativePath(Path workspaceRoot, String relativePath) {
        Path normalizedRoot = requireWorkspaceRoot(workspaceRoot);
        if (isBlank(relativePath)) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        Path candidate = Paths.get(normalizeVisibleRelativePath(relativePath, normalizedRoot));
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("Absolute path is not allowed: " + relativePath);
        }
        Path resolved = normalizedRoot.resolve(candidate).normalize();
        ensureInsideRoot(normalizedRoot, resolved, relativePath);
        return resolved;
    }

    /**
     * 解析当前会话的工作目录。兼容历史绝对路径数据，但最终仍会被裁剪回工作区内。
     */
    public static Path resolveWorkingDirectory(Path workspaceRoot, String workingDirectory) {
        Path normalizedRoot = requireWorkspaceRoot(workspaceRoot);
        if (isBlank(workingDirectory)) {
            return normalizedRoot;
        }
        Path candidate = Paths.get(normalizeVisibleRelativePath(workingDirectory, normalizedRoot));
        Path resolved = candidate.isAbsolute()
                ? candidate.toAbsolutePath().normalize()
                : normalizedRoot.resolve(candidate).normalize();
        ensureInsideRoot(normalizedRoot, resolved, workingDirectory);
        return resolved;
    }

    /**
     * 统一把工具入参规范成相对工作区根目录的路径。
     */
    public static String normalizeToolPathArgument(String rawPath, String workingDirectory, Path workspaceRoot) {
        if (isBlank(rawPath)) {
            return "";
        }
        String normalized = normalizeRelativeInput(rawPath);
        String workingDirectoryText = normalizeRelativeInput(workingDirectory);
        if (!workingDirectoryText.isEmpty()) {
            String prefix = workingDirectoryText + "/";
            while (normalized.equals(workingDirectoryText) || normalized.startsWith(prefix)) {
                normalized = normalized.equals(workingDirectoryText)
                        ? ""
                        : normalized.substring(prefix.length());
            }
        }
        if (normalized.isEmpty()) {
            return ".";
        }
        if (workspaceRoot != null) {
            Path workingDirectoryPath = resolveWorkingDirectory(workspaceRoot, workingDirectory);
            Path resolved = workingDirectoryPath.resolve(normalized).normalize();
            ensureInsideRoot(requireWorkspaceRoot(workspaceRoot), resolved, rawPath);
        }
        return normalized;
    }

    /**
     * 按“工具路径永远相对当前 workingDirectory”解析真实文件路径。
     */
    public static Path resolveToolPath(Path workspaceRoot, String workingDirectory, String toolPath) {
        Path normalizedRoot = requireWorkspaceRoot(workspaceRoot);
        Path workingDirectoryPath = resolveWorkingDirectory(normalizedRoot, workingDirectory);
        String normalized = normalizeToolPathArgument(toolPath, workingDirectory, normalizedRoot);
        Path resolved = workingDirectoryPath.resolve(normalized).normalize();
        ensureInsideRoot(normalizedRoot, resolved, toolPath);
        return resolved;
    }

    /**
     * 工具结果回显时统一裁剪成相对当前 workingDirectory 的路径。
     */
    public static String relativizeToolPath(Path workspaceRoot, String workingDirectory, Path target) {
        Path normalizedRoot = requireWorkspaceRoot(workspaceRoot);
        Path workingDirectoryPath = resolveWorkingDirectory(normalizedRoot, workingDirectory);
        Path normalizedTarget = target == null ? workingDirectoryPath : target.toAbsolutePath().normalize();
        ensureInsideRoot(normalizedRoot, normalizedTarget, String.valueOf(target));
        if (workingDirectoryPath.equals(normalizedTarget)) {
            return ".";
        }
        return normalizeSeparators(workingDirectoryPath.relativize(normalizedTarget).toString());
    }

    /**
     * 把上下文说明中的路径别名裁剪成工作区内可见形式。
     */
    public static String sanitizeContextText(String text, Path workspaceRoot) {
        if (isBlank(text) || workspaceRoot == null) {
            return text;
        }
        String normalized = text.replace("\\", "/");
        String rootName = workspaceRoot.getFileName() == null ? "" : workspaceRoot.getFileName().toString();
        if (rootName.isEmpty()) {
            return normalized;
        }
        normalized = normalized.replace("./" + rootName + "/", "./");
        normalized = normalized.replace("/" + rootName + "/", "/");
        normalized = normalized.replace(rootName + "/", "");
        return normalized;
    }

    /**
     * 把真实路径裁剪成工作区根目录下的可见相对路径。
     */
    public static String relativize(Path workspaceRoot, Path target) {
        Path normalizedRoot = requireWorkspaceRoot(workspaceRoot);
        Path normalizedTarget = target == null
                ? normalizedRoot
                : target.toAbsolutePath().normalize();
        ensureInsideRoot(normalizedRoot, normalizedTarget, String.valueOf(target));
        if (normalizedRoot.equals(normalizedTarget)) {
            return ".";
        }
        return normalizeSeparators(normalizedRoot.relativize(normalizedTarget).toString());
    }

    private static Path requireWorkspaceRoot(Path workspaceRoot) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("Workspace root must not be null");
        }
        return workspaceRoot.toAbsolutePath().normalize();
    }

    private static void ensureInsideRoot(Path workspaceRoot, Path resolved, String originalValue) {
        if (!resolved.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path escapes workspace root: " + originalValue);
        }
    }

    private static boolean startsWithPath(Path path, Path prefix) {
        if (path == null || prefix == null) {
            return false;
        }
        if (prefix.getNameCount() == 0) {
            return false;
        }
        if (path.getNameCount() < prefix.getNameCount()) {
            return false;
        }
        for (int index = 0; index < prefix.getNameCount(); index++) {
            if (!String.valueOf(path.getName(index)).equals(String.valueOf(prefix.getName(index)))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeRelativeText(String rawPath) {
        if (isBlank(rawPath)) {
            return ".";
        }
        Path candidate = Paths.get(rawPath.trim()).normalize();
        if (candidate.isAbsolute()) {
            return normalizeSeparators(candidate.getFileName() == null ? "." : candidate.getFileName().toString());
        }
        String normalized = normalizeSeparators(candidate.toString());
        return normalized.isEmpty() ? "." : normalized;
    }

    private static String normalizeRelativeInput(String rawPath) {
        if (isBlank(rawPath)) {
            return "";
        }
        String normalized = normalizeSeparators(rawPath.trim());
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return ".".equals(normalized) ? "" : normalized;
    }

    private static String normalizeVisibleRelativePath(String rawPath, Path workspaceRoot) {
        if (isBlank(rawPath)) {
            return ".";
        }
        String normalized = normalizeSeparators(rawPath.trim());
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String rootName = workspaceRoot == null || workspaceRoot.getFileName() == null
                ? ""
                : workspaceRoot.getFileName().toString();
        while (!rootName.isEmpty()
                && (normalized.equals(rootName) || normalized.startsWith(rootName + "/"))) {
            normalized = normalized.length() == rootName.length()
                    ? ""
                    : normalized.substring(rootName.length() + 1);
        }
        return normalized.isEmpty() ? "." : normalized;
    }

    private static String toDisplayPath(String relativePath) {
        if (isBlank(relativePath) || ".".equals(relativePath)) {
            return ".";
        }
        return relativePath.startsWith("./") ? relativePath : "./" + relativePath;
    }

    private static String normalizeSeparators(String value) {
        return value == null ? "" : value.replace("\\", "/");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
