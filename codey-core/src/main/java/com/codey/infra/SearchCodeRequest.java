package com.codey.infra;

/**
 * 代码搜索请求。
 */
public class SearchCodeRequest {
    private String keyword;
    private String pathHint;
    private Boolean regex;
    private Boolean caseSensitive;
    private Integer contextLines;
    private Integer maxResults;
    private String filePattern;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getPathHint() {
        return pathHint;
    }

    public void setPathHint(String pathHint) {
        this.pathHint = pathHint;
    }

    public Boolean getRegex() {
        return regex;
    }

    public void setRegex(Boolean regex) {
        this.regex = regex;
    }

    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public Integer getContextLines() {
        return contextLines;
    }

    public void setContextLines(Integer contextLines) {
        this.contextLines = contextLines;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }
}
