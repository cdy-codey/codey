package com.codey.task;

import com.codey.config.AgentSession;
import com.codey.config.SessionFactory;
import com.codey.skill.Skill;
import com.codey.skill.SkillSelector;

/**
 * 负责选择技能并创建 chat 会话句柄。
 */
public class ChatSessionHandleFactory {
    private final SkillSelector skillSelector;
    private final SessionFactory sessionFactory;

    public ChatSessionHandleFactory(SkillSelector skillSelector, SessionFactory sessionFactory) {
        this.skillSelector = skillSelector;
        this.sessionFactory = sessionFactory;
    }

    public TaskRunner.ChatSessionHandle create(GenerateTask task) {
        Skill skill = skillSelector.select(task).orElse(null);
        AgentSession session = sessionFactory.create(task, skill);
        return new TaskRunner.ChatSessionHandle(skill, session);
    }
}
