package com.codey.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地工作目录文件系统实现。
 */
public class LocalWorkspaceGateway implements WorkspaceGateway {
    private static final int DEFAULT_LIST_LIMIT = 50;
    private static final int DEFAULT_LIST_DEPTH = 1;
    private static final int DEFAULT_SEARCH_CONTEXT = 2;
    private static final int DEFAULT_SEARCH_MAX_RESULTS = 20;
    private static final long MAX_SEARCH_FILE_BYTES = 512 * 1024;
    private static final List<String> DEFAULT_IGNORED_DIRS = Arrays.asList(
            ".git", ".idea", "node_modules", "dist", "target", "build", "out"
    );

    private final Path workspaceRoot;

    public LocalWorkspaceGateway() {
        this(Paths.get("."));
    }

    public LocalWorkspaceGateway(String workspaceRoot) {
        this(Paths.get(workspaceRoot == null || workspaceRoot.trim().isEmpty() ? "." : workspaceRoot.trim()));
    }

    public LocalWorkspaceGateway(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot == null
                ? Paths.get(".").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @Override
    public String readFile(String path) {
        try {
            return new String(Files.readAllBytes(resolvePath(path)), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file: " + path, exception);
        }
    }

    @Override
    public ReadFileResult readFileResult(ReadFileRequest request) {
        if (request == null || isBlank(request.getPath())) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        try {
            Path target = resolvePath(request.getPath());
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            int totalLines = lines.size();
            int startLine = normalizePositive(request.getOffset(), 1);
            int maxLines = request.getLimit() == null ? Math.max(totalLines - startLine + 1, 0) : Math.max(0, request.getLimit());

            int fromIndex = Math.min(Math.max(startLine - 1, 0), totalLines);
            int toIndex = Math.min(fromIndex + maxLines, totalLines);
            List<String> selected = lines.subList(fromIndex, toIndex);

            ReadFileResult result = new ReadFileResult();
            result.setPath(relativize(target));
            result.setStartLine(totalLines == 0 ? 0 : fromIndex + 1);
            result.setEndLine(totalLines == 0 ? 0 : toIndex);
            result.setTotalLines(totalLines);
            result.setTruncated(toIndex < totalLines);
            result.setLines(toNumberedLines(selected, fromIndex + 1));
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file: " + request.getPath(), exception);
        }
    }

    @Override
    public String listWorkspace(String pathHint) {
        Path root = isBlank(pathHint) ? workspaceRoot : resolvePath(pathHint);
        String visibleRoot = relativize(root);
        try (Stream<Path> stream = Files.list(root)) {
            List<String> entries = stream
                    .sorted()
                    .limit(DEFAULT_LIST_LIMIT)
                    .map(this::formatWorkspaceEntry)
                    .collect(Collectors.toList());
            if (entries.isEmpty()) {
                return "path=" + relativize(root) + System.lineSeparator() + "(empty)";
            }
            return "path=" + relativize(root)
                    + System.lineSeparator()
                    + String.join(System.lineSeparator(), entries);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list workspace under: " + visibleRoot, exception);
        }
    }

    @Override
    public WorkspaceListResult listWorkspaceResult(WorkspaceListRequest request) {
        String pathHint = request == null ? null : request.getPathHint();
        Path root = isBlank(pathHint) ? workspaceRoot : resolvePath(pathHint);
        String visibleRoot = relativize(root);
        int maxDepth = request == null ? DEFAULT_LIST_DEPTH : normalizePositive(request.getMaxDepth(), DEFAULT_LIST_DEPTH);
        int limit = request == null ? DEFAULT_LIST_LIMIT : normalizePositive(request.getLimit(), DEFAULT_LIST_LIMIT);
        boolean includeHidden = request != null && Boolean.TRUE.equals(request.getIncludeHidden());

        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            List<Path> discovered = stream
                    .filter(path -> !path.equals(root))
                    .filter(path -> includeHidden || !isHiddenPath(path))
                    .sorted(workspacePathComparator())
                    .collect(Collectors.toList());

            List<WorkspaceEntry> entries = new ArrayList<WorkspaceEntry>();
            for (Path path : discovered.subList(0, Math.min(discovered.size(), limit))) {
                entries.add(workspaceEntry(root, path));
            }

            int directoryCount = 0;
            int fileCount = 0;
            for (Path path : discovered) {
                if (Files.isDirectory(path)) {
                    directoryCount++;
                } else {
                    fileCount++;
                }
            }

            WorkspaceListResult result = new WorkspaceListResult();
            result.setPath(relativize(root));
            result.setRoot(relativize(root));
            result.setMaxDepth(maxDepth);
            result.setLimit(limit);
            result.setReturnedCount(entries.size());
            result.setTotalCount(discovered.size());
            result.setTotalDiscovered(discovered.size());
            result.setDirectoryCount(directoryCount);
            result.setFileCount(fileCount);
            result.setTruncated(discovered.size() > limit);
            result.setSummary(buildWorkspaceSummary(relativize(root), directoryCount, fileCount, discovered.size()));
            result.setEntries(entries);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list workspace under: " + visibleRoot, exception);
        }
    }

    @Override
    public String searchCode(String keyword, String pathHint) {
        Path root = isBlank(pathHint) ? workspaceRoot : resolvePath(pathHint);
        String visibleRoot = relativize(root);
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isTextFile(path.getFileName().toString()))
                    .map(path -> buildLegacySearchResult(path, keyword))
                    .filter(result -> result != null)
                    .findFirst()
                    .map(LegacySearchResult::format)
                    .orElse("No code match for keyword: " + keyword);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to search code under: " + visibleRoot, exception);
        }
    }

    @Override
    public SearchCodeResult searchCodeResult(SearchCodeRequest request) {
        if (request == null || isBlank(request.getKeyword())) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        Path root = isBlank(request.getPathHint()) ? workspaceRoot : resolvePath(request.getPathHint());
        String visibleRoot = relativize(root);
        int contextLines = normalizePositive(request.getContextLines(), DEFAULT_SEARCH_CONTEXT);
        int maxResults = normalizePositive(request.getMaxResults(), DEFAULT_SEARCH_MAX_RESULTS);
        boolean useRegex = Boolean.TRUE.equals(request.getRegex());
        boolean caseSensitive = Boolean.TRUE.equals(request.getCaseSensitive());
        Pattern regexPattern = useRegex ? compilePattern(request.getKeyword(), caseSensitive) : null;
        PathMatcher fileMatcher = buildFileMatcher(request.getFilePattern());

        List<SearchCodeMatch> matches = new ArrayList<SearchCodeMatch>();
        int filesSearched = 0;
        boolean truncated = false;

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSearchableFile)
                    .filter(path -> !isIgnoredPath(path))
                    .filter(path -> fileMatcher == null || matchesFilePattern(root, path, fileMatcher))
                    .sorted()
                    .collect(Collectors.toList());

            for (Path path : candidates) {
                filesSearched++;
                if (isLargeFile(path)) {
                    continue;
                }

                List<SearchCodeMatch> fileMatches = findMatches(path, regexPattern, request.getKeyword(), useRegex, caseSensitive, contextLines);
                for (SearchCodeMatch fileMatch : fileMatches) {
                    if (matches.size() >= maxResults) {
                        truncated = true;
                        break;
                    }
                    matches.add(fileMatch);
                }
                if (truncated) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to search code under: " + visibleRoot, exception);
        }

        SearchCodeResult result = new SearchCodeResult();
        result.setRoot(relativize(root));
        result.setPattern(request.getKeyword());
        result.setRegex(useRegex);
        result.setCaseSensitive(caseSensitive);
        result.setContextLines(contextLines);
        result.setMaxResults(maxResults);
        result.setFilesSearched(filesSearched);
        result.setTotalMatches(matches.size());
        result.setTruncated(truncated);
        result.setMatches(matches);
        return result;
    }

    @Override
    public String writeFile(String path, String content) {
        try {
            Path target = resolvePath(path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));
            return relativize(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write file: " + path, exception);
        }
    }

    @Override
    public String copyFile(String sourcePath, String targetPath) {
        try {
            Path source = resolvePath(sourcePath);
            Path target = resolvePath(targetPath);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return relativize(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy file from " + sourcePath + " to " + targetPath, exception);
        }
    }

    private LegacySearchResult buildLegacySearchResult(Path path, String keyword) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.contains(keyword)) {
                    int start = Math.max(0, index - 2);
                    int end = Math.min(lines.size() - 1, index + 2);
                    List<String> context = lines.subList(start, end + 1);
                    return new LegacySearchResult(relativize(path), index + 1, context, start + 1);
                }
            }
            return null;
        } catch (IOException exception) {
            return null;
        }
    }

    private List<SearchCodeMatch> findMatches(Path path,
                                              Pattern regexPattern,
                                              String keyword,
                                              boolean useRegex,
                                              boolean caseSensitive,
                                              int contextLines) {
        List<SearchCodeMatch> matches = new ArrayList<SearchCodeMatch>();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                if (!lineMatches(lines.get(index), regexPattern, keyword, useRegex, caseSensitive)) {
                    continue;
                }
                int start = Math.max(0, index - contextLines);
                int end = Math.min(lines.size() - 1, index + contextLines);

                SearchCodeMatch item = new SearchCodeMatch();
                item.setPath(relativize(path));
                item.setMatchedLine(index + 1);
                item.setStartLine(start + 1);
                item.setEndLine(end + 1);
                item.setSnippetLines(toNumberedLines(lines.subList(start, end + 1), start + 1));
                matches.add(item);
            }
        } catch (IOException exception) {
            // 搜索期间忽略不可读文件，保留其他结果。
        }
        return matches;
    }

    private boolean lineMatches(String line,
                                Pattern regexPattern,
                                String keyword,
                                boolean useRegex,
                                boolean caseSensitive) {
        if (useRegex) {
            Matcher matcher = regexPattern.matcher(line);
            return matcher.find();
        }
        if (caseSensitive) {
            return line.contains(keyword);
        }
        return line.toLowerCase().contains(keyword.toLowerCase());
    }

    private Pattern compilePattern(String keyword, boolean caseSensitive) {
        int flags = Pattern.MULTILINE;
        if (!caseSensitive) {
            flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        return Pattern.compile(keyword, flags);
    }

    private PathMatcher buildFileMatcher(String filePattern) {
        if (isBlank(filePattern)) {
            return null;
        }
        String normalized = normalizeGlobPattern(filePattern.trim());
        if (!normalized.startsWith("glob:")) {
            normalized = "glob:" + normalized;
        }
        return FileSystems.getDefault().getPathMatcher(normalized);
    }

    private boolean matchesFilePattern(Path searchRoot, Path path, PathMatcher matcher) {
        Path relative = searchRoot.relativize(path.toAbsolutePath().normalize());
        Path normalizedRelative = toPatternPath(relative);
        Path fileName = relative.getFileName();
        return matcher.matches(normalizedRelative)
                || matcher.matches(relative)
                || (fileName != null && (matcher.matches(fileName) || matcher.matches(toPatternPath(fileName))));
    }

    private boolean isSearchableFile(Path path) {
        return Files.isRegularFile(path) && isTextFile(path.getFileName().toString());
    }

    private boolean isLargeFile(Path path) {
        try {
            return Files.size(path) > MAX_SEARCH_FILE_BYTES;
        } catch (IOException exception) {
            return true;
        }
    }

    private boolean isIgnoredPath(Path path) {
        Path relative = workspaceRoot.relativize(path.toAbsolutePath().normalize());
        for (Path part : relative) {
            if (DEFAULT_IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isHiddenPath(Path path) {
        try {
            if (Files.isHidden(path)) {
                return true;
            }
        } catch (IOException ignored) {
        }
        Path name = path.getFileName();
        return name != null && name.toString().startsWith(".");
    }

    private Comparator<Path> workspacePathComparator() {
        return Comparator
                .comparing((Path path) -> !Files.isDirectory(path))
                .thenComparing(path -> relativize(path).toLowerCase());
    }

    private WorkspaceEntry workspaceEntry(Path root, Path path) {
        WorkspaceEntry entry = new WorkspaceEntry();
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        entry.setName(path.getFileName() == null ? relativize(path) : path.getFileName().toString());
            entry.setPath(normalizeSeparators(relative.toString()));
        entry.setDirectory(Files.isDirectory(path));
        entry.setDepth(relative.getNameCount());
        if (!Files.isDirectory(path)) {
            try {
                entry.setSizeBytes(Long.valueOf(Files.size(path)));
            } catch (IOException ignored) {
                entry.setSizeBytes(null);
            }
        }
        return entry;
    }

    private String formatWorkspaceEntry(Path path) {
        String prefix = Files.isDirectory(path) ? "[D] " : "[F] ";
        return prefix + relativize(path);
    }

    private String buildWorkspaceSummary(String root, int directoryCount, int fileCount, int totalCount) {
        return "path=" + root
                + ", directories=" + directoryCount
                + ", files=" + fileCount
                + ", total=" + totalCount;
    }

    private String formatNumberedLines(List<NumberedLine> lines) {
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

    public List<NumberedLine> toNumberedLines(List<String> lines, int startLine) {
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

    public int normalizePositive(Integer value, int defaultValue) {
        return value == null || value.intValue() <= 0 ? defaultValue : value.intValue();
    }

    private boolean isTextFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".java")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx")
                || lower.endsWith(".jsx")
                || lower.endsWith(".vue")
                || lower.endsWith(".json")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".xml")
                || lower.endsWith(".md")
                || lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".scss")
                || lower.endsWith(".sql")
                || lower.endsWith(".py")
                || lower.endsWith(".go")
                || lower.endsWith(".rs")
                || lower.endsWith(".txt")
                || lower.endsWith(".properties")
                || lower.endsWith(".toml");
    }

    private Path resolvePath(String path) {
        return WorkspacePathSupport.resolveRelativePath(workspaceRoot, path);
    }

    private String relativize(Path path) {
        try {
            return WorkspacePathSupport.relativize(workspaceRoot, path);
        } catch (Exception exception) {
            return normalizeSeparators(path.toString());
        }
    }

    private String normalizeGlobPattern(String pattern) {
        String normalized = pattern.replace("\\", "/");
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private Path toPatternPath(Path path) {
        if (path == null) {
            return Paths.get(".");
        }
        String normalized = normalizeSeparators(path.toString());
        if (normalized.isEmpty()) {
            return Paths.get(".");
        }
        return Paths.get(normalized);
    }

    public boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeSeparators(String value) {
        return value == null ? "" : value.replace("\\", "/");
    }

    private static final class LegacySearchResult {
        private final String path;
        private final int matchedLine;
        private final List<String> contextLines;
        private final int startLine;

        private LegacySearchResult(String path, int matchedLine, List<String> contextLines, int startLine) {
            this.path = path;
            this.matchedLine = matchedLine;
            this.contextLines = contextLines;
            this.startLine = startLine;
        }

        private String format() {
            String snippet = contextLines.stream()
                    .map(new java.util.function.Function<String, String>() {
                        private int line = startLine;

                        @Override
                        public String apply(String value) {
                            return (line++) + ": " + value;
                        }
                    })
                    .collect(Collectors.joining(System.lineSeparator()));
            return "path=" + path
                    + ", matchedLine=" + matchedLine
                    + System.lineSeparator()
                    + snippet;
        }
    }
}
