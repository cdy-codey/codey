package com.codey.config;

import com.codey.infra.ModelMessage;
import com.codey.infra.ModelToolCall;

import java.util.List;
import java.util.UUID;

/**
 * 代理运行时会话。
 * 对外保持兼容接口，内部按上下文、对话、执行、循环记忆四类状态拆分。
 */
public class AgentSession {
    private static final int MAX_CHAT_HISTORY = 20;
    private static final int MAX_MODEL_TRANSCRIPT = 24;

    private final String sessionId;
    private final SessionContextState sessionContext = new SessionContextState();
    private final ConversationState conversationState = new ConversationState(MAX_CHAT_HISTORY);
    private final ExecutionState executionState = new ExecutionState(MAX_MODEL_TRANSCRIPT);
    private final LoopMemoryState loopMemoryState = new LoopMemoryState();

    public AgentSession() {
        this(null);
    }

    public AgentSession(String sessionId) {
        this.sessionId = isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId.trim();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSkillName() {
        return sessionContext.getSkillName();
    }

    public void setSkillName(String skillName) {
        sessionContext.setSkillName(skillName);
    }

    public String getWorkingDirectory() {
        return sessionContext.getWorkingDirectory();
    }

    public void setWorkingDirectory(String workingDirectory) {
        sessionContext.setWorkingDirectory(workingDirectory);
    }

    public String getTargetPagePath() {
        return sessionContext.getTargetPagePath();
    }

    public void setTargetPagePath(String targetPagePath) {
        sessionContext.setTargetPagePath(targetPagePath);
    }

    public String getApiSpecPath() {
        return sessionContext.getApiSpecPath();
    }

    public void setApiSpecPath(String apiSpecPath) {
        sessionContext.setApiSpecPath(apiSpecPath);
    }

    public String getUserGoal() {
        return sessionContext.getUserGoal();
    }

    public void setUserGoal(String userGoal) {
        sessionContext.setUserGoal(userGoal);
    }

    public String getLastEditedFilePath() {
        return sessionContext.getLastEditedFilePath();
    }

    public void setLastEditedFilePath(String lastEditedFilePath) {
        sessionContext.setLastEditedFilePath(lastEditedFilePath);
    }

    public List<String> getContextFiles() {
        return sessionContext.getContextFiles();
    }

    public List<String> getContextNotes() {
        return sessionContext.getContextNotes();
    }

    public List<String> getUserContextFiles() {
        return sessionContext.getUserContextFiles();
    }

    public List<String> getUserContextNotes() {
        return sessionContext.getUserContextNotes();
    }

    public List<String> getIdentities() {
        return sessionContext.getIdentities();
    }

    public List<String> getChatHistory() {
        return conversationState.getChatHistory();
    }

    public List<String> getToolResults() {
        return executionState.getToolResults();
    }

    public List<String> getEditResults() {
        return executionState.getEditResults();
    }

    public List<String> getSystemFeedback() {
        return executionState.getSystemFeedback();
    }

    public List<String> getInteractionHistory() {
        return conversationState.getInteractionHistory();
    }

    public List<ModelMessage> getModelTranscript() {
        return executionState.getModelTranscript();
    }

    public boolean hasExecutedToolRequest(String requestSignature) {
        return loopMemoryState.hasExecutedToolRequest(requestSignature);
    }

    public void rememberToolRequest(String requestSignature) {
        loopMemoryState.rememberToolRequest(requestSignature);
    }

    public boolean hasSuccessfulToolRequest(String requestSignature) {
        return loopMemoryState.hasSuccessfulToolRequest(requestSignature);
    }

    public void rememberSuccessfulToolRequest(String requestSignature) {
        loopMemoryState.rememberSuccessfulToolRequest(requestSignature);
    }

    public int recordDuplicateSuccessfulToolRequest(String requestSignature) {
        return loopMemoryState.recordDuplicateSuccessfulToolRequest(requestSignature);
    }

    public void recordToolFailure(String requestSignature) {
        loopMemoryState.recordToolFailure(requestSignature);
    }

    public int getToolRequestFailureCount(String requestSignature) {
        return loopMemoryState.getToolRequestFailureCount(requestSignature);
    }

    public int getConsecutiveToolFailureCount() {
        return loopMemoryState.getConsecutiveToolFailureCount();
    }

    public void resetConsecutiveToolFailureCount() {
        loopMemoryState.resetConsecutiveToolFailureCount();
    }

    public boolean isReplanMode() {
        return loopMemoryState.isReplanMode();
    }

    public String getReplanReason() {
        return loopMemoryState.getReplanReason();
    }

    public void enterReplanMode(String reason) {
        loopMemoryState.enterReplanMode(reason);
    }

    public void clearReplanMode() {
        loopMemoryState.clearReplanMode();
    }

    /**
     * 编辑成功后只失效循环记忆，不影响用户上下文和对话状态。
     */
    public void invalidateToolRequestMemoryAfterEdit() {
        loopMemoryState.invalidateToolRequestMemoryAfterEdit();
    }

    public boolean isReadFileRangeCovered(String path, Integer offset, Integer limit) {
        return loopMemoryState.isReadFileRangeCovered(path, offset, limit);
    }

    public void rememberReadFileRange(String path, Integer offset, Integer limit) {
        loopMemoryState.rememberReadFileRange(path, offset, limit);
    }

    public boolean hasReadFileRanges(String path) {
        return loopMemoryState.hasReadFileRanges(path);
    }

    public void rememberReadFileSnippet(String path, Integer offset, Integer limit, String snippet) {
        loopMemoryState.rememberReadFileSnippet(path, offset, limit, snippet);
    }

    public String getReadFileSnippetForRange(String path, Integer offset, Integer limit) {
        return loopMemoryState.getReadFileSnippetForRange(path, offset, limit);
    }

    public void appendToolResult(String content) {
        executionState.appendToolResult(content);
    }

    public void appendEditResult(String content) {
        executionState.appendEditResult(content);
    }

    public void appendSystemFeedback(String content) {
        executionState.appendSystemFeedback(content);
    }

    public void appendContextFile(String path) {
        sessionContext.appendContextFile(path);
    }

    public void appendUserContextFile(String path) {
        sessionContext.appendUserContextFile(path);
    }

    public void appendContextNote(String note) {
        sessionContext.appendContextNote(note);
    }

    public void appendUserContextNote(String note) {
        sessionContext.appendUserContextNote(note);
    }

    public void setIdentities(List<String> identities) {
        sessionContext.replaceIdentities(identities);
    }

    public void appendIdentities(List<String> identities) {
        sessionContext.appendIdentities(identities);
    }

    public void appendChatHistory(String content) {
        conversationState.appendChatHistory(content);
    }

    public void recordChatTurn(String userMessage, String assistantMessage) {
        conversationState.recordChatTurn(userMessage, assistantMessage);
    }

    public String getLatestAssistantResponseContent() {
        return executionState.getLatestAssistantResponseContent();
    }

    public boolean hasPendingChoice() {
        return conversationState.hasPendingChoice();
    }

    public String resolvePendingChoice(String rawUserInput) {
        return conversationState.resolvePendingChoice(rawUserInput);
    }

    public void clearPendingChoice() {
        conversationState.clearPendingChoice();
    }

    public void appendInteraction(String content) {
        conversationState.appendInteraction(content);
    }

    public void appendAssistantToolCalls(List<ModelToolCall> toolCalls) {
        executionState.appendAssistantToolCalls(toolCalls);
    }

    public void appendAssistantToolCalls(String content, String reasoningContent, List<ModelToolCall> toolCalls) {
        executionState.appendAssistantToolCalls(content, reasoningContent, toolCalls);
    }

    public void appendAssistantMessage(String content, String reasoningContent) {
        executionState.appendAssistantMessage(content, reasoningContent);
    }

    public void appendToolResultMessage(String toolCallId, String toolName, String content) {
        executionState.appendToolResultMessage(toolCallId, toolName, content);
    }

    /**
     * 当前 chat turn 结束后清空只对本轮生效的运行态，下一轮只保留稳定摘要和用户上下文。
     */
    public void resetForNextTurn() {
        executionState.resetForNextTurn();
        loopMemoryState.resetForNextTurn();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
