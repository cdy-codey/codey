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
        session.setWorkingDirectory(task.getWorkingDirectory());
        session.setTargetPagePath(task.getPagePath());
        session.setApiSpecPath(task.getApiSpecPath());
        session.setUserGoal(task.getGoal());
        session.setIdentities(task.getIdentities());
        // 页面/API/上下文文件属于稳定运行环境，进入 system context，不伪装成用户输入。
        session.appendContextFile(task.getPagePath());
        session.appendContextFile(task.getApiSpecPath());
        for (String contextFile : task.getContextFiles()) {
            session.appendContextFile(contextFile);
        }
        for (String contextNote : task.getContextNotes()) {
            session.appendContextNote(contextNote);
        }
        for (String chatHistoryEntry : task.getChatHistory()) {
            session.appendChatHistory(chatHistoryEntry);
        }
        return session;
    }
}
