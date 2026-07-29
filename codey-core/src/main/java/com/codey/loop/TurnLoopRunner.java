package com.codey.loop;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.codey.task.TaskResult;

/**
 * 负责推进整段 loop 回环，管理停滞判定和轮次推进。
 */
final class TurnLoopRunner {
    private final LoopTurnEngine loopTurnEngine;
    private final int stagnationRoundsThreshold;

    TurnLoopRunner(LoopTurnEngine loopTurnEngine, int stagnationRoundsThreshold) {
        this.loopTurnEngine = loopTurnEngine;
        this.stagnationRoundsThreshold = stagnationRoundsThreshold;
    }

    TaskResult run(AgentSession session, SkillDefinition skill) {
        int configuredMaxLoopCount = skill == null ? 8 : skill.getMaxLoopCount();
        int maxLoopCount = Math.max(1, configuredMaxLoopCount);
        LoopProgressTracker progressTracker = new LoopProgressTracker(session, maxLoopCount, stagnationRoundsThreshold);
        int callSequence = 1; // 跨 loop 的模型调用全局序号
        while (!progressTracker.hasReachedMaxLoopCount()) {
            int currentLoop = progressTracker.nextLoop();
            if (progressTracker.isStagnated()) {
                break;
            }
            LoopTurnEngine.TurnExecutionResult turnResult =
                    loopTurnEngine.executeTurn(session, skill, currentLoop, callSequence, progressTracker);
            if (turnResult.isFinished()) {
                return turnResult.getTaskResult();
            }
            callSequence = turnResult.getNextCallSequence();
        }
        return TaskResult.failed(session.getSessionId(), progressTracker.buildHaltMessage(session));
    }
}
