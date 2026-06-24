package com.codey.starter;

import com.codey.session.SessionStore;
import com.codey.client.SessionEvent;
import com.codey.client.SessionEventPublisher;

/**
 * 仅负责向外发布统一会话事件的 SessionStore 适配器。
 */
class EventPublishingSessionStore implements SessionStore {
    private final SessionEventPublisher publisher;

    EventPublishingSessionStore(SessionEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void appendEvent(SessionEvent event) {
        if (publisher == null || event == null) {
            return;
        }
        publisher.publish(event);
    }
}
