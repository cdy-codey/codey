package com.codey.infra;

/**
 * 表示带行号的文本行，供读取文件和代码搜索结果复用。
 */
public class NumberedLine {
    private int lineNumber;
    private String content;

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
