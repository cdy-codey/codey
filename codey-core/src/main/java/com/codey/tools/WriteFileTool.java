package com.codey.tools;

import com.codey.infra.ModelToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 以 UTF-8 编码写入文件，并在需要时自动创建父目录。
 */
public class WriteFileTool extends AbstractWorkspaceTool {
    private static final int MAX_PREVIEW_LINES = 16;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String displayName() {
        return "写入文件";
    }

    @Override
    public String description() {
        return "Write a UTF-8 text file. Missing parent directories are created automatically.";
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
            String pathValue = readRequiredString(request, "path");
            String content = readRequiredString(request, "content");
            Path target = context.resolvePath(pathValue);
            boolean existedBefore = Files.exists(target);
            String originalContent = existedBefore
                    ? new String(Files.readAllBytes(target), StandardCharsets.UTF_8)
                    : "";
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("path", relativize(context, target));
            payload.put("created", !existedBefore);
            payload.put("bytes", content.getBytes(StandardCharsets.UTF_8).length);
            payload.put("summary", existedBefore ? "已更新文件内容" : "已新建文件");
            payload.put("diffSummary", FileMutationSupport.buildDiffSummary(originalContent, content));
            payload.put("preview", FileMutationSupport.buildPreview(originalContent, content, MAX_PREVIEW_LINES));
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return ToolResult.ok("Write file success:\n" + result, existedBefore ? "已保存文件" : "已新建文件");
        } catch (Exception exception) {
            return ToolResult.fail("写入文件失败：" + exception.getMessage());
        }
    }

    private String relativize(WorkspaceToolContext context, Path target) {
        Path root = context.getWorkspaceRoot();
        if (root == null) {
            return target.toString();
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (normalizedRoot.equals(normalizedTarget)) {
            return ".";
        }
        return normalizedRoot.relativize(normalizedTarget).toString();
    }

    @Override
    public ToolCapability capability() {
        return ToolCapability.standard();
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty("Target file path."));
        properties.put("content", stringProperty("Full file content."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path", "content"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private String readRequiredString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return String.valueOf(value);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
