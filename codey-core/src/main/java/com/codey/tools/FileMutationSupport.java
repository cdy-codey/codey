package com.codey.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件写工具共用的文本变更辅助逻辑。
 */
public final class FileMutationSupport {
    private FileMutationSupport() {
    }

    public static boolean containsText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static ReplaceResult replaceWithFallbacks(String originalContent, String searchText, String replacement) {
        ReplaceResult exact = replaceExact(originalContent, searchText, replacement, "Replaced exact text segment");
        if (exact.isMatched()) {
            return exact;
        }

        String normalizedSearch = stripToolLinePrefixes(searchText);
        String normalizedReplacement = stripToolLinePrefixes(replacement);
        ReplaceResult stripped = replaceExact(
                originalContent,
                normalizedSearch,
                normalizedReplacement,
                "Replaced text segment after stripping tool line numbers"
        );
        if (stripped.isMatched()) {
            return stripped;
        }

        return replaceIgnoringWhitespace(
                originalContent,
                normalizedSearch,
                normalizedReplacement,
                "Replaced text segment after normalizing whitespace"
        );
    }

    public static String stripToolLinePrefixes(String value) {
        if (!containsText(value)) {
            return value;
        }
        String[] lines = value.replace("\r", "").split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines[index].replaceFirst("^\\s*\\d+:\\s?", ""));
        }
        return builder.toString();
    }

    public static String buildDiffSummary(String originalContent, String updatedContent) {
        return buildDiff(originalContent, updatedContent, 0).getSummary();
    }

    public static List<String> buildPreview(String originalContent, String updatedContent, int maxPreviewLines) {
        return buildDiff(originalContent, updatedContent, maxPreviewLines).getPreview();
    }

    /**
     * 一次行扫描同时产出 diff 摘要与预览，避免对同一份内容重复 split/遍历。
     * 大文件写入时是写工具的主要 CPU 开销点，合并后可将字符串数组分配与比较次数减半。
     */
    public static DiffResult buildDiff(String originalContent, String updatedContent, int maxPreviewLines) {
        String[] originalLines = safe(originalContent).split("\\R", -1);
        String[] updatedLines = safe(updatedContent).split("\\R", -1);
        int min = Math.min(originalLines.length, updatedLines.length);
        int firstChangedIndex = -1;
        for (int index = 0; index < min; index++) {
            if (!originalLines[index].equals(updatedLines[index])) {
                firstChangedIndex = index;
                break;
            }
        }
        if (firstChangedIndex < 0 && originalLines.length != updatedLines.length) {
            firstChangedIndex = min;
        }
        int firstChangedLine = firstChangedIndex < 0 ? -1 : firstChangedIndex + 1;
        String summary = "originalLines=" + originalLines.length
                + ", updatedLines=" + updatedLines.length
                + ", firstChangedLine=" + firstChangedLine;

        List<String> preview = new ArrayList<String>();
        if (maxPreviewLines > 0) {
            // 无变化时保持旧预览语义（从内容末尾往回取），避免行为差异影响既有 UI。
            int previewFirstIndex = firstChangedIndex < 0 ? originalLines.length : firstChangedIndex;
            int previewStart = Math.max(0, previewFirstIndex - 1);
            int previewEnd = Math.min(updatedLines.length, previewStart + Math.max(1, maxPreviewLines));
            for (int index = previewStart; index < previewEnd; index++) {
                preview.add((index + 1) + ": " + updatedLines[index]);
            }
        }
        return new DiffResult(summary, preview);
    }

    public static List<String> toLines(String content) {
        if (!containsText(content)) {
            return new ArrayList<String>();
        }
        return Arrays.asList(content.split("\\R", -1));
    }

    private static ReplaceResult replaceExact(String originalContent,
                                             String searchText,
                                             String replacement,
                                             String summary) {
        if (!containsText(searchText) || !safe(originalContent).contains(searchText)) {
            return ReplaceResult.notMatched();
        }
        return ReplaceResult.matched(safe(originalContent).replace(searchText, safe(replacement)), summary);
    }

    private static ReplaceResult replaceIgnoringWhitespace(String originalContent,
                                                           String searchText,
                                                           String replacement,
                                                           String summary) {
        if (!containsText(searchText)) {
            return ReplaceResult.notMatched();
        }
        IndexedText originalIndexed = indexNonWhitespace(safe(originalContent));
        IndexedText searchIndexed = indexNonWhitespace(searchText);
        if (searchIndexed.getCollapsed().isEmpty()) {
            return ReplaceResult.notMatched();
        }
        int collapsedIndex = originalIndexed.getCollapsed().indexOf(searchIndexed.getCollapsed());
        if (collapsedIndex < 0) {
            return ReplaceResult.notMatched();
        }
        int originalStart = originalIndexed.getPositions().get(collapsedIndex);
        int endCollapsedIndex = collapsedIndex + searchIndexed.getCollapsed().length() - 1;
        int originalEnd = originalIndexed.getPositions().get(endCollapsedIndex) + 1;
        String updated = safe(originalContent).substring(0, originalStart)
                + safe(replacement)
                + safe(originalContent).substring(originalEnd);
        return ReplaceResult.matched(updated, summary);
    }

    private static IndexedText indexNonWhitespace(String value) {
        StringBuilder collapsed = new StringBuilder();
        List<Integer> positions = new ArrayList<Integer>();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                continue;
            }
            collapsed.append(current);
            positions.add(index);
        }
        return new IndexedText(collapsed.toString(), positions);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class DiffResult {
        private final String summary;
        private final List<String> preview;

        private DiffResult(String summary, List<String> preview) {
            this.summary = summary;
            this.preview = preview;
        }

        public String getSummary() {
            return summary;
        }

        public List<String> getPreview() {
            return preview;
        }
    }

    public static final class ReplaceResult {
        private final boolean matched;
        private final String updatedContent;
        private final String summary;

        private ReplaceResult(boolean matched, String updatedContent, String summary) {
            this.matched = matched;
            this.updatedContent = updatedContent;
            this.summary = summary;
        }

        public static ReplaceResult matched(String updatedContent, String summary) {
            return new ReplaceResult(true, updatedContent, summary);
        }

        public static ReplaceResult notMatched() {
            return new ReplaceResult(false, null, null);
        }

        public boolean isMatched() {
            return matched;
        }

        public String getUpdatedContent() {
            return updatedContent;
        }

        public String getSummary() {
            return summary;
        }
    }

    private static final class IndexedText {
        private final String collapsed;
        private final List<Integer> positions;

        private IndexedText(String collapsed, List<Integer> positions) {
            this.collapsed = collapsed;
            this.positions = positions;
        }

        private String getCollapsed() {
            return collapsed;
        }

        private List<Integer> getPositions() {
            return positions;
        }
    }
}
