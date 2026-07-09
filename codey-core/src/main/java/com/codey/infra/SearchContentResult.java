package com.codey.infra;

import java.util.List;

/**
 * 结构化的文件内容搜索结果。
 */
public class SearchCodeResult {
    private String root;
    private String pattern;
    private boolean regex;
    private boolean caseSensitive;
    private int contextLines;
    private int maxResults;
    private int filesSearched;
    private int totalMatches;
    private boolean truncated;
    private List<SearchCodeMatch> matches;

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public int getContextLines() {
        return contextLines;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getFilesSearched() {
        return filesSearched;
    }

    public void setFilesSearched(int filesSearched) {
        this.filesSearched = filesSearched;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public void setTotalMatches(int totalMatches) {
        this.totalMatches = totalMatches;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<SearchCodeMatch> getMatches() {
        return matches;
    }

    public void setMatches(List<SearchCodeMatch> matches) {
        this.matches = matches;
    }
}
