package com.codey.skill;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 技能注册表。
 */
public class SkillRegistry {
    private final Map<String, Skill> skills = new LinkedHashMap<String, Skill>();

    public SkillRegistry(List<Skill> skillList) {
        if (skillList != null) {
            for (Skill skill : skillList) {
                register(skill);
            }
        }
    }

    public static SkillRegistry fromDirectory(Path skillDirectory) {
        YamlSkillLoader loader = new YamlSkillLoader();
        List<Skill> loadedSkills = new java.util.ArrayList<Skill>(loader.loadFromDirectory(skillDirectory));
        return new SkillRegistry(loadedSkills);
    }

    public void register(Skill skill) {
        skills.put(skill.definition().getName(), skill);
    }

    public Optional<Skill> findByName(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<Skill> getAll() {
        return new ArrayList<Skill>(skills.values());
    }
}
