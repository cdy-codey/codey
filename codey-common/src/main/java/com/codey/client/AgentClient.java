package com.codey.client;

/**
 * 对外统一 Agent 调用入口。
 * 单次任务和会话式交互都通过同一套 DTO 表达。
 */
public interface AgentClient {
    RunResult run(RunRequest request);

    ChatSession openSession(RunRequest request);

    RunResult runTurn(String sessionId, RunRequest request);

    /**
     * 只负责把消息提交到会话执行队列，具体结果通过事件流异步返回。
     */
    void submitTurn(String sessionId, RunRequest request);

    void closeSession(String sessionId);

    default String run(String goal) {
        RunResult result = run(RunRequest.ofGoal(goal));
        if (result.isSuccess()) {
            return result.getSummary();
        }
        throw new IllegalStateException(result.getErrorMessage());
    }
}
