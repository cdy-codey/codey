package com.codey.tools;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * 删除工作区内的文件或目录。
 */
public class DeleteFileTool extends AbstractWorkspaceTool {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "delete_file";
    }

    @Override
    public String displayName() {
        return "删除文件";
    }

    @Override
    public String description() {
        return "Delete files or directories in the workspace. Directories are removed recursively.";
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
            List<String> paths = readRequiredPaths(request);
            List<String> deleted = new ArrayList<String>();
            for (String pathValue : paths) {
                Path target = context.resolvePath(pathValue);
                if (!Files.exists(target)) {
                    throw new IllegalArgumentException("path does not exist: " + pathValue);
                }
                deleteRecursively(target);
                deleted.add(relativize(context, target));
            }

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("deletedCount", deleted.size());
            payload.put("paths", deleted);
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return ToolResult.ok("Delete file success:\n" + result, "已删除 " + deleted.size() + " 个项目");
        } catch (Exception exception) {
            return ToolResult.fail("删除文件失败：" + exception.getMessage());
        }
    }

    private void deleteRecursively(Path target) throws Exception {
        if (!Files.isDirectory(target)) {
            Files.delete(target);
            return;
        }
        try (Stream<Path> stream = Files.walk(target)) {
            List<Path> allPaths = stream
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            for (Path path : allPaths) {
                Files.delete(path);
            }
        }
    }

    private String relativize(WorkspaceToolContext context, Path target) {
        if (context == null) {
            return target == null ? "." : target.toString();
        }
        return context.relativize(target);
    }

    @Override
    public ToolCapability capability() {
        return ToolCapability.standard();
    }

    private List<String> readRequiredPaths(ToolInvocation request) {
        Object value = request.getArguments().get("paths");
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> rawList = (List<Object>) value;
            List<String> paths = new ArrayList<String>();
            for (Object item : rawList) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    paths.add(String.valueOf(item));
                }
            }
            if (!paths.isEmpty()) {
                return paths;
            }
        }
        throw new IllegalArgumentException("paths must not be empty");
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        Map<String, Object> arrayProperty = new LinkedHashMap<String, Object>();
        arrayProperty.put("type", "array");
        arrayProperty.put("description", "List of file or directory paths relative to the current working directory.");
        arrayProperty.put("items", stringProperty("File or directory path relative to the current working directory."));
        properties.put("paths", arrayProperty);

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("paths"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
