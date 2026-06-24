package com.codey.starter;

import com.codey.client.SessionEvent;
import com.codey.client.SessionEventListener;
import com.codey.client.SessionEventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * starter 默认事件发布器。
 * 它只负责把统一事件依次分发给已注册的监听器 Bean。
 */
class DefaultSessionEventPublisher implements SessionEventPublisher {
    private final List<SessionEventListener> listeners;

    DefaultSessionEventPublisher(List<SessionEventListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            this.listeners = Collections.emptyList();
            return;
        }
        this.listeners = Collections.unmodifiableList(new ArrayList<SessionEventListener>(listeners));
    }

    @Override
    public void publish(SessionEvent event) {
        if (event == null || listeners.isEmpty()) {
            return;
        }
        for (SessionEventListener listener : listeners) {
            if (listener == null) {
                continue;
            }
            listener.onEvent(event);
        }
    }
}
