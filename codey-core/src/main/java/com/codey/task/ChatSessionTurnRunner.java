package com.codey.task;

import com.codey.loop.LoopOrchestrator;

/**
 * 负责执行单轮 chat turn，把会话更新、主循环调用和历史回写串起来。
 */
public class ChatSessionTurnRunner {
    private final ChatTurnSessionUpdater chatTurnSessionUpdater;
    private final LoopOrchestrator loopOrchestrator;
    private final ChatHistoryRecorder chatHistoryRecorder;

    public ChatSessionTurnRunner(ChatTurnSessionUpdater chatTurnSessionUpdater,
                                 LoopOrchestrator loopOrchestrator,
                                 ChatHistoryRecorder chatHistoryRecorder) {
        this.chatTurnSessionUpdater = chatTurnSessionUpdater;
        this.loopOrchestrator = loopOrchestrator;
        this.chatHistoryRecorder = chatHistoryRecorder;
    }

    public TaskResult runTurn(TaskRunner.ChatSessionHandle handle, GenerateTask task) {
        if (handle == null) {
            throw new IllegalArgumentException("Chat session handle must not be null");
        }
        chatTurnSessionUpdater.mergeIntoSession(handle.getSession(), task);
        TaskResult result = loopOrchestrator.run(
                handle.getSession(),
                handle.getSkill() == null ? null : handle.getSkill().definition()
        );
        chatHistoryRecorder.recordTurn(handle.getSession(), task, result);
        return result;
    }
}
