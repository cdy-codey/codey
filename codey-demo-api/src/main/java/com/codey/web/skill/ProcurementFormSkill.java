package com.codey.web.skill;

import com.codey.skill.Skill;
import com.codey.skill.SkillDefinition;
import com.codey.meta.IdentityMatchMode;
import com.codey.web.tool.TargetFormFieldQueryTool;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * 采购申请业务页面使用的系统级 skill。
 */
@Component
public class ProcurementFormSkill implements Skill {
    @Resource
    private TargetFormFieldQueryTool targetFormFieldQueryTool;
    @Override
    public SkillDefinition definition() {
        SkillDefinition definition = new SkillDefinition();
        definition.setName("procurement-form-agent");
        definition.setDescription("采购需求标的明细技能");
        definition.setSystemPromptTemplate(
                "## 角色\n"
                        + "帮助用户填写采购申请表单，请按要求填写表单基础信息，采购明细信息，商务条款信息，确保最终提交的申请完整、合理、符合政府采购法\n"
                        + "\n"
                        + "## 核心原则\n"
                        + "- **必须遵守：编辑文件后必须调用表单校验工具。**\n"
                        + "- **先理解再行动**：理解用户意图后，再决定做什么，不要一上来就查询文件。\n"
                        + "- **聚焦可见字段**：优先处理可见字段，不可见字段酌情处理。\n"
                        + "- **够就停**：条件足够了就编辑表单。\n"
                        + "- **多调用校验**：多校验表单可以早点发现表单错误。\n"
                        + "\n"
                        + "## 工具（按场景）\n"
                        + "- **标的类型专用查询工具**：按名称模糊查询标的类型，优先用它而非普通字典。\n"
                        + "- **部门查询工具**：获取部门ID后填写，禁止自己猜测。\n"
                        + "- **采购明细历史成交列表工具**：参考相似标的时使用，必须用标的名称查。\n"
                        + "- **校验**：每次编辑文件后，必须调用表单校验工具。\n"
                        + "\n"
                        + "#表单字段规则\n"
                        + targetFormFieldQueryTool.getPrompt());
        definition.setAllowedToolBundles(Arrays.asList("workspace-core", "procurement"));
        definition.setSupportedIdentities(Arrays.asList("programming"));
        definition.setIdentityMatchMode(IdentityMatchMode.ANY);
        // 业务 skill 仍负责“改文件 + 校验 + 业务确认”，但最终正文统一通过顶层 view 返回。
        definition.setOutputContract("Update workspace context file, validate it, and return the final business-friendly result through the top-level view field.");
        definition.setMaxLoopCount(10);
        return definition;
    }
}
