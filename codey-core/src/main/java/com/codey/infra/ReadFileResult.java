package com.codey.infra;

import java.util.List;

/**
 * 结构化的文件读取结果。
 */
public class ReadFileResult {
    private String path;
    private int startLine;
    private int endLine;
    private int totalLines;
    private boolean truncated;
    private List<NumberedLine> lines;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public int getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(int totalLines) {
        this.totalLines = totalLines;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<NumberedLine> getLines() {
        return lines;
    }

    public void setLines(List<NumberedLine> lines) {
        this.lines = lines;
    }
}
