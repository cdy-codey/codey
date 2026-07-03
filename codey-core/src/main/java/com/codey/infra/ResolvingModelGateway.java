package com.codey.infra;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * 运行期按请求解析最终模型配置的网关。
 * 会话打开时冻结一份 modelConfig 快照，后续每轮统一复用该快照；
 * 如果请求未传入，则回退到启动期默认配置；两者都缺失时直接报错。
 */
public class ResolvingModelGateway implements ModelGateway {
    private final ModelConfig defaultConfig;
    private final ObjectMapper objectMapper;
    private final Path modelInputLogRoot;

    public ResolvingModelGateway(ModelConfig defaultConfig, ObjectMapper objectMapper, Path modelInputLogRoot) {
        this.defaultConfig = ModelConfigResolver.merge(defaultConfig, null);
        this.objectMapper = objectMapper;
        this.modelInputLogRoot = modelInputLogRoot;
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        ModelConfig resolved = ModelConfigResolver.merge(defaultConfig, request == null ? null : request.getModelConfig());
        String provider = resolved == null ? null : resolved.getProvider();
        if (isBlank(provider)) {
            throw new IllegalStateException("模型配置缺失：请在 openSession 时传入 modelConfig，或在 application.yml 中配置 codey.model");
        }
        if ("stub".equalsIgnoreCase(provider)) {
            return new StubModelGateway().chat(request);
        }
        if ("http".equalsIgnoreCase(provider)) {
            validateHttpConfig(resolved);
            if (modelInputLogRoot != null) {
                return new HttpModelGateway(resolved, objectMapper, modelInputLogRoot).chat(request);
            }
            return new HttpModelGateway(resolved, objectMapper).chat(request);
        }
        throw new IllegalArgumentException("Unsupported model provider: " + provider);
    }

    private void validateHttpConfig(ModelConfig config) {
        if (config == null) {
            throw new IllegalStateException("模型配置缺失：请在 openSession 时传入 modelConfig，或在 application.yml 中配置 codey.model");
        }
        if (isBlank(config.getEndpoint())) {
            throw new IllegalArgumentException("HTTP model endpoint is required");
        }
        if (isBlank(config.getModelName())) {
            throw new IllegalArgumentException("HTTP model name is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
