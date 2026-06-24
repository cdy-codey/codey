package com.codey.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

/**
 * ? YAML ?????? `AppConfig`?????????????
 */
public class AppConfigLoader {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public AppConfig load(String filePath) {
        AppConfig config = new AppConfig();
        if (isBlank(filePath)) {
            return config;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return config;
        }

        try {
            AppConfig loaded = yamlMapper.readValue(file, AppConfig.class);
            return loaded == null ? config : loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load app config: " + filePath, exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
