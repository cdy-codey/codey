package com.codey.tools;

import com.codey.infra.ModelToolDefinition;
import com.codey.tool.ToolDescriptor;

/**
 * 将 common descriptor 转换为模型侧工具定义。
 */
final class ToolDefinitionAdapter {

    ModelToolDefinition toModelToolDefinition(ToolDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("tool descriptor must not be null");
        }
        ModelToolDefinition definition = new ModelToolDefinition();
        definition.setName(descriptor.getName());
        definition.setDescription(descriptor.getDescription());
        definition.setParameters(descriptor.getParameters());
        return definition;
    }
}
