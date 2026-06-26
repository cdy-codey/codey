package com.codey.mcp;

import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.*;

import com.codey.infra.ModelToolDefinition;
import com.codey.infra.WorkspaceEntry;
import com.codey.infra.WorkspaceListResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 列出工作区或指定子目录，并返回结构化目录信息。
 */
public class ListWorkspaceTool extends AbstractWorkspaceTool {
    private final WorkspaceToolPayloadFormatter payloadFormatter = new WorkspaceToolPayloadFormatter();

    public ListWorkspaceTool(com.codey.infra.WorkspaceGateway workspaceGateway) {
    }

    @Override
    public String name() {
        return "list_workspace";
    }

    @Override
    public String displayName() {
        return "查看工作区";
    }

    @Override
    public String description() {
        return "List the workspace or a subdirectory and return structured directory information.";
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
            Path root = resolveRoot(context, readString(request, "pathHint"));
            int maxDepth = normalizePositive(readInteger(request, "maxDepth"), 1);
            int limit = normalizePositive(readInteger(request, "limit"), 50);
            boolean includeHidden = Boolean.TRUE.equals(readBoolean(request, "includeHidden"));
            List<Path> discovered;
            try (Stream<Path> stream = Files.walk(root, maxDepth)) {
                discovered = stream
                        .filter(path -> !path.equals(root))
                        .filter(path -> includeHidden || !isHiddenPath(path))
                        .sorted(Comparator
                                .comparing((Path path) -> !Files.isDirectory(path))
                                .thenComparing(path -> context.relativize(path).toLowerCase()))
                        .collect(Collectors.toList());
            }

            List<WorkspaceEntry> entries = new ArrayList<WorkspaceEntry>();
            for (Path path : discovered.subList(0, Math.min(discovered.size(), limit))) {
                WorkspaceEntry entry = new WorkspaceEntry();
                entry.setName(path.getFileName() == null ? context.relativize(path) : path.getFileName().toString());
                entry.setPath(context.relativize(path));
                entry.setDirectory(Files.isDirectory(path));
                entry.setDepth(computeDepth(context.relativize(path)));
                if (!Files.isDirectory(path)) {
                    entry.setSizeBytes(Long.valueOf(Files.size(path)));
                }
                entries.add(entry);
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
            // 回显真实查看目录，避免模型误以为所有列表结果都来自当前目录。
            String visibleRoot = context.relativize(root);
            result.setPath(visibleRoot);
            result.setRoot(visibleRoot);
            result.setMaxDepth(maxDepth);
            result.setLimit(limit);
            result.setReturnedCount(entries.size());
            result.setTotalCount(discovered.size());
            result.setTotalDiscovered(discovered.size());
            result.setDirectoryCount(directoryCount);
            result.setFileCount(fileCount);
            result.setTruncated(discovered.size() > limit);
            result.setSummary("path=" + visibleRoot
                    + ", directories=" + directoryCount
                    + ", files=" + fileCount
                    + ", total=" + discovered.size());
            result.setEntries(entries);
            return ToolResult.ok(
                    "Workspace list result:\n" + payloadFormatter.formatWorkspaceListResult(result),
                    "已返回目录内容"
            );
        } catch (Exception exception) {
            return ToolResult.fail("查看工作区失败：" + exception.getMessage());
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
        properties.put("pathHint", stringProperty("Optional subdirectory path hint relative to the current working directory. Defaults to the current working directory."));
        properties.put("limit", integerProperty("Maximum number of entries to return."));
        properties.put("maxDepth", integerProperty("Maximum traversal depth. Default is 1."));
        properties.put("includeHidden", booleanProperty("Whether hidden files and directories are included."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList());
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private String readString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInteger(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Boolean readBoolean(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private Path resolveRoot(WorkspaceToolContext context, String pathHint) {
        if (context == null) {
            throw new IllegalStateException("workspace context is required");
        }
        if (pathHint == null || pathHint.trim().isEmpty() || ".".equals(pathHint.trim())) {
            return context.resolvePath(".");
        }
        return context.resolvePath(pathHint);
    }

    private int normalizePositive(Integer value, int defaultValue) {
        return value == null || value.intValue() <= 0 ? defaultValue : value.intValue();
    }

    private boolean isHiddenPath(Path path) {
        try {
            if (Files.isHidden(path)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        Path name = path.getFileName();
        return name != null && name.toString().startsWith(".");
    }

    private int computeDepth(String path) {
        if (path == null || path.trim().isEmpty() || ".".equals(path.trim())) {
            return 0;
        }
        return path.replace("\\", "/").split("/").length;
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

    private Map<String, Object> booleanProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "boolean");
        property.put("description", description);
        return property;
    }
}
