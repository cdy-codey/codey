package com.codey.task;

import com.codey.config.AgentSession;

import java.util.List;

/**
 * 负责把当前轮任务输入合并回运行时会话，避免 TaskRunner 继续膨胀。
 */
public class ChatTurnSessionUpdater {

    public void mergeIntoSession(AgentSession session, GenerateTask task) {
        if (session == null || task == null) {
            return;
        }
        mergeGoal(session, task.getGoal());
        if (!isBlank(task.getWorkingDirectory())) {
            session.setWorkingDirectory(task.getWorkingDirectory());
        }
        if (!isBlank(task.getPagePath())) {
            session.setTargetPagePath(task.getPagePath());
            // 当前页面路径属于运行时上下文，进入 system context。
            session.appendContextFile(task.getPagePath());
        }
        if (!isBlank(task.getApiSpecPath())) {
            session.setApiSpecPath(task.getApiSpecPath());
            session.appendContextFile(task.getApiSpecPath());
        }
        session.appendIdentities(task.getIdentities());
        appendDistinctContextFiles(session, task.getContextFiles());
        appendDistinctContextNotes(session, task.getContextNotes());
    }

    /**
     * 待用户从候选项中选择时，优先把简短答案解析为真实目标。
     */
    private void mergeGoal(AgentSession session, String goal) {
        if (isBlank(goal)) {
            return;
        }
        String rawGoal = goal;
        String normalized = rawGoal.trim();
        if (session.hasPendingChoice() && isChoiceAnswer(normalized)) {
            String resolved = session.resolvePendingChoice(normalized);
            if (!isBlank(resolved)) {
                session.appendInteraction("用户选择: " + normalized + " => " + resolved);
                session.setUserGoal(resolved);
                return;
            }
        }
        session.setUserGoal(rawGoal);
    }

    private void appendDistinctContextFiles(AgentSession session, List<String> additions) {
        if (session == null || additions == null) {
            return;
        }
        for (String item : additions) {
            if (!isBlank(item)) {
                session.appendContextFile(item);
            }
        }
    }

    private void appendDistinctContextNotes(AgentSession session, List<String> additions) {
        if (session == null || additions == null) {
            return;
        }
        for (String item : additions) {
            if (!isBlank(item)) {
                session.appendContextNote(item);
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isChoiceAnswer(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.length() == 1) {
            char c = Character.toUpperCase(normalized.charAt(0));
            return (c >= 'A' && c <= 'D') || (c >= '0' && c <= '9');
        }
        return normalized.length() == 2 && normalized.matches("^\\d{1,2}$");
    }
}
