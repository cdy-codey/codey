package com.codey.task;

import com.codey.loop.LoopOrchestrator;
import com.codey.config.AgentSession;
import com.codey.config.SessionFactory;
import com.codey.skill.Skill;
import com.codey.skill.SkillSelector;

/**
 * 任务总入口。
 */
public class TaskRunner {
    private final TaskExecutionRunner taskExecutionRunner;
    private final ChatSessionHandleFactory chatSessionHandleFactory;
    private final ChatSessionTurnRunner chatSessionTurnRunner;

    public TaskRunner(SkillSelector skillSelector,
                      SessionFactory sessionFactory,
                      LoopOrchestrator loopOrchestrator) {
        this(
                new TaskExecutionRunner(skillSelector, sessionFactory, loopOrchestrator),
                new ChatSessionHandleFactory(skillSelector, sessionFactory),
                new ChatSessionTurnRunner(
                        new ChatTurnSessionUpdater(),
                        loopOrchestrator,
                        new ChatHistoryRecorder()
                )
        );
    }

    public TaskRunner(TaskExecutionRunner taskExecutionRunner,
                      ChatSessionHandleFactory chatSessionHandleFactory,
                      ChatSessionTurnRunner chatSessionTurnRunner) {
        this.taskExecutionRunner = taskExecutionRunner;
        this.chatSessionHandleFactory = chatSessionHandleFactory;
        this.chatSessionTurnRunner = chatSessionTurnRunner;
    }

    public TaskResult run(GenerateTask task) {
        return taskExecutionRunner.run(task);
    }

    public ChatSessionHandle openChatSession(GenerateTask task) {
        return chatSessionHandleFactory.create(task);
    }

    public TaskResult runChatTurn(ChatSessionHandle handle, GenerateTask task) {
        return chatSessionTurnRunner.runTurn(handle, task);
    }

    public static final class ChatSessionHandle {
        private final Skill skill;
        private final AgentSession session;

        ChatSessionHandle(Skill skill, AgentSession session) {
            this.skill = skill;
            this.session = session;
        }

        public String getSessionId() {
            return session == null ? null : session.getSessionId();
        }

        Skill getSkill() {
            return skill;
        }

        AgentSession getSession() {
            return session;
        }
    }
}
