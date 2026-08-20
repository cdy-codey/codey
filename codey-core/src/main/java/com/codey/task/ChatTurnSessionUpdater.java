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
        // 用户重新发起一条新消息时，本轮应从干净的循环态开始，
        // 避免继承上一轮因为 stagnation / replan 退出时留下的短期执行状态。
        // 注意：transcript 跨轮保留，这里只清理摘要型运行态，不丢上一轮思考轨迹。
        session.resetForNextTurn();
        String rawGoal = goal;
        String normalized = rawGoal.trim();
        if (session.hasPendingChoice() && session.matchesPendingChoice(normalized)) {
            String resolved = session.resolvePendingChoice(normalized);
            if (!isBlank(resolved)) {
                session.appendInteraction("用户选择: " + normalized + " => " + resolved);
                // 目标统一 trim 后存储，与 transcript 文本保持一致，
                // 避免 PromptContractValidator 的子串匹配因前后空白而失败。
                session.setUserGoal(resolved.trim());
                // 用户输入进入 transcript，作为跨轮完整时间线的一部分。
                session.appendUserMessage(resolved);
                return;
            }
        }
        // 只存 trim 后的目标；transcript 追加与渲染视图都会 trim，
        // 未 trim 的原文会导致校验时 prompt.contains(goal) 找不到目标。
        session.setUserGoal(normalized);
        session.appendUserMessage(rawGoal);
    }

    private void appendDistinctContextFiles(AgentSession session, List<String> additions) {
        if (session == null || additions == null) {
            return;
        }
        for (String item : additions) {
            if (!isBlank(item)) {
                // 当前轮前端补充的文件保持“用户原始输入”语义，避免被工具执行路径覆盖。
                session.appendUserContextFile(item);
            }
        }
    }

    private void appendDistinctContextNotes(AgentSession session, List<String> additions) {
        if (session == null || additions == null) {
            return;
        }
        for (String item : additions) {
            if (!isBlank(item)) {
                session.appendUserContextNote(item);
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
