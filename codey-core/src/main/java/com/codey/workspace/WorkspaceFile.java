package com.codey.workspace;

import java.util.Date;

/**
 * 工作区文件内容快照。
 * 用于返回当前选中文件的内容与更新时间。
 * path 为工作区相对路径（不含工作区名），fileKey 为完整相对路径（从 workspaceRoot 起算）。
 */
public class WorkspaceFile {
    private final String name;
    private final String path;
    private final String fileKey;
    private final String workspaceId;
    private final String content;
    private final Date updatedAt;

    public WorkspaceFile(String name, String fileKey, String content, Date updatedAt) {
        this(name, fileKey, fileKey, "", content, updatedAt);
    }

    /**
     * 完整构造器。
     * @param path        工作区相对路径（不含工作区名前缀），用于前端展示
     * @param fileKey     完整相对路径（从 workspaceRoot 起算），用于后端文件操作
     * @param workspaceId 所属工作区标识
     */
    public WorkspaceFile(String name, String path, String fileKey, String workspaceId,
                         String content, Date updatedAt) {
        this.name = name;
        this.path = path;
        this.fileKey = fileKey;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
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

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getContent() {
        return content;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }
}
