package com.codey.loop;

import com.codey.meta.IdentityMatcher;
import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.codey.tool.ToolSpec;
import com.codey.tools.ToolRegistry;
import com.codey.tool.ToolMetadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 参考成熟引擎的工具目录策略：
 * 先暴露读取/搜索工具，拿到上下文后再放开 edit_code。
 */
public class ToolExposurePlanner {
    /**
     * 表单模式下屏蔽的基础工具：表单模式字段信息已完整提供，AI 直接输出 JSON 结果，
     * 不需要（也不允许）通过多轮文件读写来修改结果，因此屏蔽两类工具：
     * <ul>
     *   <li>编辑文件工具：edit_file / apply_structured_patch / delete_file</li>
     *   <li>格式读写工具：read_json / search_json / edit_json</li>
     * </ul>
     */
    private static final Set<String> FORM_MODE_BLOCKED_TOOLS = new HashSet<String>(Arrays.asList(
            "edit_file",
            "apply_structured_patch",
            "delete_file",
            "read_json",
            "search_json",
            "edit_json"
    ));

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
                    && matchesSkillMetadata(tool, skill)
                    && !isBlockedInFormMode(tool, session)) {
                visibleTools.add(tool.descriptor().getName());
            }
        }
        return visibleTools;
    }

    /**
     * 表单模式下屏蔽指定的编辑文件工具与格式读写工具。
     * 命中屏蔽名单的工具既不暴露给模型，也不允许在运行时被调用。
     */
    private boolean isBlockedInFormMode(ToolSpec tool, AgentSession session) {
        if (session == null || !session.isFormMode()) {
            return false;
        }
        return tool != null
                && tool.descriptor() != null
                && FORM_MODE_BLOCKED_TOOLS.contains(tool.descriptor().getName());
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
