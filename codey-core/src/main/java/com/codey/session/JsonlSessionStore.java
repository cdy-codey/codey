package com.codey.session;

import com.codey.client.SessionEvent;
import com.codey.client.SessionEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * 基于 json 的会话日志存储。
 * 为了方便人工分析和查看，事件会先格式化后再写入文件。
 * 为了避免流式思考按单字落盘，这里会先做短缓冲，再按句子或长度聚合写入。
 */
public class JsonlSessionStore implements SessionStore, SessionDirectoryAware {
    private static final int THINKING_FLUSH_THRESHOLD = 120;
    private static final int MAX_INLINE_STRING_LENGTH = 4096;
    private static final int MAX_COLLECTION_ITEMS = 32;
    private static final int MAX_SANITIZE_DEPTH = 6;
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final Path sessionDir;
    private final ObjectMapper objectMapper;
    private final Map<String, StringBuilder> pendingThinking = new HashMap<String, StringBuilder>();

    public JsonlSessionStore(Path sessionDir) {
        this.sessionDir = sessionDir;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public Path getSessionDirectory() {
        return sessionDir;
    }

    @Override
    public void completeModelText(String sessionId) {
        flushThinking(sessionId, true);
    }

    private void flushThinking(String sessionId, boolean flushAll) {
        StringBuilder buffer = pendingThinking.get(sessionId);
        if (buffer == null || buffer.length() == 0) {
            return;
        }

        int flushLength = resolveThinkingFlushLength(buffer, flushAll);
        if (flushLength <= 0) {
            return;
        }

        String chunk = buffer.substring(0, flushLength);
        buffer.delete(0, flushLength);
        if (buffer.length() == 0) {
            pendingThinking.remove(sessionId);
        }
        // 这里直接落盘聚合后的思考片段，避免再次走 appendEvent 触发递归。
        writeEventLine(SessionEventFactory.modelThinkingDelta(sessionId, chunk));
    }

    private int resolveThinkingFlushLength(StringBuilder buffer, boolean flushAll) {
        if (flushAll) {
            return buffer.length();
        }
        if (buffer.length() < THINKING_FLUSH_THRESHOLD) {
            return 0;
        }

        for (int index = buffer.length() - 1; index >= 0; index--) {
            if (isThinkingBoundary(buffer.charAt(index))) {
                return index + 1;
            }
        }
        return THINKING_FLUSH_THRESHOLD;
    }

    private boolean isThinkingBoundary(char value) {
        return value == '\n'
                || value == '。'
                || value == '！'
                || value == '？'
                || value == '.'
                || value == '!'
                || value == '?'
                || value == ';'
                || value == '；';
    }

    @Override
    public void appendEvent(SessionEvent event) {
        if (event == null) {
            return;
        }
        if (event.getType() == SessionEventType.MODEL_THINKING_DELTA) {
            appendThinkingDelta(event);
            return;
        }
        flushThinking(event.getSessionId(), true);
        if (event.getType() == SessionEventType.MODEL_TEXT_DELTA) {
            return;
        }
        writeEventLine(event);
    }

    private void writeEventLine(SessionEvent event) {
        try {
            Files.createDirectories(sessionDir);
            Path file = sessionDir.resolve(event.getSessionId() + ".jsonl");
            Map<String, Object> eventLine = new LinkedHashMap<String, Object>();
            eventLine.put("timestamp", formatNow());
            eventLine.put("sessionId", event.getSessionId());
            eventLine.put("eventType", event.getType() == null ? null : event.getType().getCode());
            if (!isBlank(event.getStage())) {
                eventLine.put("stage", event.getStage());
            }
            if (!isBlank(event.getMessage())) {
                eventLine.put("message", event.getMessage());
            }
            // 会话日志只保留审计必要信息，避免写工具把整份文件内容同步打到磁盘造成控制台卡顿。
            eventLine.put("payload", sanitizePayload(event.getPayload()));
            String line = objectMapper.writeValueAsString(eventLine) + System.lineSeparator();
            Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write session log", exception);
        }
    }

    // 独立日志统一输出 Date 风格时间字符串，避免项目内继续混用 java.time 时间类型。
    private String formatNow() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_PATTERN);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return dateFormat.format(new Date());
    }

    private void appendThinkingDelta(SessionEvent event) {
        String delta = event.getMessage();
        if (isBlank(delta)) {
            Object payloadDelta = SessionEventFactory.payloadValue(event, "delta");
            delta = payloadDelta == null ? null : String.valueOf(payloadDelta);
        }
        if (isBlank(delta)) {
            return;
        }
        String sessionId = event.getSessionId();
        StringBuilder buffer = pendingThinking.get(sessionId);
        if (buffer == null) {
            buffer = new StringBuilder();
            pendingThinking.put(sessionId, buffer);
        }
        buffer.append(delta);
        flushThinking(sessionId, false);
    }

    private Object sanitizePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        return sanitizeValue(objectMapper.convertValue(payload, Object.class), 0);
    }

    private Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth >= MAX_SANITIZE_DEPTH) {
            return "[depth-limited]";
        }
        if (value instanceof String) {
            return truncateString((String) value);
        }
        if (value instanceof Map<?, ?>) {
            return sanitizeMap((Map<?, ?>) value, depth + 1);
        }
        if (value instanceof Iterable<?>) {
            return sanitizeIterable((Iterable<?>) value, depth + 1);
        }
        return value;
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> value, int depth) {
        Map<String, Object> sanitized = new LinkedHashMap<String, Object>();
        int index = 0;
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (index >= MAX_COLLECTION_ITEMS) {
                sanitized.put("__truncated__", "remaining entries were omitted");
                break;
            }
            sanitized.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue(), depth));
            index++;
        }
        return sanitized;
    }

    private Object sanitizeIterable(Iterable<?> value, int depth) {
        java.util.List<Object> sanitized = new java.util.ArrayList<Object>();
        int index = 0;
        for (Object item : value) {
            if (index >= MAX_COLLECTION_ITEMS) {
                sanitized.add("[truncated]");
                break;
            }
            sanitized.add(sanitizeValue(item, depth));
            index++;
        }
        return sanitized;
    }

    private String truncateString(String value) {
        if (value == null || value.length() <= MAX_INLINE_STRING_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_INLINE_STRING_LENGTH)
                + "...(已截断 "
                + (value.length() - MAX_INLINE_STRING_LENGTH)
                + " 字符)";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
