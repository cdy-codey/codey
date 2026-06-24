package com.codey.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

/**
 * 从 YAML 文件加载模型配置，并支持命令行覆盖。
 */
public class ModelConfigLoader {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ModelConfig load(String filePath) {
        ModelConfig config = new ModelConfig();
        if (isBlank(filePath)) {
            return config;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return config;
        }

        try {
            ModelConfig loaded = yamlMapper.readValue(file, ModelConfig.class);
            return loaded == null ? config : loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load model config: " + filePath, exception);
        }
    }

    public ModelConfig applyOverrides(ModelConfig base,
                                      String provider,
                                      String endpoint,
                                      String modelName,
                                      String apiKey) {
        ModelConfig config = base == null ? new ModelConfig() : base;
        if (!isBlank(provider)) {
            config.setProvider(provider);
        }
        if (!isBlank(endpoint)) {
            config.setEndpoint(endpoint);
        }
        if (!isBlank(modelName)) {
            config.setModelName(modelName);
        }
        if (!isBlank(apiKey)) {
            config.setApiKey(apiKey);
        }
        return config;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
