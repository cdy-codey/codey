package com.codey.web.api;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 记录前端会话级显示偏好。
 * 这里只影响 Web Demo 对浏览器返回的内容，不干预 core 内部推理链路。
 */
@Component
public class ChatSessionDisplayOptionsStore {
    private final ConcurrentMap<String, Boolean> includeThinkingBySession = new ConcurrentHashMap<String, Boolean>();

    public void saveIncludeThinking(String sessionId, boolean includeThinking) {
        if (isBlank(sessionId)) {
            return;
        }
        includeThinkingBySession.put(sessionId, Boolean.valueOf(includeThinking));
    }

    public boolean shouldIncludeThinking(String sessionId) {
        Boolean includeThinking = isBlank(sessionId) ? null : includeThinkingBySession.get(sessionId);
        return includeThinking == null || includeThinking.booleanValue();
    }

    public void clear(String sessionId) {
        if (isBlank(sessionId)) {
            return;
        }
        includeThinkingBySession.remove(sessionId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
