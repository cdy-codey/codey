package com.codey.web.service;

import com.codey.web.config.WebDemoProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 单工作目录文件服务。
 * 只保留工作目录树查询、创建、编辑、删除四类基础操作。
 */
@Service
public class WorkspaceService {
    private static final Set<String> STANDARD_IGNORED_ENTRY_NAMES = new HashSet<String>(Arrays.asList(
            ".git", ".idea", "node_modules", "target", "dist"
    ));

    private final WebDemoProperties properties;
    private final Path workspaceRoot;
    private final Set<String> ignoredEntryNames;

    public WorkspaceService(WebDemoProperties properties) {
        this.properties = properties;
        this.workspaceRoot = properties.resolveWorkingDirectoryRoot();
        this.ignoredEntryNames = buildIgnoredEntryNames(properties.getSessionDirectory());
    }

    public WorkspaceSnapshot query(String relativePath) {
        try {
            return buildSnapshot(relativePath, "工作目录已加载");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "查询工作目录失败", exception);
        }
    }

    /**
     * create 接口同时支持创建文件和目录，便于后续扩展目录管理能力。
     */
    public WorkspaceSnapshot createEntry(String relativePath, boolean directory, String content) {
        Path target = resolvePath(relativePath, false);
        try {
            ensureWorkspaceRoot();
            if (Files.exists(target)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "目标已存在");
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
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建工作区文件失败", exception);
        }
    }

    public WorkspaceSnapshot updateFile(String relativePath, String content) {
        Path target = resolvePath(relativePath, false);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定文件");
        }
        try {
            Files.write(target, normalizeContent(content).getBytes(StandardCharsets.UTF_8));
            return buildSnapshot(relativePath, "文件已保存");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存工作区文件失败", exception);
        }
    }

    /**
     * update 接口除了保存文件内容，也支持只修改当前节点名称，避免继续膨胀新路由。
     */
    public WorkspaceSnapshot renameEntry(String relativePath, String newName) {
        Path target = resolvePath(relativePath, false);
        if (!Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到待重命名目标");
        }
        String normalizedNewName = normalizeEntryName(newName);
        Path currentName = target.getFileName();
        if (currentName != null && normalizedNewName.equals(currentName.toString())) {
            return query(relativePath);
        }
        Path parent = target.getParent();
        if (parent == null || !parent.startsWith(workspaceRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法重命名目标");
        }
        Path renamed = parent.resolve(normalizedNewName).normalize();
        if (!renamed.startsWith(workspaceRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法重命名目标");
        }
        if (Files.exists(renamed)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标名称已存在");
        }
        try {
            Files.move(target, renamed);
            return buildSnapshot(Files.isDirectory(renamed) ? null : toRelativePath(renamed), "名称已更新");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "重命名工作区文件失败", exception);
        }
    }

    public WorkspaceSnapshot deleteEntry(String relativePath) {
        Path target = resolvePath(relativePath, false);
        if (!Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到待删除目标");
        }
        try {
            deleteRecursively(target);
            return buildSnapshot(null, "目标已删除");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除工作区文件失败", exception);
        }
    }

    public String getVisibleWorkspaceRoot() {
        return properties.toVisiblePath(workspaceRoot);
    }

    private WorkspaceSnapshot buildSnapshot(String currentPath, String status) throws IOException {
        ensureWorkspaceRoot();
        FileCounter counter = countEntries();
        List<WorkspaceTreeNode> entries = listTree(workspaceRoot, 0);
        ProjectScope projectScope = resolveProjectScope(entries, currentPath);
        WorkspaceFile currentFile = buildCurrentFile(currentPath);
        return new WorkspaceSnapshot(
                properties.toVisiblePath(workspaceRoot),
                counter.getFileCount(),
                counter.getDirectoryCount(),
                entries,
                currentFile,
                projectScope.getProjectPath(),
                projectScope.getProjectWorkingDirectory(),
                projectScope.getCurrentFilePath(),
                status,
                Instant.now()
        );
    }

    private WorkspaceFile buildCurrentFile(String relativePath) throws IOException {
        String normalized = normalizeRelativePath(relativePath, true);
        if (normalized.isEmpty()) {
            return null;
        }
        Path target = resolvePath(normalized, false);
        if (!Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定文件");
        }
        if (Files.isDirectory(target)) {
            return null;
        }
        String fileKey = toRelativePath(target);
        return new WorkspaceFile(
                target.getFileName().toString(),
                fileKey,
                new String(Files.readAllBytes(target), StandardCharsets.UTF_8),
                Files.getLastModifiedTime(target).toInstant()
        );
    }

    private List<WorkspaceTreeNode> listTree(Path currentRoot, int depth) throws IOException {
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
            nodes.add(new WorkspaceTreeNode(
                    child.getFileName().toString(),
                    toRelativePath(child),
                    depth == 0 && directory,
                    directory,
                    directory ? listTree(child, depth + 1) : Collections.<WorkspaceTreeNode>emptyList()
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
            return new ProjectScope("", properties.toVisiblePath(workspaceRoot), normalizedPath);
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
                properties.toVisiblePath(workspaceRoot.resolve(projectPath)),
                currentFilePath
        );
    }

    private FileCounter countEntries() throws IOException {
        FileCounter counter = new FileCounter();
        if (!Files.exists(workspaceRoot)) {
            return counter;
        }
        try (Stream<Path> stream = Files.walk(workspaceRoot)) {
            stream
                    .filter(path -> !path.equals(workspaceRoot))
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件路径");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径不能为空");
        }
        if (candidate.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件路径");
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

    private Set<String> buildIgnoredEntryNames(String sessionDirectory) {
        Set<String> ignoredNames = new HashSet<String>(STANDARD_IGNORED_ENTRY_NAMES);
        Path normalizedSessionDirectory = Paths.get(sessionDirectory).normalize();
        Path fileName = normalizedSessionDirectory.getFileName();
        if (fileName != null) {
            ignoredNames.add(fileName.toString());
        }
        return ignoredNames;
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    private String normalizeEntryName(String entryName) {
        String normalized = entryName == null ? "" : entryName.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不合法");
        }
        return normalized;
    }

    private String toRelativePath(Path target) {
        return workspaceRoot.relativize(target).toString().replace('\\', '/');
    }

    private static String buildDocumentId(String fileKey) {
        return "workspace:" + fileKey;
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

    public static class WorkspaceSnapshot {
        private final String rootPath;
        private final int fileCount;
        private final int directoryCount;
        private final List<WorkspaceTreeNode> entries;
        private final WorkspaceFile currentFile;
        private final String projectPath;
        private final String projectWorkingDirectory;
        private final String currentProjectFilePath;
        private final String status;
        private final Instant updatedAt;

        public WorkspaceSnapshot(String rootPath,
                                 int fileCount,
                                 int directoryCount,
                                 List<WorkspaceTreeNode> entries,
                                 WorkspaceFile currentFile,
                                 String projectPath,
                                 String projectWorkingDirectory,
                                 String currentProjectFilePath,
                                 String status,
                                 Instant updatedAt) {
            this.rootPath = rootPath;
            this.fileCount = fileCount;
            this.directoryCount = directoryCount;
            this.entries = entries;
            this.currentFile = currentFile;
            this.projectPath = projectPath;
            this.projectWorkingDirectory = projectWorkingDirectory;
            this.currentProjectFilePath = currentProjectFilePath;
            this.status = status;
            this.updatedAt = updatedAt;
        }

        public String getRootPath() {
            return rootPath;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getDirectoryCount() {
            return directoryCount;
        }

        public List<WorkspaceTreeNode> getEntries() {
            return entries;
        }

        public WorkspaceFile getCurrentFile() {
            return currentFile;
        }

        public String getProjectPath() {
            return projectPath;
        }

        public String getProjectWorkingDirectory() {
            return projectWorkingDirectory;
        }

        public String getCurrentProjectFilePath() {
            return currentProjectFilePath;
        }

        public String getStatus() {
            return status;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }
    }

    public static class WorkspaceTreeNode {
        private final String name;
        private final String label;
        private final String path;
        private final String fileKey;
        private final String documentId;
        private final String nodeType;
        private final boolean projectRoot;
        private final boolean directory;
        private final boolean creatable;
        private final List<WorkspaceTreeNode> children;

        public WorkspaceTreeNode(String name, String fileKey, boolean projectRoot, boolean directory, List<WorkspaceTreeNode> children) {
            this.name = name;
            this.label = name;
            this.path = fileKey;
            this.fileKey = fileKey;
            this.documentId = buildDocumentId(fileKey);
            this.nodeType = directory ? "directory" : "file";
            this.projectRoot = projectRoot;
            this.directory = directory;
            this.creatable = directory;
            this.children = children;
        }

        public String getName() {
            return name;
        }

        public String getLabel() {
            return label;
        }

        public String getPath() {
            return path;
        }

        public String getFileKey() {
            return fileKey;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public boolean isProjectRoot() {
            return projectRoot;
        }

        public boolean isDirectory() {
            return directory;
        }

        public boolean isCreatable() {
            return creatable;
        }

        public List<WorkspaceTreeNode> getChildren() {
            return children;
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

    public static class WorkspaceFile {
        private final String name;
        private final String path;
        private final String fileKey;
        private final String documentId;
        private final String content;
        private final Instant updatedAt;

        public WorkspaceFile(String name, String fileKey, String content, Instant updatedAt) {
            this.name = name;
            this.path = fileKey;
            this.fileKey = fileKey;
            this.documentId = buildDocumentId(fileKey);
            this.content = content;
            this.updatedAt = updatedAt;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public String getFileKey() {
            return fileKey;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getContent() {
            return content;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }
    }
}
