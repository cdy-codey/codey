package com.codey.loop;

import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.codey.tool.ToolInvocation;

/**
 * 在工具执行前只做技能侧的可见工具校验。
 */
public class ToolAccessController {
    private final ToolExposurePlanner toolExposurePlanner;

    public ToolAccessController() {
        this(null);
    }

    public ToolAccessController(com.codey.tools.ToolRegistry toolRegistry) {
        this.toolExposurePlanner = new ToolExposurePlanner(toolRegistry);
    }

    public boolean isAllowed(SkillDefinition skill, ToolInvocation request, AgentSession session) {
        if (skill != null && !toolExposurePlanner.selectVisibleTools(session, skill).contains(request.getToolName())) {
            return false;
        }
        return true;
    }
}
