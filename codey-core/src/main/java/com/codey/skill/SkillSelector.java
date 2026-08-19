package com.codey.skill;

import com.codey.meta.IdentityMatcher;
import com.codey.form.FormSelector;
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
    // 表单模式下用于解析表单绑定的 skill；非表单模式或未注入时为 null
    private final FormSelector formSelector;

    public SkillSelector(SkillRegistry skillRegistry) {
        this(skillRegistry, null);
    }

    public SkillSelector(SkillRegistry skillRegistry, FormSelector formSelector) {
        this.skillRegistry = skillRegistry;
        this.formSelector = formSelector;
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
        // 表单模式以表单绑定的业务 skill 为主，同时保留前端显式传入的展示类 skill（如 ui-json-render-agent），
        // 让模型既按 Schema 填写字段，又遵循结构化展示协议输出结果。
        if (task != null && task.isFormMode() && formSelector != null) {
            Set<String> names = new LinkedHashSet<String>();
            String formSkillName = formSelector.resolveSkillName(task.getFormName());
            if (formSkillName != null && !formSkillName.trim().isEmpty()) {
                names.add(formSkillName.trim());
            }
            if (task.getSkillNames() != null) {
                for (String item : task.getSkillNames()) {
                    addSkillNames(names, item);
                }
            }
            if (names.isEmpty()) {
                addSkillNames(names, task.getSkillName());
            }
            return new ArrayList<String>(names);
        }
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
