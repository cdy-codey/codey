package com.codey.web.skill;

import com.codey.skill.Skill;
import com.codey.skill.SkillDefinition;
import com.codey.meta.IdentityMatchMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 采购申请业务页面使用的系统级 skill。
 */
@Component
public class ProcurementFormSkill implements Skill {

    @Override
    public SkillDefinition definition() {
        SkillDefinition definition = new SkillDefinition();
        definition.setName("procurement-form-agent");
        definition.setDescription("Assist with procurement request form completion in business demo pages.");
        definition.setSystemPromptTemplate(
                "## 角色\n"
                        + "帮助用户填写采购申请表单，确保最终提交的申请完整、合理、符合政府采购法，你的行为是简单高效不做多余的解析。\n"
                        + "\n"
                        + "## 核心原则\n"
                        + "- **必须遵守：编辑文件后必须调用表单校验工具，无例外。**\n"
                        + "- **先理解再行动**：理解用户意图后，再决定做什么，不要一上来就查询文件。\n"
                        + "- **聚焦可见字段**：优先处理可见字段，不可见字段酌情处理。\n"
                        + "- **够就停**：条件足够了就编辑表单。\n"
                        + "\n"                                       
                      );
        definition.setAllowedToolBundles(Arrays.asList("workspace-core", "procurement"));
        definition.setSupportedIdentities(Arrays.asList("programming"));
        definition.setIdentityMatchMode(IdentityMatchMode.ANY);
        // 业务 skill 仍负责“改文件 + 校验 + 业务确认”，但最终正文统一通过顶层 view 返回。
        definition.setOutputContract("Update workspace context file, validate it, and return the final business-friendly result through the top-level view field.");
        definition.setMaxLoopCount(10);
        return definition;
    }
}
