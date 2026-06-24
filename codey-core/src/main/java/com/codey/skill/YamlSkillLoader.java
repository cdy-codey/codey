package com.codey.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 从 YAML 文件中加载 skill。
 */
public class YamlSkillLoader {
    private final ObjectMapper yamlMapper;

    public YamlSkillLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public List<Skill> loadFromDirectory(Path skillDirectory) {
        if (skillDirectory == null || !Files.exists(skillDirectory)) {
            return Collections.emptyList();
        }

        List<Skill> skills = new ArrayList<Skill>();
        try (Stream<Path> pathStream = Files.walk(skillDirectory)) {
            pathStream
                    .filter(Files::isRegularFile)
                    .filter(this::isYamlFile)
                    .forEach(path -> skills.add(loadFromFile(path)));
            return skills;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load skills from: " + skillDirectory, exception);
        }
    }

    public Skill loadFromFile(Path file) {
        try {
            SkillFileDefinition fileDefinition = yamlMapper.readValue(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8),
                    SkillFileDefinition.class
            );
            return new ConfigurableSkill(toSkillDefinition(fileDefinition));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load skill file: " + file, exception);
        }
    }

    private SkillDefinition toSkillDefinition(SkillFileDefinition fileDefinition) {
        SkillDefinition definition = new SkillDefinition();
        definition.setName(requireText(fileDefinition.getName(), "name"));
        definition.setDescription(fileDefinition.getDescription());
        definition.setSupportedIdentities(fileDefinition.getSupportedIdentities());
        definition.setIdentityMatchMode(fileDefinition.getIdentityMatchMode());
        definition.setAllowedToolGroups(fileDefinition.getAllowedToolGroups());
        definition.setAllowedToolBundles(fileDefinition.getAllowedToolBundles());
        definition.setMaxLoopCount(fileDefinition.getMaxLoopCount() == null ? 8 : fileDefinition.getMaxLoopCount());
        definition.setSystemPromptTemplate(resolvePrompt(fileDefinition));
        definition.setOutputContract(resolveOutputContract(fileDefinition.getOutputContract()));
        return definition;
    }

    private String resolvePrompt(SkillFileDefinition fileDefinition) {
        return requireText(fileDefinition.getSystemPromptTemplate(), "systemPromptTemplate");
    }

    private String resolveOutputContract(Map<String, Object> contract) {
        if (contract == null || contract.isEmpty()) {
            return "OpenAI tool_calls + final result JSON";
        }
        return String.valueOf(contract);
    }

    private boolean isYamlFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Skill field must not be blank: " + fieldName);
        }
        return value.trim();
    }
}
