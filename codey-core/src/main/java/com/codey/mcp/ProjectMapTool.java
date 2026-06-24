package com.codey.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codey.infra.LocalWorkspaceGateway;
import com.codey.infra.ModelToolDefinition;
import com.codey.infra.WorkspaceGateway;
import com.codey.tools.AbstractWorkspaceTool;
import com.codey.tools.ToolCapability;
import com.codey.tools.ToolInvocation;
import com.codey.tools.ToolResult;
import com.codey.tools.WorkspaceToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 为当前工作区生成目录树、关键文件和文件类型统计摘要。
 */
public class ProjectMapTool extends AbstractWorkspaceTool {
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final int DEFAULT_MAX_ENTRIES = 120;
    private static final int DEFAULT_MAX_KEY_FILES = 12;
    private static final List<String> PRIORITY_FILE_NAMES = Arrays.asList(
            "package.json", "pom.xml", "build.gradle", "settings.gradle", "Cargo.toml",
            "README.md", "README", "vite.config.ts", "vite.config.js", "tsconfig.json",
            "index.html", "main.ts", "main.js", "App.vue", "app.vue"
    );

    private final WorkspaceGateway workspaceGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProjectMapTool(WorkspaceGateway workspaceGateway) {
        this.workspaceGateway = workspaceGateway;
    }

    @Override
    public String name() {
        return "project_map";
    }

    @Override
    public String displayName() {
        return "查询目录结构";
    }

    @Override
    public String description() {
        return "Return directory tree, key files and file type summary for the current project.";
    }

    @Override
    public ModelToolDefinition toModelToolDefinition() {
        ModelToolDefinition definition = new ModelToolDefinition();
        definition.setName(name());
        definition.setDescription(description());
        definition.setParameters(buildParameters());
        return definition;
    }

    @Override
    public ToolResult execute(ToolInvocation request, WorkspaceToolContext context) {
        try {
            Path root = resolveRoot(request, context);
            int maxDepth = readInteger(request, "maxDepth", DEFAULT_MAX_DEPTH);
            int maxEntries = readInteger(request, "maxEntries", DEFAULT_MAX_ENTRIES);
            int maxKeyFiles = readInteger(request, "maxKeyFiles", DEFAULT_MAX_KEY_FILES);

            List<Path> discovered = discover(root, maxDepth);
            List<Path> sorted = discovered.stream()
                    .sorted(new Comparator<Path>() {
                        @Override
                        public int compare(Path left, Path right) {
                            boolean leftIsFile = !Files.isDirectory(left);
                            boolean rightIsFile = !Files.isDirectory(right);
                            if (leftIsFile != rightIsFile) {
                                return leftIsFile ? 1 : -1;
                            }
                            return relativize(root, left).toLowerCase()
                                    .compareTo(relativize(root, right).toLowerCase());
                        }
                    })
                    .collect(Collectors.toList());

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("root", relativize(root, root));
            payload.put("maxDepth", maxDepth);
            payload.put("totalEntries", sorted.size());
            payload.put("directories", countDirectories(sorted));
            payload.put("files", countFiles(sorted));
            payload.put("fileTypes", summarizeFileTypes(sorted));
            payload.put("keyFiles", extractKeyFiles(root, sorted, maxKeyFiles));
            payload.put("tree", buildTree(root, sorted, maxEntries));
            payload.put("truncated", sorted.size() > maxEntries);
            payload.put("summary", buildSummary(sorted));
            return ToolResult.ok(
                    "Project map result:\n" + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                    "已生成目录结构概览"
            );
        } catch (Exception exception) {
            return ToolResult.fail("生成目录结构概览失败：" + exception.getMessage());
        }
    }

    @Override
    public ToolCapability capability() {
        return ToolCapability.readOnlyParallel();
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("pathHint", stringProperty("Optional subdirectory path hint. Defaults to the whole workspace."));
        properties.put("maxDepth", integerProperty("Maximum traversal depth. Default is 3."));
        properties.put("maxEntries", integerProperty("Maximum number of tree entries to return."));
        properties.put("maxKeyFiles", integerProperty("Maximum number of key files to include."));

        parameters.put("properties", properties);
        parameters.put("required", Collections.emptyList());
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private Path resolveRoot(ToolInvocation request, WorkspaceToolContext context) {
        String pathHint = readString(request, "pathHint");
        if (context != null && context.getWorkspaceRoot() != null) {
            return isBlank(pathHint) ? context.getWorkspaceRoot() : context.resolvePath(pathHint);
        }
        if (workspaceGateway instanceof LocalWorkspaceGateway) {
            Path workspaceRoot = ((LocalWorkspaceGateway) workspaceGateway).getWorkspaceRoot();
            if (isBlank(pathHint)) {
                return workspaceRoot;
            }
            Path resolved = workspaceRoot.resolve(pathHint).normalize();
            if (!resolved.startsWith(workspaceRoot)) {
                throw new IllegalArgumentException("Path escapes workspace root: " + pathHint);
            }
            return resolved;
        }
        throw new IllegalStateException("project_map requires local workspace access");
    }

    private List<Path> discover(Path root, int maxDepth) throws IOException {
        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream
                    .filter(path -> !path.equals(root))
                    .filter(this::shouldKeep)
                    .collect(Collectors.toList());
        }
    }

    private boolean shouldKeep(Path path) {
        String normalized = path.toString().replace("\\", "/");
        return !normalized.contains("/.git/")
                && !normalized.contains("/node_modules/")
                && !normalized.contains("/dist/")
                && !normalized.contains("/target/")
                && !normalized.contains("/build/")
                && !normalized.contains("/out/");
    }

    private int countDirectories(List<Path> paths) {
        int count = 0;
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                count++;
            }
        }
        return count;
    }

    private int countFiles(List<Path> paths) {
        int count = 0;
        for (Path path : paths) {
            if (!Files.isDirectory(path)) {
                count++;
            }
        }
        return count;
    }

    private List<Map<String, Object>> summarizeFileTypes(List<Path> paths) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                continue;
            }
            String extension = extensionOf(path.getFileName() == null ? "" : path.getFileName().toString());
            Integer current = counts.get(extension);
            counts.put(extension, current == null ? 1 : current + 1);
        }

        List<Map<String, Object>> summary = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("extension", entry.getKey());
            item.put("count", entry.getValue());
            summary.add(item);
        }
        summary.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                Integer leftCount = (Integer) left.get("count");
                Integer rightCount = (Integer) right.get("count");
                int byCount = rightCount.compareTo(leftCount);
                if (byCount != 0) {
                    return byCount;
                }
                return String.valueOf(left.get("extension")).compareTo(String.valueOf(right.get("extension")));
            }
        });
        return summary;
    }

    private List<String> extractKeyFiles(Path root, List<Path> paths, int maxKeyFiles) {
        List<String> keyFiles = new ArrayList<String>();
        for (String fileName : PRIORITY_FILE_NAMES) {
            for (Path path : paths) {
                if (Files.isDirectory(path) || path.getFileName() == null) {
                    continue;
                }
                if (!fileName.equalsIgnoreCase(path.getFileName().toString())) {
                    continue;
                }
                String relative = relativize(root, path);
                if (!keyFiles.contains(relative)) {
                    keyFiles.add(relative);
                }
                if (keyFiles.size() >= maxKeyFiles) {
                    return keyFiles;
                }
            }
        }
        return keyFiles;
    }

    private List<Map<String, Object>> buildTree(Path root, List<Path> sorted, int maxEntries) {
        List<Map<String, Object>> tree = new ArrayList<Map<String, Object>>();
        int limit = Math.min(sorted.size(), maxEntries);
        for (int index = 0; index < limit; index++) {
            Path path = sorted.get(index);
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            Path relative = root.relativize(path);
            entry.put("path", normalizeSeparators(relative.toString()));
            entry.put("name", path.getFileName() == null ? normalizeSeparators(relative.toString()) : path.getFileName().toString());
            entry.put("directory", Files.isDirectory(path));
            entry.put("depth", relative.getNameCount());
            tree.add(entry);
        }
        return tree;
    }

    private String buildSummary(List<Path> sorted) {
        int directories = countDirectories(sorted);
        int files = countFiles(sorted);
        return "共发现 " + directories + " 个目录，" + files + " 个文件";
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "(no_extension)";
        }
        return fileName.substring(dotIndex).toLowerCase();
    }

    private String relativize(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (normalizedRoot.equals(normalizedPath)) {
            return ".";
        }
        return normalizeSeparators(normalizedRoot.relativize(normalizedPath).toString());
    }

    private String normalizeSeparators(String value) {
        return value == null ? "" : value.replace("\\", "/");
    }

    private int readInteger(ToolInvocation request, String fieldName, int defaultValue) {
        Object value = request.getArguments().get(fieldName);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String readString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Map<String, Object> integerProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }
}

