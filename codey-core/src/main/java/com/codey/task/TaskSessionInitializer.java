package com.codey.task;

import com.codey.config.AgentSession;
import com.codey.skill.Skill;

/**
 * 负责根据任务输入初始化运行时会话，收敛 task -> session 的映射逻辑。
 */
public class TaskSessionInitializer {

    public AgentSession initialize(GenerateTask task, Skill skill) {
        AgentSession session = new AgentSession(task == null ? null : task.getSessionId());
        if (task == null) {
            return session;
        }
        if (skill != null && skill.definition() != null) {
            session.setSkillName(skill.definition().getName());
        }
        session.setModelConfig(task.getModelConfig());
        String wd = task.getWorkingDirectory();
        if (wd == null || wd.trim().isEmpty()) {
            wd = session.getSessionId();
        }
        session.setWorkingDirectory(wd);
        session.setTargetPagePath(task.getPagePath());
        session.setApiSpecPath(task.getApiSpecPath());
        session.setUserGoal(task.getGoal());
        session.setIdentities(task.getIdentities());
        // 页面/API 仍属于运行时上下文，但前端主动传入的文件与说明要单独记为 userContext，
        // 这样后续工具读写不会把脏路径反灌回提示词。
        session.appendContextFile(task.getPagePath());
        session.appendContextFile(task.getApiSpecPath());
        for (String contextFile : task.getContextFiles()) {
            session.appendUserContextFile(contextFile);
        }
        for (String contextNote : task.getContextNotes()) {
            session.appendUserContextNote(contextNote);
        }
        for (String chatHistoryEntry : task.getChatHistory()) {
            session.appendChatHistory(chatHistoryEntry);
        }
        return session;
    }
}
