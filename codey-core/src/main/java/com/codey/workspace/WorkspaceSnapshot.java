package com.codey.workspace;

import java.util.Date;
import java.util.List;

/**
 * 工作区快照。
 * 用于承载目录树、当前文件和项目作用域等展示信息。
 */
public class WorkspaceSnapshot {
    private final String rootPath;
    private final int fileCount;
    private final int directoryCount;
    private final List<WorkspaceTreeNode> entries;
    private final WorkspaceFile currentFile;
    private final String projectPath;
    private final String projectWorkingDirectory;
    private final String currentProjectFilePath;
    private final String workspaceId;
    private final String status;
    private final Date updatedAt;

    public WorkspaceSnapshot(String rootPath,
                             int fileCount,
                             int directoryCount,
                             List<WorkspaceTreeNode> entries,
                             WorkspaceFile currentFile,
                             String projectPath,
                             String projectWorkingDirectory,
                             String currentProjectFilePath,
                             String status,
                             Date updatedAt) {
        this(rootPath, fileCount, directoryCount, entries, currentFile,
             projectPath, projectWorkingDirectory, currentProjectFilePath, "", status, updatedAt);
    }

    public WorkspaceSnapshot(String rootPath,
                             int fileCount,
                             int directoryCount,
                             List<WorkspaceTreeNode> entries,
                             WorkspaceFile currentFile,
                             String projectPath,
                             String projectWorkingDirectory,
                             String currentProjectFilePath,
                             String workspaceId,
                             String status,
                             Date updatedAt) {
        this.rootPath = rootPath;
        this.fileCount = fileCount;
        this.directoryCount = directoryCount;
        this.entries = entries;
        this.currentFile = currentFile;
        this.projectPath = projectPath;
        this.projectWorkingDirectory = projectWorkingDirectory;
        this.currentProjectFilePath = currentProjectFilePath;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
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

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getStatus() {
        return status;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }
}
