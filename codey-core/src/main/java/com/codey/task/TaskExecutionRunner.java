package com.codey.task;

import com.codey.loop.LoopOrchestrator;
import com.codey.config.AgentSession;
import com.codey.config.SessionFactory;
import com.codey.skill.Skill;
import com.codey.skill.SkillSelector;

/**
 * 负责执行单次任务，把技能选择、会话创建和主循环调用串起来。
 */
public class TaskExecutionRunner {
    private final SkillSelector skillSelector;
    private final SessionFactory sessionFactory;
    private final LoopOrchestrator loopOrchestrator;

    public TaskExecutionRunner(SkillSelector skillSelector,
                               SessionFactory sessionFactory,
                               LoopOrchestrator loopOrchestrator) {
        this.skillSelector = skillSelector;
        this.sessionFactory = sessionFactory;
        this.loopOrchestrator = loopOrchestrator;
    }

    public TaskResult run(GenerateTask task) {
        Skill skill = skillSelector.select(task).orElse(null);
        AgentSession session = sessionFactory.create(task, skill);
        return loopOrchestrator.run(session, skill == null ? null : skill.definition());
    }
}
