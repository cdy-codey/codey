package com.codey.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 承载循环去重、防抖、失败计数和读取缓存等短期记忆。
 */
final class LoopMemoryState {
    private final Set<String> successfulToolRequests = new HashSet<String>();
    private final Map<String, Integer> toolRequestFailureCounts = new HashMap<String, Integer>();
    private final Map<String, Integer> duplicateSuccessfulToolRequestCounts = new HashMap<String, Integer>();
    private final Map<String, List<LineRange>> readFileRangesByPath = new HashMap<String, List<LineRange>>();
    private final Map<String, List<SnippetRange>> readFileSnippetsByPath = new HashMap<String, List<SnippetRange>>();
    private int consecutiveToolFailureCount;
    private int lastContextSummaryTriggerChars;
    private boolean replanMode;
    private String replanReason;

    boolean hasExecutedToolRequest(String requestSignature) {
        return hasSuccessfulToolRequest(requestSignature);
    }

    void rememberToolRequest(String requestSignature) {
        rememberSuccessfulToolRequest(requestSignature);
    }

    boolean hasSuccessfulToolRequest(String requestSignature) {
        return requestSignature != null && successfulToolRequests.contains(requestSignature);
    }

    void rememberSuccessfulToolRequest(String requestSignature) {
        if (requestSignature != null && !requestSignature.trim().isEmpty()) {
            successfulToolRequests.add(requestSignature);
            toolRequestFailureCounts.remove(requestSignature);
            duplicateSuccessfulToolRequestCounts.remove(requestSignature);
        }
    }

    int recordDuplicateSuccessfulToolRequest(String requestSignature) {
        if (requestSignature == null || requestSignature.trim().isEmpty()) {
            return 0;
        }
        Integer count = duplicateSuccessfulToolRequestCounts.get(requestSignature);
        int nextCount = count == null ? 1 : count + 1;
        duplicateSuccessfulToolRequestCounts.put(requestSignature, nextCount);
        return nextCount;
    }

    void recordToolFailure(String requestSignature) {
        if (requestSignature != null && !requestSignature.trim().isEmpty()) {
            Integer count = toolRequestFailureCounts.get(requestSignature);
            toolRequestFailureCounts.put(requestSignature, count == null ? 1 : count + 1);
        }
        consecutiveToolFailureCount++;
    }

    int getToolRequestFailureCount(String requestSignature) {
        if (requestSignature == null || requestSignature.trim().isEmpty()) {
            return 0;
        }
        Integer count = toolRequestFailureCounts.get(requestSignature);
        return count == null ? 0 : count;
    }

    int getConsecutiveToolFailureCount() {
        return consecutiveToolFailureCount;
    }

    void resetConsecutiveToolFailureCount() {
        consecutiveToolFailureCount = 0;
    }

    int getLastContextSummaryTriggerChars() {
        return lastContextSummaryTriggerChars;
    }

    void setLastContextSummaryTriggerChars(int lastContextSummaryTriggerChars) {
        this.lastContextSummaryTriggerChars = Math.max(0, lastContextSummaryTriggerChars);
    }

    boolean isReplanMode() {
        return replanMode;
    }

    String getReplanReason() {
        return replanReason;
    }

    void enterReplanMode(String reason) {
        replanMode = true;
        replanReason = reason;
    }

    void clearReplanMode() {
        replanMode = false;
        replanReason = null;
    }

    void invalidateToolRequestMemoryAfterEdit() {
        successfulToolRequests.clear();
        toolRequestFailureCounts.clear();
        duplicateSuccessfulToolRequestCounts.clear();
        readFileRangesByPath.clear();
        readFileSnippetsByPath.clear();
    }

    boolean isReadFileRangeCovered(String path, Integer offset, Integer limit) {
        if (path == null || path.trim().isEmpty() || offset == null || limit == null) {
            return false;
        }
        int start = Math.max(1, offset.intValue());
        int end = Math.max(start, start + Math.max(0, limit.intValue()) - 1);
        List<LineRange> ranges = readFileRangesByPath.get(path.trim());
        if (ranges == null || ranges.isEmpty()) {
            return false;
        }
        for (LineRange range : ranges) {
            if (range != null && range.covers(start, end)) {
                return true;
            }
        }
        return false;
    }

    void rememberReadFileRange(String path, Integer offset, Integer limit) {
        if (path == null || path.trim().isEmpty() || offset == null || limit == null) {
            return;
        }
        int start = Math.max(1, offset.intValue());
        int end = Math.max(start, start + Math.max(0, limit.intValue()) - 1);
        String key = path.trim();
        List<LineRange> ranges = readFileRangesByPath.get(key);
        if (ranges == null) {
            ranges = new ArrayList<LineRange>();
            readFileRangesByPath.put(key, ranges);
        }
        mergeRange(ranges, new LineRange(start, end));
        while (ranges.size() > 6) {
            ranges.remove(0);
        }
    }

    boolean hasReadFileRanges(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        List<LineRange> ranges = readFileRangesByPath.get(path.trim());
        return ranges != null && !ranges.isEmpty();
    }

    void rememberReadFileSnippet(String path, Integer offset, Integer limit, String snippet) {
        if (path == null || path.trim().isEmpty() || offset == null || limit == null) {
            return;
        }
        if (snippet == null || snippet.trim().isEmpty()) {
            return;
        }
        int start = Math.max(1, offset.intValue());
        int end = Math.max(start, start + Math.max(0, limit.intValue()) - 1);
        String key = path.trim();
        List<SnippetRange> ranges = readFileSnippetsByPath.get(key);
        if (ranges == null) {
            ranges = new ArrayList<SnippetRange>();
            readFileSnippetsByPath.put(key, ranges);
        }
        String normalizedSnippet = snippet.trim();
        for (int index = ranges.size() - 1; index >= 0; index--) {
            SnippetRange existing = ranges.get(index);
            if (existing != null && existing.start == start && existing.end == end) {
                ranges.remove(index);
            }
        }
        ranges.add(new SnippetRange(start, end, normalizedSnippet));
        while (ranges.size() > 12) {
            ranges.remove(0);
        }
    }

    String getReadFileSnippetForRange(String path, Integer offset, Integer limit) {
        if (path == null || path.trim().isEmpty() || offset == null || limit == null) {
            return "";
        }
        int start = Math.max(1, offset.intValue());
        int end = Math.max(start, start + Math.max(0, limit.intValue()) - 1);
        List<SnippetRange> ranges = readFileSnippetsByPath.get(path.trim());
        if (ranges == null || ranges.isEmpty()) {
            return "";
        }
        SnippetRange best = null;
        SnippetRange bestOverlap = null;
        int bestOverlapSize = 0;
        for (SnippetRange range : ranges) {
            if (range == null) {
                continue;
            }
            if (!range.covers(start, end)) {
                int overlap = range.overlapSize(start, end);
                if (overlap > bestOverlapSize) {
                    bestOverlapSize = overlap;
                    bestOverlap = range;
                }
                continue;
            }
            if (best == null || range.size() < best.size()) {
                best = range;
            }
        }
        if (best != null) {
            return best.snippet;
        }
        if (bestOverlap != null && bestOverlapSize > 0) {
            return bestOverlap.snippet;
        }
        return "";
    }

    /**
     * 已完成 chat turn 后清空短期循环记忆，避免把上一轮的去重、读取缓存和重规划状态带入下一轮。
     */
    void resetForNextTurn() {
        successfulToolRequests.clear();
        toolRequestFailureCounts.clear();
        duplicateSuccessfulToolRequestCounts.clear();
        readFileRangesByPath.clear();
        readFileSnippetsByPath.clear();
        consecutiveToolFailureCount = 0;
        replanMode = false;
        replanReason = null;
    }

    private void mergeRange(List<LineRange> ranges, LineRange candidate) {
        if (ranges == null || candidate == null) {
            return;
        }
        int start = candidate.start;
        int end = candidate.end;
        for (int index = ranges.size() - 1; index >= 0; index--) {
            LineRange existing = ranges.get(index);
            if (existing == null || !existing.overlapsOrAdjacent(start, end)) {
                continue;
            }
            start = Math.min(start, existing.start);
            end = Math.max(end, existing.end);
            ranges.remove(index);
        }
        ranges.add(new LineRange(start, end));
    }

    private static final class LineRange {
        private final int start;
        private final int end;

        private LineRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        private boolean covers(int otherStart, int otherEnd) {
            return otherStart >= start && otherEnd <= end;
        }

        private boolean overlapsOrAdjacent(int otherStart, int otherEnd) {
            return otherStart <= end + 1 && otherEnd >= start - 1;
        }
    }

    private static final class SnippetRange {
        private final int start;
        private final int end;
        private final String snippet;

        private SnippetRange(int start, int end, String snippet) {
            this.start = start;
            this.end = end;
            this.snippet = snippet;
        }

        private int size() {
            return Math.max(0, end - start + 1);
        }

        private boolean covers(int otherStart, int otherEnd) {
            return otherStart >= start && otherEnd <= end;
        }

        private int overlapSize(int otherStart, int otherEnd) {
            int left = Math.max(start, otherStart);
            int right = Math.min(end, otherEnd);
            return Math.max(0, right - left + 1);
        }
    }
}
