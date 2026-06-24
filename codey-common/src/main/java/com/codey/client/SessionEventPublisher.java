package com.codey.client;

/**
 * 会话事件发布器。
 * starter 默认会把内核事件通过该接口抛出，业务方可替换默认实现。
 */
public interface SessionEventPublisher {
    void publish(SessionEvent event);
}
