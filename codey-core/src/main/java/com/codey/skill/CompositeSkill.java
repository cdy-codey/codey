package com.codey.skill;

import com.codey.meta.IdentityMatchMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 把多个独立 skill 合成为一个运行时 skill，便于主循环仍按单个 SkillDefinition 运行。
 */
public class CompositeSkill implements Skill {
    private final List<Skill> delegates;
    private final SkillDefinition definition;

    public CompositeSkill(List<Skill> skills) {
        this.delegates = skills == null ? new ArrayList<Skill>() : new ArrayList<Skill>(skills);
        this.definition = buildDefinition(this.delegates);
    }

    @Override
    public SkillDefinition definition() {
        return definition;
    }

    public List<Skill> getDelegates() {
        return new ArrayList<Skill>(delegates);
    }

    private SkillDefinition buildDefinition(List<Skill> skills) {
        SkillDefinition merged = new SkillDefinition();
        merged.setName(joinNames(skills));
        merged.setDescription(joinSections(skills, true));
        merged.setSystemPromptTemplate(joinSections(skills, false));
        merged.setAllowedToolGroups(mergeToolGroups(skills));
        merged.setAllowedToolBundles(mergeToolBundles(skills));
        merged.setSupportedIdentities(mergeSupportedIdentities(skills));
        merged.setIdentityMatchMode(IdentityMatchMode.ANY);
        merged.setOutputContract(joinOutputContracts(skills));
        merged.setMaxLoopCount(resolveMaxLoopCount(skills));
        merged.setAutoCompleteOnVerifiedWrite(hasAutoCompleteOnVerifiedWrite(skills));
        merged.setAutoCompleteSummary(resolveAutoCompleteSummary(skills));
        merged.setContextFileName(resolveContextFileName(skills));
        return merged;
    }

    private String joinNames(List<Skill> skills) {
        List<String> names = new ArrayList<String>();
        for (Skill skill : skills) {
            String name = skill == null || skill.definition() == null ? null : skill.definition().getName();
            if (!isBlank(name)) {
                names.add(name.trim());
            }
        }
        return String.join(",", names);
    }

    private String joinSections(List<Skill> skills, boolean useDescription) {
        List<String> sections = new ArrayList<String>();
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null) {
                continue;
            }
            SkillDefinition definition = skill.definition();
            String name = isBlank(definition.getName()) ? "unnamed-skill" : definition.getName().trim();
            String content = useDescription ? definition.getDescription() : definition.getSystemPromptTemplate();
            if (isBlank(content)) {
                continue;
            }
            sections.add("[" + name + "]\n" + content.trim());
        }
        return String.join("\n\n", sections);
    }

    private List<String> mergeToolGroups(List<Skill> skills) {
        Set<String> values = new LinkedHashSet<String>();
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null || skill.definition().getAllowedToolGroups() == null) {
                continue;
            }
            for (String value : skill.definition().getAllowedToolGroups()) {
                if (!isBlank(value)) {
                    values.add(value.trim());
                }
            }
        }
        return new ArrayList<String>(values);
    }

    private List<String> mergeToolBundles(List<Skill> skills) {
        Set<String> values = new LinkedHashSet<String>();
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null || skill.definition().getAllowedToolBundles() == null) {
                continue;
            }
            for (String value : skill.definition().getAllowedToolBundles()) {
                if (!isBlank(value)) {
                    values.add(value.trim());
                }
            }
        }
        return new ArrayList<String>(values);
    }

    private List<String> mergeSupportedIdentities(List<Skill> skills) {
        Set<String> values = new LinkedHashSet<String>();
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null || skill.definition().getSupportedIdentities() == null) {
                continue;
            }
            for (String value : skill.definition().getSupportedIdentities()) {
                if (!isBlank(value)) {
                    values.add(value.trim());
                }
            }
        }
        return new ArrayList<String>(values);
    }

    private String joinOutputContracts(List<Skill> skills) {
        List<String> sections = new ArrayList<String>();
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null || isBlank(skill.definition().getOutputContract())) {
                continue;
            }
            sections.add(skill.definition().getOutputContract().trim());
        }
        return String.join("\n", sections);
    }

    private int resolveMaxLoopCount(List<Skill> skills) {
        int maxLoopCount = 0;
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null) {
                continue;
            }
            maxLoopCount = Math.max(maxLoopCount, skill.definition().getMaxLoopCount());
        }
        return maxLoopCount;
    }

    private boolean hasAutoCompleteOnVerifiedWrite(List<Skill> skills) {
        for (Skill skill : skills) {
            if (skill != null && skill.definition() != null && skill.definition().isAutoCompleteOnVerifiedWrite()) {
                return true;
            }
        }
        return false;
    }

    private String resolveAutoCompleteSummary(List<Skill> skills) {
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null) {
                continue;
            }
            String summary = skill.definition().getAutoCompleteSummary();
            if (!isBlank(summary)) {
                return summary.trim();
            }
        }
        return null;
    }

    private String resolveContextFileName(List<Skill> skills) {
        for (Skill skill : skills) {
            if (skill == null || skill.definition() == null) {
                continue;
            }
            String fileName = skill.definition().getContextFileName();
            if (!isBlank(fileName)) {
                return fileName.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
