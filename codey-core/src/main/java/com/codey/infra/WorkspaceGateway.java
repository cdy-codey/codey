package com.codey.infra;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作目录文件系统访问接口。
 */
public interface WorkspaceGateway {
    String readFile(String path);

    default ReadFileResult readFileResult(ReadFileRequest request) {
        if (request == null || isBlank(request.getPath())) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        String content = readFile(request.getPath());
        List<String> lines = splitLines(content);
        int totalLines = lines.size();
        int startLine = normalizePositive(request.getOffset(), 1);
        int maxLines = request.getLimit() == null ? Math.max(totalLines - startLine + 1, 0) : Math.max(0, request.getLimit().intValue());
        int fromIndex = Math.min(Math.max(startLine - 1, 0), totalLines);
        int toIndex = Math.min(fromIndex + maxLines, totalLines);

        ReadFileResult result = new ReadFileResult();
        result.setPath(request.getPath());
        result.setStartLine(totalLines == 0 ? 0 : fromIndex + 1);
        result.setEndLine(totalLines == 0 ? 0 : toIndex);
        result.setTotalLines(totalLines);
        result.setTruncated(toIndex < totalLines);
        result.setLines(toNumberedLines(lines.subList(fromIndex, toIndex), fromIndex + 1));
        return result;
    }

    String listWorkspace(String pathHint);

    default WorkspaceListResult listWorkspaceResult(WorkspaceListRequest request) {
        String pathHint = request == null ? null : request.getPathHint();
        String legacy = listWorkspace(pathHint);
        List<String> lines = splitLines(legacy);

        WorkspaceListResult result = new WorkspaceListResult();
        result.setPath(isBlank(pathHint) ? "." : pathHint);
        result.setRoot(resolveLegacyRoot(lines, pathHint));
        result.setMaxDepth(request == null ? 1 : normalizePositive(request.getMaxDepth(), 1));
        result.setLimit(request == null ? 50 : normalizePositive(request.getLimit(), 50));
        result.setSummary(legacy);

        List<WorkspaceEntry> entries = new ArrayList<WorkspaceEntry>();
        for (String line : lines) {
            WorkspaceEntry entry = parseLegacyWorkspaceEntry(line);
            if (entry != null) {
                entries.add(entry);
            }
        }
        result.setEntries(entries);
        result.setReturnedCount(entries.size());
        result.setTotalCount(entries.size());
        result.setTotalDiscovered(entries.size());
        result.setDirectoryCount(countDirectories(entries));
        result.setFileCount(entries.size() - result.getDirectoryCount());
        result.setTruncated(false);
        return result;
    }

    String searchCode(String keyword, String pathHint);

    default SearchCodeResult searchCodeResult(SearchCodeRequest request) {
        if (request == null || isBlank(request.getKeyword())) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        String legacy = searchCode(request.getKeyword(), request.getPathHint());
        SearchCodeResult result = new SearchCodeResult();
        result.setRoot(isBlank(request.getPathHint()) ? "." : request.getPathHint());
        result.setPattern(request.getKeyword());
        result.setRegex(Boolean.TRUE.equals(request.getRegex()));
        result.setCaseSensitive(Boolean.TRUE.equals(request.getCaseSensitive()));
        result.setContextLines(request.getContextLines() == null ? 2 : Math.max(0, request.getContextLines().intValue()));
        result.setMaxResults(request.getMaxResults() == null ? 20 : Math.max(1, request.getMaxResults().intValue()));
        result.setFilesSearched(0);

        List<SearchCodeMatch> matches = parseLegacySearchMatches(legacy);
        result.setMatches(matches);
        result.setTotalMatches(matches.size());
        result.setTruncated(false);
        return result;
    }

    String writeFile(String path, String content);

    String copyFile(String sourcePath, String targetPath);

    default String normalizeRelativePath(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }

    default boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    default int normalizePositive(Integer value, int defaultValue) {
        return value == null || value.intValue() <= 0 ? defaultValue : value.intValue();
    }

    default List<String> splitLines(String content) {
        List<String> lines = new ArrayList<String>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        String[] split = content.replace("\r", "").split("\n", -1);
        for (int index = 0; index < split.length; index++) {
            if (index == split.length - 1 && split[index].isEmpty()) {
                continue;
            }
            lines.add(split[index]);
        }
        return lines;
    }

    default List<NumberedLine> toNumberedLines(List<String> lines, int startLine) {
        List<NumberedLine> numberedLines = new ArrayList<NumberedLine>();
        int lineNumber = startLine;
        for (String line : lines) {
            NumberedLine item = new NumberedLine();
            item.setLineNumber(lineNumber++);
            item.setContent(line);
            numberedLines.add(item);
        }
        return numberedLines;
    }

    default String resolveLegacyRoot(List<String> lines, String pathHint) {
        for (String line : lines) {
            if (line != null && line.trim().startsWith("path=")) {
                return normalizeRelativePath(line.trim().substring("path=".length()).trim());
            }
        }
        return isBlank(pathHint) ? "." : normalizeRelativePath(pathHint.trim());
    }

    default WorkspaceEntry parseLegacyWorkspaceEntry(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || "(empty)".equals(trimmed) || trimmed.startsWith("path=")) {
            return null;
        }
        WorkspaceEntry entry = new WorkspaceEntry();
        boolean directory = trimmed.startsWith("[D]");
        String path = trimmed.replaceFirst("^\\[[DF]\\]\\s*", "").trim();
        entry.setDirectory(directory);
        entry.setPath(normalizeRelativePath(path));
        entry.setName(extractName(path));
        entry.setDepth(computeDepth(path));
        return entry;
    }

    default int countDirectories(List<WorkspaceEntry> entries) {
        int count = 0;
        for (WorkspaceEntry entry : entries) {
            if (entry != null && entry.isDirectory()) {
                count++;
            }
        }
        return count;
    }

    default List<SearchCodeMatch> parseLegacySearchMatches(String content) {
        List<SearchCodeMatch> matches = new ArrayList<SearchCodeMatch>();
        if (isBlank(content) || content.startsWith("No code match")) {
            return matches;
        }
        List<String> lines = splitLines(content);
        if (lines.isEmpty()) {
            return matches;
        }
        String header = lines.get(0);
        if (!header.startsWith("path=")) {
            return matches;
        }
        String path = header;
        int matchedLine = 0;
        int separatorIndex = header.indexOf(", matchedLine=");
        if (separatorIndex >= 0) {
            path = header.substring("path=".length(), separatorIndex).trim();
            try {
                matchedLine = Integer.parseInt(header.substring(separatorIndex + ", matchedLine=".length()).trim());
            } catch (Exception ignored) {
                matchedLine = 0;
            }
        }

        List<NumberedLine> snippetLines = new ArrayList<NumberedLine>();
        int startLine = 0;
        int endLine = 0;
        for (int index = 1; index < lines.size(); index++) {
            String snippet = lines.get(index);
            int colonIndex = snippet.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }
            try {
                int lineNumber = Integer.parseInt(snippet.substring(0, colonIndex).trim());
                NumberedLine item = new NumberedLine();
                item.setLineNumber(lineNumber);
                item.setContent(snippet.substring(colonIndex + 1).trim());
                snippetLines.add(item);
                if (startLine == 0) {
                    startLine = lineNumber;
                }
                endLine = lineNumber;
            } catch (Exception ignored) {
            }
        }

        SearchCodeMatch match = new SearchCodeMatch();
        match.setPath(normalizeRelativePath(path));
        match.setMatchedLine(matchedLine <= 0 ? startLine : matchedLine);
        match.setStartLine(startLine);
        match.setEndLine(endLine);
        match.setSnippetLines(snippetLines);
        matches.add(match);
        return matches;
    }

    default String extractName(String path) {
        String normalized = normalizeRelativePath(path);
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    default int computeDepth(String path) {
        String normalized = normalizeRelativePath(path);
        if (isBlank(normalized) || ".".equals(normalized)) {
            return 0;
        }
        return normalized.split("/").length;
    }
}
