package com.codey.tools;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内核内部工具容器。
 * 基础工具与扩展工具分层维护，扩展工具允许覆盖基础工具。
 */
public class ToolRegistry {
    private final Map<String, ToolSpec> builtinTools = new LinkedHashMap<String, ToolSpec>();
    private final Map<String, ToolSpec> extensionTools = new LinkedHashMap<String, ToolSpec>();
    private final ToolDefinitionAdapter definitionAdapter = new ToolDefinitionAdapter();

    public void register(ToolSpec tool) {
        registerBuiltin(tool);
    }

    public void registerBuiltin(ToolSpec tool) {
        String toolName = requireToolName(tool);
        if (builtinTools.containsKey(toolName)) {
            throw new IllegalStateException("Duplicate builtin tool: " + toolName);
        }
        builtinTools.put(toolName, tool);
    }

    public void registerExtension(ToolSpec tool) {
        String toolName = requireToolName(tool);
        if (extensionTools.containsKey(toolName)) {
            throw new IllegalStateException("Duplicate extension tool: " + toolName);
        }
        extensionTools.put(toolName, tool);
    }

    public Optional<ToolSpec> findByName(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        ToolSpec extension = extensionTools.get(toolName);
        if (extension != null) {
            return Optional.of(extension);
        }
        return Optional.ofNullable(builtinTools.get(toolName));
    }

    public Optional<ToolDescriptor> findDescriptor(String toolName) {
        Optional<ToolSpec> tool = findByName(toolName);
        if (!tool.isPresent() || tool.get().descriptor() == null) {
            return Optional.empty();
        }
        return Optional.of(tool.get().descriptor());
    }

    /**
     * UI 展示优先使用 displayName，缺失时再回退到真实工具名，避免执行标识被改写。
     */
    public String resolveDisplayName(String toolName) {
        Optional<ToolDescriptor> descriptor = findDescriptor(toolName);
        if (!descriptor.isPresent()) {
            return toolName;
        }
        String displayName = descriptor.get().getDisplayName();
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName.trim();
        }
        String name = descriptor.get().getName();
        return name == null ? toolName : name;
    }

    public List<ToolSpec> getAllTools() {
        Map<String, ToolSpec> merged = new LinkedHashMap<String, ToolSpec>(builtinTools);
        merged.putAll(extensionTools);
        return Collections.unmodifiableList(new ArrayList<ToolSpec>(merged.values()));
    }

    public List<ModelToolDefinition> getToolDefinitions(List<String> allowedToolNames) {
        List<ModelToolDefinition> definitions = new ArrayList<ModelToolDefinition>();
        if (allowedToolNames == null) {
            return definitions;
        }
        for (String toolName : allowedToolNames) {
            Optional<ToolSpec> tool = findByName(toolName);
            if (tool.isPresent()) {
                definitions.add(definitionAdapter.toModelToolDefinition(tool.get().descriptor()));
            }
        }
        return definitions;
    }

    private String requireToolName(ToolSpec tool) {
        if (tool == null || tool.descriptor() == null) {
            throw new IllegalArgumentException("tool descriptor must not be null");
        }
        ToolDescriptor descriptor = tool.descriptor();
        String toolName = descriptor.getName();
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        String displayName = descriptor.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("tool displayName must not be blank: " + toolName);
        }
        return toolName.trim();
    }

    static ToolResult adaptResult(com.codey.tool.ToolResult result) {
        if (result instanceof ToolResult) {
            return (ToolResult) result;
        }
        if (result == null) {
            return ToolResult.fail("tool returned null result");
        }
        ToolResult adapted = result.isSuccess()
                ? ToolResult.ok(result.getContent(), result.getSummary())
                : ToolResult.fail(result.getErrorMessage(), result.getSummary());
        if (result.isCompactedForContext()) {
            adapted.withContextContent(
                    result.getContentForModel(),
                    result.getSpilloverPath(),
                    result.getOriginalContentLength()
            );
        }
        return adapted;
    }
}
