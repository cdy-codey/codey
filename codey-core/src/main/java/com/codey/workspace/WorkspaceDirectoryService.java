package com.codey.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 通用工作区目录服务。
 * 负责目录树查询、文件读写、重命名和删除等基础文件操作。
 */
public class WorkspaceDirectoryService {
    private final Path workspaceRoot;
    private final Set<String> ignoredEntryNames;
    private final Function<Path, String> visiblePathResolver;

    public WorkspaceDirectoryService(Path workspaceRoot,
                                     Set<String> ignoredEntryNames,
                                     Function<Path, String> visiblePathResolver) {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot must not be null");
        }
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.ignoredEntryNames = ignoredEntryNames == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new HashSet<String>(ignoredEntryNames));
        this.visiblePathResolver = visiblePathResolver == null
                ? new Function<Path, String>() {
                    @Override
                    public String apply(Path path) {
                        return path == null ? "" : normalizeSeparators(path.toAbsolutePath().normalize().toString());
                    }
                }
                : visiblePathResolver;
    }

    public WorkspaceSnapshot query(String relativePath) {
        try {
            return buildSnapshot(relativePath, "工作目录已加载");
        } catch (WorkspaceDirectoryException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new WorkspaceDirectoryException(
                    WorkspaceDirectoryException.ErrorType.INTERNAL_ERROR,
                    "查询工作目录失败",
                    exception
            );
        }
    }

    public WorkspaceSnapshot createEntry(String relativePath, boolean directory, String content) {
        Path target = resolvePath(relativePath, false);
        try {
            ensureWorkspaceRoot();
            if (Files.exists(target)) {
                throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.CONFLICT, "目标已存在");
            }
            if (directory) {
                Files.createDirectories(target);
                return buildSnapshot(null, "目录已创建");
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, normalizeContent(content).getBytes(StandardCharsets.UTF_8));
            return buildSnapshot(relativePath, "文件已创建");
        } catch (WorkspaceDirectoryException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new WorkspaceDirectoryException(
                    WorkspaceDirectoryException.ErrorType.INTERNAL_ERROR,
                    "创建工作区文件失败",
                    exception
            );
        }
    }

    /**
     * 统一处理创建、保存和重命名，避免上层继续区分多个写接口。
     */
    public WorkspaceSnapshot push(String relativePath, boolean directory, String content, String newName) {
        Path target = resolvePath(relativePath, false);
        String normalizedNewName = normalizeOptionalEntryName(newName);
        if (normalizedNewName != null) {
            return renameEntry(relativePath, normalizedNewName);
        }
        if (!Files.exists(target)) {
            return createEntry(relativePath, directory, content);
        }
        if (Files.isDirectory(target)) {
            if (directory) {
                return query(relativePath);
            }
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "目录不支持直接保存内容");
        }
        return updateFile(relativePath, content);
    }

    public WorkspaceSnapshot updateFile(String relativePath, String content) {
        Path target = resolvePath(relativePath, false);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.NOT_FOUND, "未找到指定文件");
        }
        try {
            Files.write(target, normalizeContent(content).getBytes(StandardCharsets.UTF_8));
            return buildSnapshot(relativePath, "文件已保存");
        } catch (IOException exception) {
            throw new WorkspaceDirectoryException(
                    WorkspaceDirectoryException.ErrorType.INTERNAL_ERROR,
                    "保存工作区文件失败",
                    exception
            );
        }
    }

    /**
     * 除了保存文件内容，也支持只修改当前节点名称，保持通用目录管理能力完整。
     */
    public WorkspaceSnapshot renameEntry(String relativePath, String newName) {
        Path target = resolvePath(relativePath, false);
        if (!Files.exists(target)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.NOT_FOUND, "未找到待重命名目标");
        }
        String normalizedNewName = normalizeEntryName(newName);
        Path currentName = target.getFileName();
        if (currentName != null && normalizedNewName.equals(currentName.toString())) {
            return query(relativePath);
        }
        Path parent = target.getParent();
        if (parent == null || !parent.startsWith(workspaceRoot)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "非法重命名目标");
        }
        Path renamed = parent.resolve(normalizedNewName).normalize();
        if (!renamed.startsWith(workspaceRoot)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "非法重命名目标");
        }
        if (Files.exists(renamed)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.CONFLICT, "目标名称已存在");
        }
        try {
            Files.move(target, renamed);
            return buildSnapshot(Files.isDirectory(renamed) ? null : toRelativePath(renamed), "名称已更新");
        } catch (IOException exception) {
            throw new WorkspaceDirectoryException(
                    WorkspaceDirectoryException.ErrorType.INTERNAL_ERROR,
                    "重命名工作区文件失败",
                    exception
            );
        }
    }

    public WorkspaceSnapshot deleteEntry(String relativePath) {
        Path target = resolvePath(relativePath, false);
        if (!Files.exists(target)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.NOT_FOUND, "未找到待删除目标");
        }
        try {
            deleteRecursively(target);
            return buildSnapshot(null, "目标已删除");
        } catch (IOException exception) {
            throw new WorkspaceDirectoryException(
                    WorkspaceDirectoryException.ErrorType.INTERNAL_ERROR,
                    "删除工作区文件失败",
                    exception
            );
        }
    }

    public String getVisibleWorkspaceRoot() {
        return toVisiblePath(workspaceRoot);
    }

    private WorkspaceSnapshot buildSnapshot(String currentPath, String status) throws IOException {
        ensureWorkspaceRoot();
        
        String normalizedPath = normalizeRelativePath(currentPath, true);
        String projectSegment = "";
        if (!normalizedPath.isEmpty()) {
            int separatorIndex = normalizedPath.indexOf('/');
            projectSegment = separatorIndex >= 0 ? normalizedPath.substring(0, separatorIndex) : normalizedPath;
        }

        FileCounter counter = countEntries(projectSegment);
        List<WorkspaceTreeNode> entries = listTree(workspaceRoot, 0, null);
        
        if (!projectSegment.isEmpty()) {
            List<WorkspaceTreeNode> scopedEntries = new ArrayList<WorkspaceTreeNode>();
            for (WorkspaceTreeNode entry : entries) {
                if (entry.isDirectory() && entry.getName().equals(projectSegment)) {
                    scopedEntries.add(entry);
                    break;
                }
            }
            if (!scopedEntries.isEmpty()) {
                entries = scopedEntries;
            }
        }

        ProjectScope projectScope = resolveProjectScope(entries, currentPath);
        WorkspaceFile currentFile = buildCurrentFile(currentPath);
        return new WorkspaceSnapshot(
                toVisiblePath(workspaceRoot),
                counter.getFileCount(),
                counter.getDirectoryCount(),
                entries,
                currentFile,
                projectScope.getProjectPath(),
                projectScope.getProjectWorkingDirectory(),
                projectScope.getCurrentFilePath(),
                projectScope.getProjectPath(),  // workspaceId 即当前所属工作区名
                status,
                new Date()
        );
    }

    private WorkspaceFile buildCurrentFile(String relativePath) throws IOException {
        String normalized = normalizeRelativePath(relativePath, true);
        if (normalized.isEmpty()) {
            return null;
        }
        Path target = resolvePath(normalized, false);
        if (!Files.exists(target)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.NOT_FOUND, "未找到指定文件");
        }
        if (Files.isDirectory(target)) {
            return null;
        }
        String fileKey = toRelativePath(target);
        // 拆分工作区标识和工作区相对展示路径
        String workspaceId = "";
        String workspaceRelativePath = fileKey;
        int separatorIndex = fileKey.indexOf('/');
        if (separatorIndex > 0) {
            workspaceId = fileKey.substring(0, separatorIndex);
            workspaceRelativePath = fileKey.substring(separatorIndex + 1);
        }
        return new WorkspaceFile(
                target.getFileName().toString(),
                workspaceRelativePath,
                fileKey,
                workspaceId,
                new String(Files.readAllBytes(target), StandardCharsets.UTF_8),
                new Date(Files.getLastModifiedTime(target).toMillis())
        );
    }

    private List<WorkspaceTreeNode> listTree(Path currentRoot, int depth, Path workspacePath) throws IOException {
        if (!Files.isDirectory(currentRoot)) {
            return Collections.emptyList();
        }
        List<Path> children = new ArrayList<Path>();
        try (Stream<Path> stream = Files.list(currentRoot)) {
            stream
                    .filter(path -> !isIgnoredPath(path))
                    .sorted(new Comparator<Path>() {
                        @Override
                        public int compare(Path left, Path right) {
                            boolean leftDirectory = Files.isDirectory(left);
                            boolean rightDirectory = Files.isDirectory(right);
                            if (leftDirectory != rightDirectory) {
                                return leftDirectory ? -1 : 1;
                            }
                            return left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString());
                        }
                    })
                    .forEach(children::add);
        }
        List<WorkspaceTreeNode> nodes = new ArrayList<WorkspaceTreeNode>();
        for (Path child : children) {
            boolean directory = Files.isDirectory(child);
            boolean isProjectRoot = depth == 0 && directory;

            // 工作区节点本身成为其子树的工作区根路径
            Path childWorkspacePath = isProjectRoot ? child : workspacePath;

            // fileKey 始终为相对于 workspaceRoot 的完整路径，用于后端文件操作
            String fileKey = toRelativePath(child);

            // path 为展示路径：工作区节点保留完整名称，子级节点相对于所属工作区（移除工作区名前缀）
            String displayPath;
            String workspaceId;
            if (isProjectRoot) {
                displayPath = fileKey;
                workspaceId = child.getFileName().toString();
            } else if (workspacePath != null) {
                displayPath = normalizeSeparators(workspacePath.relativize(child).toString());
                workspaceId = workspacePath.getFileName().toString();
            } else {
                displayPath = fileKey;
                workspaceId = "";
            }

            nodes.add(new WorkspaceTreeNode(
                    child.getFileName().toString(),
                    displayPath,
                    fileKey,
                    workspaceId,
                    isProjectRoot,
                    directory,
                    directory ? listTree(child, depth + 1, childWorkspacePath) : Collections.<WorkspaceTreeNode>emptyList()
            ));
        }
        return nodes;
    }

    private ProjectScope resolveProjectScope(List<WorkspaceTreeNode> entries, String currentPath) {
        String normalizedPath = normalizeRelativePath(currentPath, true);
        String projectPath = "";
        if (!normalizedPath.isEmpty()) {
            int separatorIndex = normalizedPath.indexOf('/');
            String firstSegment = separatorIndex >= 0 ? normalizedPath.substring(0, separatorIndex) : normalizedPath;
            for (WorkspaceTreeNode entry : entries) {
                if (entry.isDirectory() && firstSegment.equals(entry.getPath())) {
                    projectPath = entry.getPath();
                    break;
                }
            }
        }
        if (projectPath.isEmpty()) {
            for (WorkspaceTreeNode entry : entries) {
                if (entry.isProjectRoot()) {
                    projectPath = entry.getPath();
                    break;
                }
            }
        }
        if (projectPath.isEmpty()) {
            return new ProjectScope("", toVisiblePath(workspaceRoot), normalizedPath);
        }
        String currentFilePath = normalizedPath;
        if (!currentFilePath.isEmpty()) {
            if (currentFilePath.equals(projectPath)) {
                currentFilePath = "";
            } else if (currentFilePath.startsWith(projectPath + "/")) {
                currentFilePath = currentFilePath.substring(projectPath.length() + 1);
            }
        }
        return new ProjectScope(
                projectPath,
                toVisiblePath(workspaceRoot.resolve(projectPath)),
                currentFilePath
        );
    }

    private FileCounter countEntries(String projectSegment) throws IOException {
        FileCounter counter = new FileCounter();
        Path targetRoot = workspaceRoot;
        if (projectSegment != null && !projectSegment.isEmpty()) {
            Path scoped = workspaceRoot.resolve(projectSegment).normalize();
            if (Files.isDirectory(scoped) && scoped.startsWith(workspaceRoot)) {
                targetRoot = scoped;
                counter.incrementDirectoryCount();
            }
        }
        if (!Files.exists(targetRoot)) {
            return counter;
        }
        final Path finalTargetRoot = targetRoot;
        try (Stream<Path> stream = Files.walk(finalTargetRoot)) {
            stream
                    .filter(path -> !path.equals(finalTargetRoot))
                    .filter(path -> !containsIgnoredSegment(workspaceRoot.relativize(path)))
                    .forEach(path -> {
                        if (Files.isDirectory(path)) {
                            counter.incrementDirectoryCount();
                        } else {
                            counter.incrementFileCount();
                        }
                    });
        }
        return counter;
    }

    private void ensureWorkspaceRoot() throws IOException {
        Files.createDirectories(workspaceRoot);
    }

    private void deleteRecursively(Path target) throws IOException {
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path resolvePath(String relativePath, boolean allowBlank) {
        String normalized = normalizeRelativePath(relativePath, allowBlank);
        if (normalized.isEmpty()) {
            return workspaceRoot;
        }
        Path resolved = workspaceRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "非法文件路径");
        }
        return resolved;
    }

    private String normalizeRelativePath(String relativePath, boolean allowBlank) {
        String candidate = relativePath == null ? "" : relativePath.trim().replace('\\', '/');
        while (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }
        if (candidate.isEmpty()) {
            if (allowBlank) {
                return "";
            }
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "文件路径不能为空");
        }
        if (candidate.contains("..")) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "非法文件路径");
        }
        return candidate;
    }

    private boolean isIgnoredPath(Path path) {
        Path fileName = path == null ? null : path.getFileName();
        return fileName != null && ignoredEntryNames.contains(fileName.toString());
    }

    private boolean containsIgnoredSegment(Path relativePath) {
        if (relativePath == null) {
            return false;
        }
        for (Path segment : relativePath) {
            if (ignoredEntryNames.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    private String normalizeEntryName(String entryName) {
        String normalized = entryName == null ? "" : entryName.trim();
        if (normalized.isEmpty()) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "名称不能为空");
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new WorkspaceDirectoryException(WorkspaceDirectoryException.ErrorType.BAD_REQUEST, "名称不合法");
        }
        return normalized;
    }

    private String normalizeOptionalEntryName(String entryName) {
        if (entryName == null) {
            return null;
        }
        String normalized = entryName.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalizeEntryName(normalized);
    }

    private String toRelativePath(Path target) {
        return normalizeSeparators(workspaceRoot.relativize(target).toString());
    }

    private String toVisiblePath(Path path) {
        return visiblePathResolver.apply(path);
    }

    private static String normalizeSeparators(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static final class FileCounter {
        private int fileCount;
        private int directoryCount;

        public int getFileCount() {
            return fileCount;
        }

        public int getDirectoryCount() {
            return directoryCount;
        }

        public void incrementFileCount() {
            this.fileCount += 1;
        }

        public void incrementDirectoryCount() {
            this.directoryCount += 1;
        }
    }

    private static final class ProjectScope {
        private final String projectPath;
        private final String projectWorkingDirectory;
        private final String currentFilePath;

        private ProjectScope(String projectPath, String projectWorkingDirectory, String currentFilePath) {
            this.projectPath = projectPath;
            this.projectWorkingDirectory = projectWorkingDirectory;
            this.currentFilePath = currentFilePath;
        }

        public String getProjectPath() {
            return projectPath;
        }

        public String getProjectWorkingDirectory() {
            return projectWorkingDirectory;
        }

        public String getCurrentFilePath() {
            return currentFilePath;
        }
    }

}
