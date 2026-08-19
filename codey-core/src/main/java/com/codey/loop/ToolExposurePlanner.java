package com.codey.loop;

import com.codey.meta.IdentityMatcher;
import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.codey.tool.ToolSpec;
import com.codey.tools.ToolRegistry;
import com.codey.tool.ToolMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * 参考成熟引擎的工具目录策略：
 * 先暴露读取/搜索工具，拿到上下文后再放开 edit_code。
 */
public class ToolExposurePlanner {
    private final ToolRegistry toolRegistry;

    public ToolExposurePlanner() {
        this(null);
    }

    public ToolExposurePlanner(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public List<String> selectVisibleTools(AgentSession session, SkillDefinition skill) {
        if (toolRegistry == null) {
            return new ArrayList<String>();
        }
        List<String> visibleTools = new ArrayList<String>();
        for (ToolSpec tool : candidateTools(skill)) {
            if (supportsSessionIdentities(tool, session)
                    && matchesSkillMetadata(tool, skill)) {
                visibleTools.add(tool.descriptor().getName());
            }
        }
        return visibleTools;
    }

    private List<ToolSpec> candidateTools(SkillDefinition skill) {
        if (toolRegistry == null) {
            return new ArrayList<ToolSpec>();
        }
        if (skill == null) {
            return toolRegistry.getAllTools();
        }
        if (!hasScopedToolSelection(skill)) {
            return new ArrayList<ToolSpec>();
        }
        return toolRegistry.getAllTools();
    }

    private boolean hasScopedToolSelection(SkillDefinition skill) {
        if (skill == null) {
            return false;
        }
        return (skill.getAllowedToolGroups() != null && !skill.getAllowedToolGroups().isEmpty())
                || (skill.getAllowedToolBundles() != null && !skill.getAllowedToolBundles().isEmpty());
    }

    private boolean supportsSessionIdentities(ToolSpec tool, AgentSession session) {
        ToolMetadata metadata = tool == null ? null : tool.metadata();
        return IdentityMatcher.matches(
                session == null ? null : session.getIdentities(),
                metadata == null ? null : metadata.getSupportedIdentities(),
                metadata == null ? null : metadata.getIdentityMatchMode()
        );
    }

    private boolean matchesSkillMetadata(ToolSpec tool, SkillDefinition skill) {
        if (tool == null || skill == null) {
            return true;
        }
        ToolMetadata metadata = tool.metadata();
        if (metadata == null) {
            return true;
        }
        if (!matchesGroup(skill, metadata)) {
            return false;
        }
        return matchesBundle(skill, metadata);
    }

    private boolean matchesGroup(SkillDefinition skill, ToolMetadata metadata) {
        if (skill.getAllowedToolGroups() == null || skill.getAllowedToolGroups().isEmpty()) {
            return true;
        }
        String group = metadata.getGroup();
        if (group == null || group.trim().isEmpty()) {
            return false;
        }
        return containsIgnoreCase(skill.getAllowedToolGroups(), group);
    }

    private boolean matchesBundle(SkillDefinition skill, ToolMetadata metadata) {
        if (skill.getAllowedToolBundles() == null || skill.getAllowedToolBundles().isEmpty()) {
            return true;
        }
        String bundle = metadata.getBundle();
        if (bundle == null || bundle.trim().isEmpty()) {
            return false;
        }
        return containsIgnoreCase(skill.getAllowedToolBundles(), bundle);
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.trim().equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }
}
