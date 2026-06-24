package com.codey.skill;

/**
 * 基于配置文件加载的 Skill。
 */
public class ConfigurableSkill implements Skill {
    private final SkillDefinition definition;

    public ConfigurableSkill(SkillDefinition definition) {
        this.definition = definition;
    }

    @Override
    public SkillDefinition definition() {
        return definition;
    }
}
