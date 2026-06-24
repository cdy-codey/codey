package com.codey.infra;

/**
 * 接收模型流式文本、思考内容和工具调用事件的监听器。
 */
public interface ModelStreamListener {
    void onTextDelta(String delta);

    default void onThinkingDelta(String delta) {
    }

    default void onToolCallStarted(ModelToolCall toolCall) {
    }
}
