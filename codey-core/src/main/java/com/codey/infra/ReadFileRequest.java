package com.codey.infra;

/**
 * 读取文件请求。
 */
public class ReadFileRequest {
    private String path;
    private Integer offset;
    private Integer limit;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
