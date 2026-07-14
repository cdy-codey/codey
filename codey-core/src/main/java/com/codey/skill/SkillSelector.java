package com.codey.skill;

import com.codey.meta.IdentityMatcher;
import com.codey.task.GenerateTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 根据任务配置选择当前应生效的技能。
 */
public class SkillSelector {
    private final SkillRegistry skillRegistry;

    public SkillSelector(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public Optional<Skill> select(GenerateTask task) {
        List<String> requestedSkillNames = resolveRequestedSkillNames(task);
        if (requestedSkillNames.isEmpty()) {
            return Optional.empty();
        }
        List<Skill> resolvedSkills = new ArrayList<Skill>();
        for (String skillName : requestedSkillNames) {
            Skill skill = skillRegistry.findByName(skillName)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillName));
            if (!supportsTaskIdentities(skill, task)) {
                throw new IllegalArgumentException("Skill does not support session identities: " + skillName);
            }
            resolvedSkills.add(skill);
        }
        if (resolvedSkills.size() == 1) {
            return Optional.of(resolvedSkills.get(0));
        }
        return Optional.<Skill>of(new CompositeSkill(resolvedSkills));
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

    private List<String> resolveRequestedSkillNames(GenerateTask task) {
        Set<String> names = new LinkedHashSet<String>();
        if (task != null && task.getSkillNames() != null) {
            for (String item : task.getSkillNames()) {
                addSkillNames(names, item);
            }
        }
        if (names.isEmpty()) {
            addSkillNames(names, task == null ? null : task.getSkillName());
        }
        return new ArrayList<String>(names);
    }

    private void addSkillNames(Set<String> names, String rawValue) {
        if (rawValue == null) {
            return;
        }
        String[] parts = rawValue.split(",");
        for (String part : parts) {
            String normalized = part == null ? "" : part.trim();
            if (!normalized.isEmpty()) {
                names.add(normalized);
            }
        }
    }
}
