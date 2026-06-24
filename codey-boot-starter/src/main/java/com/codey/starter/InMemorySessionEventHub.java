package com.codey.starter;

import com.codey.client.SessionEvent;
import com.codey.client.SessionEventHub;
import com.codey.client.SessionEventListener;
import com.codey.client.SessionEventSubscription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于内存的会话事件中心。
 * 该实现按 sessionId 保存有限长度的事件缓存，并向订阅者广播后续事件。
 */
class InMemorySessionEventHub implements SessionEventHub, SessionEventListener {
    private static final int DEFAULT_BUFFER_SIZE = 200;

    private final int bufferSize;
    private final Map<String, CopyOnWriteArrayList<SessionEventListener>> subscribers =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<SessionEventListener>>();
    private final Map<String, Deque<SessionEvent>> recentEvents =
            new ConcurrentHashMap<String, Deque<SessionEvent>>();

    InMemorySessionEventHub() {
        this(DEFAULT_BUFFER_SIZE);
    }

    InMemorySessionEventHub(int bufferSize) {
        this.bufferSize = Math.max(1, bufferSize);
    }

    @Override
    public void onEvent(SessionEvent event) {
        if (event == null || isBlank(event.getSessionId())) {
            return;
        }
        remember(event);
        CopyOnWriteArrayList<SessionEventListener> listeners = subscribers.get(event.getSessionId());
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (SessionEventListener listener : listeners) {
            if (listener == null) {
                continue;
            }
            listener.onEvent(event);
        }
    }

    @Override
    public SessionEventSubscription subscribe(String sessionId, SessionEventListener listener) {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener 不能为空");
        }
        CopyOnWriteArrayList<SessionEventListener> listeners = subscribers.get(sessionId);
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<SessionEventListener>();
            CopyOnWriteArrayList<SessionEventListener> existing = subscribers.putIfAbsent(sessionId, listeners);
            if (existing != null) {
                listeners = existing;
            }
        }
        listeners.add(listener);
        final CopyOnWriteArrayList<SessionEventListener> target = listeners;
        final String targetSessionId = sessionId;
        return new SessionEventSubscription() {
            @Override
            public void close() {
                target.remove(listener);
                if (target.isEmpty()) {
                    subscribers.remove(targetSessionId, target);
                }
            }
        };
    }

    @Override
    public List<SessionEvent> snapshot(String sessionId) {
        if (isBlank(sessionId)) {
            return Collections.emptyList();
        }
        Deque<SessionEvent> deque = recentEvents.get(sessionId);
        if (deque == null || deque.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (deque) {
            return Collections.unmodifiableList(new ArrayList<SessionEvent>(deque));
        }
    }

    @Override
    public void clear(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        recentEvents.remove(sessionId);
        subscribers.remove(sessionId);
    }

    private void remember(SessionEvent event) {
        Deque<SessionEvent> deque = recentEvents.get(event.getSessionId());
        if (deque == null) {
            deque = new ArrayDeque<SessionEvent>();
            Deque<SessionEvent> existing = recentEvents.putIfAbsent(event.getSessionId(), deque);
            if (existing != null) {
                deque = existing;
            }
        }
        synchronized (deque) {
            deque.addLast(event);
            while (deque.size() > bufferSize) {
                deque.removeFirst();
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
