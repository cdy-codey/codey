package com.codey.infra;

/**
 * 工作目录列表请求。
 */
public class WorkspaceListRequest {
    private String pathHint;
    private Integer limit;
    private Integer maxDepth;
    private Boolean includeHidden;

    public String getPathHint() {
        return pathHint;
    }

    public void setPathHint(String pathHint) {
        this.pathHint = pathHint;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Boolean getIncludeHidden() {
        return includeHidden;
    }

    public void setIncludeHidden(Boolean includeHidden) {
        this.includeHidden = includeHidden;
    }
}
