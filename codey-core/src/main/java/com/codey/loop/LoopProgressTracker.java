package com.codey.loop;

import com.codey.config.AgentSession;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 记录循环内的进度快照，用于停滞判定和失败消息输出。
 * 最大循环次数表示“距离上次有效进展之后还能继续尝试多少轮”，而不是整轮对话的绝对死计数。
 */
final class LoopProgressTracker {
    private final int stagnationRoundsThreshold;
    private final int maxLoopCount;
    private int loop;
    private int progressLoop;
    private int toolResultsSize;
    private int editResultsSize;
    private int transcriptSize;

    LoopProgressTracker(AgentSession session, int maxLoopCount, int stagnationRoundsThreshold) {
        this.maxLoopCount = maxLoopCount;
        this.stagnationRoundsThreshold = stagnationRoundsThreshold;
        this.toolResultsSize = countToolResults(session);
        this.editResultsSize = countEditResults(session);
        this.transcriptSize = countTranscript(session);
    }

    int nextLoop() {
        loop++;
        return loop;
    }

    boolean isStagnated() {
        return loop - progressLoop >= stagnationRoundsThreshold;
    }

    boolean hasReachedMaxLoopCount() {
        return loop - progressLoop >= maxLoopCount;
    }

    SessionProgressSnapshot snapshot(AgentSession session) {
        return new SessionProgressSnapshot(
                countToolResults(session),
                countEditResults(session),
                countTranscript(session)
        );
    }

    void markProgress(int currentLoop, SessionProgressSnapshot before, SessionProgressSnapshot after) {
        if (before == null || after == null) {
            return;
        }
        if (!after.hasProgressComparedTo(before)) {
            return;
        }
        progressLoop = currentLoop;
        toolResultsSize = after.getToolResultsSize();
        editResultsSize = after.getEditResultsSize();
        transcriptSize = after.getTranscriptSize();
    }

    String buildHaltMessage(AgentSession session) {
        String lastFeedback = "";
        if (session != null && !session.getSystemFeedback().isEmpty()) {
            lastFeedback = session.getSystemFeedback().get(session.getSystemFeedback().size() - 1);
        }
        int loopsSinceProgress = Math.max(0, loop - progressLoop);
        String reason = hasReachedMaxLoopCount()
                ? "reached maxLoopCount since last progress"
                : "detected stagnation";
        String message = "Loop halted: " + reason + ". "
                + "maxLoopCount=" + maxLoopCount
                + ", loop=" + loop
                + ", progressLoop=" + progressLoop
                + ", loopsSinceProgress=" + loopsSinceProgress
                + ", toolResults=" + toolResultsSize
                + ", editResults=" + editResultsSize
                + ", transcript=" + transcriptSize;
        if (!isBlank(lastFeedback)) {
            message = message + ". lastFeedback=" + lastFeedback;
        }
        // #region debug-point B:loop-halt
        debugReport(
                "pre-fix",
                "B",
                "LoopProgressTracker.buildHaltMessage",
                "[DEBUG] 循环因停滞被终止",
                "{"
                        + "\"sessionId\":\"" + escapeDebug(session == null ? null : session.getSessionId()) + "\","
                        + "\"maxLoopCount\":\"" + maxLoopCount + "\","
                        + "\"loop\":\"" + loop + "\","
                        + "\"progressLoop\":\"" + progressLoop + "\","
                        + "\"loopsSinceProgress\":\"" + loopsSinceProgress + "\","
                        + "\"toolResults\":\"" + toolResultsSize + "\","
                        + "\"editResults\":\"" + editResultsSize + "\","
                        + "\"transcript\":\"" + transcriptSize + "\","
                        + "\"lastFeedback\":\"" + escapeDebug(lastFeedback) + "\""
                        + "}",
                session == null ? null : session.getSessionId()
        );
        // #endregion
        return message;
    }

    private int countToolResults(AgentSession session) {
        return session == null ? 0 : session.getToolResults().size();
    }

    private int countEditResults(AgentSession session) {
        return session == null ? 0 : session.getEditResults().size();
    }

    private int countTranscript(AgentSession session) {
        return session == null ? 0 : session.getModelTranscript().size();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // #region debug-point B:loop-halt
    private void debugReport(String runId, String hypothesisId, String location, String msg, String dataJson, String traceId) {
        try {
            String serverUrl = "http://127.0.0.1:7777/event";
            String sessionId = "db-model-stagnation";
            Path envPath = Paths.get(".dbg", "db-model-stagnation.env");
            if (Files.exists(envPath)) {
                List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.startsWith("DEBUG_SERVER_URL=")) {
                        serverUrl = line.substring("DEBUG_SERVER_URL=".length()).trim();
                    } else if (line.startsWith("DEBUG_SESSION_ID=")) {
                        sessionId = line.substring("DEBUG_SESSION_ID=".length()).trim();
                    }
                }
            }
            String payload = "{"
                    + "\"sessionId\":\"" + escapeDebug(sessionId) + "\","
                    + "\"runId\":\"" + escapeDebug(runId) + "\","
                    + "\"hypothesisId\":\"" + escapeDebug(hypothesisId) + "\","
                    + "\"location\":\"" + escapeDebug(location) + "\","
                    + "\"msg\":\"" + escapeDebug(msg) + "\","
                    + "\"traceId\":\"" + escapeDebug(traceId) + "\","
                    + "\"data\":" + (dataJson == null ? "{}" : dataJson)
                    + "}";
            HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(body);
                outputStream.flush();
            } finally {
                outputStream.close();
            }
            connection.getInputStream().close();
        } catch (Exception ignored) {
        }
    }

    private String escapeDebug(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
    // #endregion

    static final class SessionProgressSnapshot {
        private final int toolResultsSize;
        private final int editResultsSize;
        private final int transcriptSize;

        SessionProgressSnapshot(int toolResultsSize, int editResultsSize, int transcriptSize) {
            this.toolResultsSize = toolResultsSize;
            this.editResultsSize = editResultsSize;
            this.transcriptSize = transcriptSize;
        }

        boolean hasProgressComparedTo(SessionProgressSnapshot before) {
            if (before == null) {
                return true;
            }
            return toolResultsSize > before.toolResultsSize
                    || editResultsSize > before.editResultsSize
                    || transcriptSize > before.transcriptSize;
        }

        int getToolResultsSize() {
            return toolResultsSize;
        }

        int getEditResultsSize() {
            return editResultsSize;
        }

        int getTranscriptSize() {
            return transcriptSize;
        }
    }
}
