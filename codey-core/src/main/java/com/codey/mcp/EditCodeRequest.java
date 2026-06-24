package com.codey.mcp;

import com.codey.tools.*;

/**
 * `edit_code` 工具的结构化请求参数。
 */
public class EditCodeRequest {
    private String file;
    private String instruction;
    private EditMode mode = EditMode.APPEND;
    private String targetMarker;
    private String startMarker;
    private String endMarker;
    private String searchText;
    private String replaceText;
    private String generatedContent;

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public EditMode getMode() {
        return mode;
    }

    public void setMode(EditMode mode) {
        this.mode = mode;
    }

    public String getTargetMarker() {
        return targetMarker;
    }

    public void setTargetMarker(String targetMarker) {
        this.targetMarker = targetMarker;
    }

    public String getStartMarker() {
        return startMarker;
    }

    public void setStartMarker(String startMarker) {
        this.startMarker = startMarker;
    }

    public String getEndMarker() {
        return endMarker;
    }

    public void setEndMarker(String endMarker) {
        this.endMarker = endMarker;
    }

    public String getGeneratedContent() {
        return generatedContent;
    }

    public void setGeneratedContent(String generatedContent) {
        this.generatedContent = generatedContent;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public String getReplaceText() {
        return replaceText;
    }

    public void setReplaceText(String replaceText) {
        this.replaceText = replaceText;
    }
}

