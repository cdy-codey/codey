package com.codey.loop;

import com.codey.meta.IdentityMatcher;
import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.codey.tool.ToolSpec;
import com.codey.tools.ToolRegistry;
import com.codey.tool.ToolMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 参考成熟引擎的工具目录策略：
 * 先暴露读取/搜索工具，拿到上下文后再放开 edit_code。
 */
public class ToolExposurePlanner {
    /** 单表模式下需要排除的工具名 */
    private static final List<String> SINGLE_FILE_EXCLUDED_TOOLS = Arrays.asList(
            "read_file", "search_content",
            "read_json", "search_json"    // 业务层的 JSON 读取/搜索工具
    );

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
        // 单表模式：文件内容已在系统提示词中，不需要 read_file 和 search_content
        boolean singleFileMode = session != null && session.isSingleFileMode();
        List<String> visibleTools = new ArrayList<String>();
        for (ToolSpec tool : candidateTools(skill)) {
            if (singleFileMode && isSingleFileExcludedTool(tool)) {
                continue;
            }
            if (supportsSessionIdentities(tool, session) && matchesSkillMetadata(tool, skill)) {
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

    /**
     * 判断工具是否在单表模式下需要排除。
     */
    private boolean isSingleFileExcludedTool(ToolSpec tool) {
        if (tool == null || tool.descriptor() == null) {
            return false;
        }
        String name = tool.descriptor().getName();
        return name != null && SINGLE_FILE_EXCLUDED_TOOLS.contains(name);
    }
}
