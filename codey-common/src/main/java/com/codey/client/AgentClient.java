package com.codey.client;

/**
 * 对外统一 Agent 调用入口。
 * 单次任务和会话式交互都通过同一套 DTO 表达。
 */
public interface AgentClient {
    RunResult run(RunRequest request);

    ChatSession openSession(RunRequest request);

    RunResult runTurn(String sessionId, RunRequest request);

    void closeSession(String sessionId);

    default String run(String goal) {
        RunResult result = run(RunRequest.ofGoal(goal));
        if (result.isSuccess()) {
            return result.getSummary();
        }
        throw new IllegalStateException(result.getErrorMessage());
    }
}
