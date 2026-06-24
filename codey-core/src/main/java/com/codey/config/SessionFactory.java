package com.codey.config;

import com.codey.skill.Skill;
import com.codey.task.GenerateTask;
import com.codey.task.TaskSessionInitializer;

/**
 * 根据任务输入和技能配置创建 `AgentSession`。
 */
public class SessionFactory {
    private final TaskSessionInitializer taskSessionInitializer;

    public SessionFactory() {
        this(new TaskSessionInitializer());
    }

    public SessionFactory(TaskSessionInitializer taskSessionInitializer) {
        this.taskSessionInitializer = taskSessionInitializer;
    }

    public AgentSession create(GenerateTask task, Skill skill) {
        return taskSessionInitializer.initialize(task, skill);
    }
}
