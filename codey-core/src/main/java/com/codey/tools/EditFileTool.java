package com.codey.tools;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过查找替换方式执行精确的局部文件编辑。
 */
public class EditFileTool extends AbstractWorkspaceTool {
    private static final int MAX_PREVIEW_LINES = 16;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public String displayName() {
        return "编辑文件";
    }

    @Override
    public String description() {
        return "Modify files through search and replace for precise local edits.";
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
            String search = readRequiredString(request, "search");
            String replace = readRequiredString(request, "replace");
            Path target = context.resolvePath(pathValue);
            String originalContent = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);

            FileMutationSupport.ReplaceResult replaceResult =
                    FileMutationSupport.replaceWithFallbacks(originalContent, search, replace);
            if (!replaceResult.isMatched()) {
                return ToolResult.fail("编辑文件失败：未找到需要替换的原始内容");
            }

            Files.write(target, replaceResult.getUpdatedContent().getBytes(StandardCharsets.UTF_8));

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("path", relativize(context, target));
            payload.put("summary", replaceResult.getSummary());
            payload.put("diffSummary", FileMutationSupport.buildDiffSummary(originalContent, replaceResult.getUpdatedContent()));
            payload.put("preview", FileMutationSupport.buildPreview(originalContent, replaceResult.getUpdatedContent(), MAX_PREVIEW_LINES));
            String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return ToolResult.ok("Edit file success:\n" + result, "已完成文件更新");
        } catch (Exception exception) {
            return ToolResult.fail("编辑文件失败：" + exception.getMessage());
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
        properties.put("search", stringProperty("Original text to search for."));
        properties.put("replace", stringProperty("Replacement text."));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path", "search", "replace"));
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
