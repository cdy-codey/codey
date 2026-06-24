package com.codey.console.chat;

import com.codey.client.RunRequest;
import com.codey.client.RunResult;

/**
 * 命令行聊天任务执行器的抽象接口。
 */
public interface ChatTaskExecutor {
    RunResult run(RunRequest request);

    default void reset() {
    }
}
