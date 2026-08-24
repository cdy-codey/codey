package com.codey.mcp;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolResult;
import com.codey.tools.AbstractWorkspaceTool;
import com.codey.tools.FileMutationSupport;
import com.codey.tools.WorkspaceToolContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 以 UTF-8 编码写入文件，并在需要时自动创建父目录。
 */
public class WriteFileTool extends AbstractWorkspaceTool {
    private static final int MAX_PREVIEW_LINES = 16;

    // ObjectWriter 线程安全，复用实例避免每次写入都重建（含 JSON 工厂初始化开销）。
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.fasterxml.jackson.databind.ObjectWriter prettyWriter =
            objectMapper.writerWithDefaultPrettyPrinter();

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
            String content = readOptionalString(request, "content");
            if (content == null || content.trim().isEmpty()) {
                // content 缺失多为模型输出被截断（finish_reason=length）导致：给模型明确引导，避免无意义重试。
                return ToolResult.fail("写入文件失败：content 参数为空。请重新生成包含完整文件内容的 write_file 调用；"
                        + "若内容较长导致单次输出被截断，请调大模型 max_tokens 配置或改用 edit_file 分次更新。");
            }
            Path target = context.resolvePath(pathValue);
            boolean existedBefore = Files.exists(target);
            // 表单模式：一次性整文件替换，无需读取原文件做 diff 对比，跳过 readAllBytes 与逐行比较。
            boolean formMode = context.isFormMode();
            String originalContent = (!formMode && existedBefore)
                    ? new String(Files.readAllBytes(target), StandardCharsets.UTF_8)
                    : "";
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            // 复用同一份字节数组：写入文件与计算字节数共用，避免大内容重复编码。
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(target, contentBytes);

            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("path", relativize(context, target));
            payload.put("created", !existedBefore);
            payload.put("bytes", contentBytes.length);
            payload.put("summary", existedBefore ? "已更新文件内容" : "已新建文件");
            if (formMode) {
                // 表单模式不产出 diff 摘要与预览，减少大文件写入时的字符串处理开销。
                payload.put("diffSummary", "formMode replace, diff skipped");
                payload.put("preview", Collections.emptyList());
            } else {
                // 非表单模式：diff 摘要与预览一次行扫描产出，避免对原/新内容重复 split 与遍历。
                FileMutationSupport.DiffResult diff =
                        FileMutationSupport.buildDiff(originalContent, content, MAX_PREVIEW_LINES);
                payload.put("diffSummary", diff.getSummary());
                payload.put("preview", diff.getPreview());
            }
            String result = prettyWriter.writeValueAsString(payload);
            return ToolResult.ok("Write file success:\n" + result, existedBefore ? "已保存文件" : "已新建文件");
        } catch (Exception exception) {
            return ToolResult.fail("写入文件失败：" + exception.getMessage());
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

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", stringProperty("Target file path relative to the current working directory."));
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

    private String readOptionalString(ToolInvocation request, String fieldName) {
        Object value = request.getArguments().get(fieldName);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
