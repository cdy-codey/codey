package com.codey.mcp;

import com.codey.infra.NumberedLine;
import com.codey.infra.ReadFileResult;
import com.codey.infra.SearchCodeMatch;
import com.codey.infra.SearchCodeResult;
import com.codey.infra.WorkspaceEntry;
import com.codey.infra.WorkspaceListResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责把工作目录 DTO 转成工具层稳定输出，避免网关层直接拼接 JSON。
 */
final class WorkspaceToolPayloadFormatter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    String formatReadFileResult(ReadFileResult result) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("path", result.getPath());
        payload.put("startLine", result.getStartLine());
        payload.put("endLine", result.getEndLine());
        payload.put("totalLines", result.getTotalLines());
        payload.put("truncated", result.isTruncated());
        payload.put("content", formatNumberedLines(result.getLines()));
        return toJson(payload);
    }

    String formatWorkspaceListResult(WorkspaceListResult result) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("path", result.getPath());
        payload.put("root", result.getRoot());
        payload.put("maxDepth", result.getMaxDepth());
        payload.put("limit", result.getLimit());
        payload.put("returnedCount", result.getReturnedCount());
        payload.put("totalCount", result.getTotalCount());
        payload.put("totalDiscovered", result.getTotalDiscovered());
        payload.put("directoryCount", result.getDirectoryCount());
        payload.put("fileCount", result.getFileCount());
        payload.put("truncated", result.isTruncated());
        payload.put("summary", result.getSummary());
        payload.put("entries", toWorkspaceEntries(result.getEntries()));
        return toJson(payload);
    }

    String formatSearchCodeResult(SearchCodeResult result) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("root", result.getRoot());
        payload.put("pattern", result.getPattern());
        payload.put("regex", result.isRegex());
        payload.put("caseSensitive", result.isCaseSensitive());
        payload.put("contextLines", result.getContextLines());
        payload.put("maxResults", result.getMaxResults());
        payload.put("filesSearched", result.getFilesSearched());
        payload.put("totalMatches", result.getTotalMatches());
        payload.put("truncated", result.isTruncated());
        payload.put("matches", toSearchMatches(result.getMatches()));
        return toJson(payload);
    }

    private List<Map<String, Object>> toWorkspaceEntries(List<WorkspaceEntry> entries) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        if (entries == null) {
            return items;
        }
        for (WorkspaceEntry entry : entries) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", entry.getName());
            item.put("path", entry.getPath());
            item.put("directory", entry.isDirectory());
            item.put("depth", entry.getDepth());
            item.put("sizeBytes", entry.getSizeBytes());
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> toSearchMatches(List<SearchCodeMatch> matches) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        if (matches == null) {
            return items;
        }
        for (SearchCodeMatch match : matches) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("path", match.getPath());
            item.put("matchedLine", match.getMatchedLine());
            item.put("startLine", match.getStartLine());
            item.put("endLine", match.getEndLine());
            item.put("snippet", formatNumberedLines(match.getSnippetLines()));
            items.add(item);
        }
        return items;
    }

    private String formatNumberedLines(List<NumberedLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(lines.get(index).getLineNumber())
                    .append(": ")
                    .append(lines.get(index).getContent());
        }
        return builder.toString();
    }

    private String toJson(Map<String, Object> payload) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }
}
