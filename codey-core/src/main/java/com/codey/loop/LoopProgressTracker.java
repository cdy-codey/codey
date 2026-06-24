package com.codey.loop;

import com.codey.config.AgentSession;

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
