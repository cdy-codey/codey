package com.codey.infra;

import java.util.List;

/**
 * 结构化的工作目录列表结果。
 */
public class WorkspaceListResult {
    private String path;
    private String root;
    private int maxDepth;
    private int limit;
    private int returnedCount;
    private int totalCount;
    private int totalDiscovered;
    private int directoryCount;
    private int fileCount;
    private boolean truncated;
    private String summary;
    private List<WorkspaceEntry> entries;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getReturnedCount() {
        return returnedCount;
    }

    public void setReturnedCount(int returnedCount) {
        this.returnedCount = returnedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getTotalDiscovered() {
        return totalDiscovered;
    }

    public void setTotalDiscovered(int totalDiscovered) {
        this.totalDiscovered = totalDiscovered;
    }

    public int getDirectoryCount() {
        return directoryCount;
    }

    public void setDirectoryCount(int directoryCount) {
        this.directoryCount = directoryCount;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<WorkspaceEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<WorkspaceEntry> entries) {
        this.entries = entries;
    }
}
