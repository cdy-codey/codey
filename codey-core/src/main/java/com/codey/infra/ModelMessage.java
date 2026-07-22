package com.codey.infra;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型请求中的一条结构化消息。
 */
public class ModelMessage {
    private ModelMessageRole role;
    private String content;
    private Boolean summary;
    private String reasoningContent;
    private String toolCallId;
    private String toolName;
    private List<ModelToolCall> toolCalls = new ArrayList<ModelToolCall>();

    public ModelMessage() {
    }

    public ModelMessage(String role, String content) {
        this(ModelMessageRole.fromValue(role), content);
    }

    public ModelMessage(ModelMessageRole role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role == null ? null : role.getWireValue();
    }

    public void setRole(String role) {
        this.role = ModelMessageRole.fromValue(role);
    }

    @JsonIgnore
    public ModelMessageRole getRoleEnum() {
        return role;
    }

    public void setRoleEnum(ModelMessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getSummary() {
        return summary;
    }

    public void setSummary(Boolean summary) {
        this.summary = summary;
    }

    @JsonIgnore
    public boolean isSummaryMessage() {
        return Boolean.TRUE.equals(summary);
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public List<ModelToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ModelToolCall> toolCalls) {
        this.toolCalls = toolCalls == null ? new ArrayList<ModelToolCall>() : new ArrayList<ModelToolCall>(toolCalls);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean isSystem() {
        return role == ModelMessageRole.SYSTEM;
    }

    public boolean isUser() {
        return role == ModelMessageRole.USER;
    }

    public boolean isAssistant() {
        return role == ModelMessageRole.ASSISTANT;
    }

    public boolean isToolResult() {
        return role == ModelMessageRole.TOOL;
    }

    public static ModelMessage system(String content) {
        return new ModelMessage(ModelMessageRole.SYSTEM, content);
    }

    public static ModelMessage systemSummary(String content) {
        ModelMessage message = system(content);
        message.setSummary(Boolean.TRUE);
        return message;
    }

    public static ModelMessage user(String content) {
        return new ModelMessage(ModelMessageRole.USER, content);
    }

    public static ModelMessage assistant(String content) {
        return new ModelMessage(ModelMessageRole.ASSISTANT, content);
    }

    public static ModelMessage assistant(String content, String reasoningContent) {
        ModelMessage message = assistant(content);
        message.setReasoningContent(reasoningContent);
        return message;
    }

    public static ModelMessage assistantToolCalls(List<ModelToolCall> toolCalls) {
        ModelMessage message = assistant("");
        message.setToolCalls(toolCalls);
        return message;
    }

    public static ModelMessage assistantToolCalls(String content,
                                                  String reasoningContent,
                                                  List<ModelToolCall> toolCalls) {
        ModelMessage message = assistant(content, reasoningContent);
        message.setToolCalls(toolCalls);
        return message;
    }

    public static ModelMessage toolResult(String toolCallId, String toolName, String content) {
        ModelMessage message = new ModelMessage(ModelMessageRole.TOOL, content);
        message.setToolCallId(toolCallId);
        message.setToolName(toolName);
        return message;
    }
}
