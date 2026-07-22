package com.codey.session;

import com.codey.client.SessionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 将同一份会话事件同时转发给多个底层存储实现。
 */
public class CompositeSessionStore implements SessionStore, SessionDirectoryAware {
    private final List<SessionStore> delegates = new ArrayList<SessionStore>();

    public CompositeSessionStore(SessionStore... delegates) {
        if (delegates != null) {
            this.delegates.addAll(Arrays.asList(delegates));
        }
    }

    @Override
    public void appendEvent(SessionEvent event) {
        for (SessionStore delegate : delegates) {
            delegate.appendEvent(event);
        }
    }

    @Override
    public void completeModelText(String sessionId) {
        for (SessionStore delegate : delegates) {
            delegate.completeModelText(sessionId);
        }
    }

    @Override
    public boolean consumeDisplayedFinalSummary(String sessionId) {
        boolean consumed = false;
        for (SessionStore delegate : delegates) {
            if (delegate.consumeDisplayedFinalSummary(sessionId)) {
                consumed = true;
            }
        }
        return consumed;
    }

    @Override
    public java.nio.file.Path getSessionDirectory() {
        for (SessionStore delegate : delegates) {
            if (delegate instanceof SessionDirectoryAware) {
                java.nio.file.Path sessionDirectory = ((SessionDirectoryAware) delegate).getSessionDirectory();
                if (sessionDirectory != null) {
                    return sessionDirectory;
                }
            }
        }
        return null;
    }
}
