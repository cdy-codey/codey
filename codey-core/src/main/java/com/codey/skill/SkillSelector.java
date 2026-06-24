package com.codey.skill;

import com.codey.meta.IdentityMatcher;
import com.codey.task.GenerateTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 根据任务配置选择当前应生效的技能。
 */
public class SkillSelector {
    private final SkillRegistry skillRegistry;

    public SkillSelector(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public Optional<Skill> select(GenerateTask task) {
        String skillName = task.getSkillName();
        if (skillName == null || skillName.trim().isEmpty()) {
            return Optional.empty();
        }
        Skill skill = skillRegistry.findByName(skillName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillName));
        if (!supportsTaskIdentities(skill, task)) {
            throw new IllegalArgumentException("Skill does not support session identities: " + skillName);
        }
        return Optional.of(skill);
    }

    public List<Skill> listSupported(GenerateTask task) {
        List<Skill> supported = new ArrayList<Skill>();
        for (Skill skill : skillRegistry.getAll()) {
            if (supportsTaskIdentities(skill, task)) {
                supported.add(skill);
            }
        }
        return supported;
    }

    private boolean supportsTaskIdentities(Skill skill, GenerateTask task) {
        if (skill == null || skill.definition() == null) {
            return false;
        }
        SkillDefinition definition = skill.definition();
        return IdentityMatcher.matches(
                task == null ? null : task.getIdentities(),
                definition.getSupportedIdentities(),
                definition.getIdentityMatchMode()
        );
    }
}
