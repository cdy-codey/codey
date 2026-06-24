package com.codey.infra;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * 根据配置选择模型网关。
 */
public class ModelGatewayFactory {

    public ModelGateway create(ModelConfig config, ObjectMapper objectMapper) {
        return create(config, objectMapper, null);
    }

    public ModelGateway create(ModelConfig config, ObjectMapper objectMapper, Path modelInputLogRoot) {
        if (config == null || config.getProvider() == null || config.getProvider().trim().isEmpty()) {
            return new StubModelGateway();
        }

        if ("stub".equalsIgnoreCase(config.getProvider())) {
            return new StubModelGateway();
        }

        if ("http".equalsIgnoreCase(config.getProvider())) {
            validateHttpConfig(config);
            if (modelInputLogRoot != null) {
                return new HttpModelGateway(config, objectMapper, modelInputLogRoot);
            }
            return new HttpModelGateway(config, objectMapper);
        }

        throw new IllegalArgumentException("Unsupported model provider: " + config.getProvider());
    }

    private void validateHttpConfig(ModelConfig config) {
        if (config.getEndpoint() == null || config.getEndpoint().trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP model endpoint is required");
        }
        if (config.getModelName() == null || config.getModelName().trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP model name is required");
        }
    }
}
