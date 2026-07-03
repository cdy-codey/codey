package com.codey.session;

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
 * 为独立事件目录提供统一的格式化落盘能力。
 */
abstract class AbstractStructuredEventStore implements SessionStore {
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final Path rootDir;
    private final ObjectMapper objectMapper;
    private final Map<String, Integer> sequenceBySession = new HashMap<String, Integer>();

    protected AbstractStructuredEventStore(Path rootDir) {
        this.rootDir = rootDir;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    protected void writeEvent(String sessionId, String eventType, Object payload) {
        try {
            Path sessionDir = rootDir.resolve(sessionId);
            Files.createDirectories(sessionDir);
            int sequence = nextSequence(sessionId);
            String fileName = String.format("%04d-%s.json", Integer.valueOf(sequence), sanitizeFileName(eventType));
            Path file = sessionDir.resolve(fileName);

            Map<String, Object> event = new LinkedHashMap<String, Object>();
            event.put("timestamp", formatNow());
            event.put("sessionId", sessionId);
            event.put("eventType", eventType);
            event.put("payload", payload);

            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event)
                    + System.lineSeparator();
            Files.write(file, content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write structured event log", exception);
        }
    }

    // 结构化独立日志统一输出 Date 风格时间字符串，保持全项目时间口径一致。
    private String formatNow() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_PATTERN);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return dateFormat.format(new Date());
    }

    private int nextSequence(String sessionId) {
        Integer current = sequenceBySession.get(sessionId);
        int next = current == null ? 1 : current.intValue() + 1;
        sequenceBySession.put(sessionId, Integer.valueOf(next));
        return next;
    }

    private String sanitizeFileName(String value) {
        return value == null ? "event" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
