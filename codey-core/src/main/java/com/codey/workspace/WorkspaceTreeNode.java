package com.codey.workspace;

import java.util.List;

/**
 * 工作区树节点。
 * 同时承载目录树展示所需的路径和类型信息。
 * path 为展示路径（工作区相对路径，不含工作区名），fileKey 为完整相对路径（从 workspaceRoot 起算，用于后端操作）。
 */
public class WorkspaceTreeNode {
    private final String name;
    private final String label;
    private final String path;
    private final String fileKey;
    private final String nodeType;
    private final boolean projectRoot;
    private final boolean directory;
    private final boolean creatable;
    private final String workspaceId;
    private final List<WorkspaceTreeNode> children;

    public WorkspaceTreeNode(String name, String fileKey, boolean projectRoot, boolean directory, List<WorkspaceTreeNode> children) {
        this(name, fileKey, fileKey, "", projectRoot, directory, children);
    }

    /**
     * 完整构造器。
     * @param path         展示路径，子级节点为工作区相对路径（不含工作区名前缀）
     * @param fileKey      完整相对路径（从 workspaceRoot 起算），用于后端文件操作
     * @param workspaceId  所属工作区标识，工作区节点为自己的名称，子节点继承
     */
    public WorkspaceTreeNode(String name, String path, String fileKey, String workspaceId,
                             boolean projectRoot, boolean directory, List<WorkspaceTreeNode> children) {
        this.name = name;
        this.label = name;
        this.path = path;
        this.fileKey = fileKey;
        this.nodeType = directory ? "directory" : "file";
        this.projectRoot = projectRoot;
        this.directory = directory;
        this.creatable = directory;
        this.workspaceId = workspaceId == null ? "" : workspaceId;
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

    public String getWorkspaceId() {
        return workspaceId;
    }

    public List<WorkspaceTreeNode> getChildren() {
        return children;
    }
}
