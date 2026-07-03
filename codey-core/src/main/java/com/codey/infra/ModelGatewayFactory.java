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
        return new ResolvingModelGateway(config, objectMapper, modelInputLogRoot);
    }
}
