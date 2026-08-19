package com.codey.skill;

import com.codey.meta.IdentityMatchMode;
import com.codey.resource.PromptResourceReader;

import java.util.Arrays;

/**
 * 内置的 UI JSON 展示规则 skill。
 * 只定义结构化展示协议，供上层按 skillName 显式启用，不承载业务领域语义。
 * 展示协议内容维护在 prompts/skills/ui-json-render.md，避免 Java 字符串拼接导致的格式问题。
 */
public class UiJsonRenderSkill implements Skill {

    /** 从 md 资源加载的展示协议模板，类加载时读取一次。 */
    private static final String SYSTEM_PROMPT_TEMPLATE =
            PromptResourceReader.readResource("prompts/skills/ui-json-render.md");

    @Override
    public SkillDefinition definition() {
        SkillDefinition definition = new SkillDefinition();
        definition.setName("ui-json-render-agent");
        definition.setDescription("Define UI JSON rendering contracts. Invoke when upper layers explicitly need structured JSON views such as form_data or diff_data.");
        definition.setSystemPromptTemplate(SYSTEM_PROMPT_TEMPLATE);
        definition.setSupportedIdentities(Arrays.asList("workspace-core"));
        definition.setIdentityMatchMode(IdentityMatchMode.ANY);
        definition.setOutputContract("Return the final answer through the top-level view field. Use text/form_data/diff_data views as appropriate.");
        definition.setMaxLoopCount(8);
        return definition;
    }
}
