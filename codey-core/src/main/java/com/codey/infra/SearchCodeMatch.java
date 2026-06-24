package com.codey.infra;

import java.util.List;

/**
 * 单条代码搜索命中结果。
 */
public class SearchCodeMatch {
    private String path;
    private int matchedLine;
    private int startLine;
    private int endLine;
    private List<NumberedLine> snippetLines;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getMatchedLine() {
        return matchedLine;
    }

    public void setMatchedLine(int matchedLine) {
        this.matchedLine = matchedLine;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public List<NumberedLine> getSnippetLines() {
        return snippetLines;
    }

    public void setSnippetLines(List<NumberedLine> snippetLines) {
        this.snippetLines = snippetLines;
    }
}
