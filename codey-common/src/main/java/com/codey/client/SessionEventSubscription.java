package com.codey.client;

/**
 * 会话事件订阅句柄。
 * 业务侧在不再需要接收事件时应主动关闭订阅。
 */
public interface SessionEventSubscription extends AutoCloseable {
    @Override
    void close();
}
