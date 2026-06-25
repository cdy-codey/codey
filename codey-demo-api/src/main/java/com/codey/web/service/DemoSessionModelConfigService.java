package com.codey.web.service;

import com.codey.client.RunRequest;
import com.codey.config.ModelProperties;
import com.codey.starter.SpringProperties;
import org.springframework.stereotype.Service;

/**
 * Demo 示例：把不同来源的模型配置统一组装到同一个会话请求字段。
 * 这里刻意只保留一套 `modelConfig` 传递模型，区别仅在于数据来源不同。
 */
@Service
public class DemoSessionModelConfigService {

    private final SpringProperties springProperties;

    public DemoSessionModelConfigService(SpringProperties springProperties) {
        this.springProperties = springProperties;
    }

    /**
     * 使用 application.yml 中的默认模型配置作为会话配置来源。
     */
    public RunRequest applyYamlModelConfig(RunRequest request) {
        RunRequest normalized = normalize(request);
        normalized.setModelConfig(copyModelConfig(springProperties == null ? null : springProperties.getModel()));
        return normalized;
    }

    /**
     * 使用“数据库查询结果”作为会话配置来源。
     * Demo 中先用内存模拟数据库记录，真实接入时替换成 DAO/Repository 查询即可。
     */
    public RunRequest applyDatabaseModelConfig(RunRequest request) {
        RunRequest normalized = normalize(request);
        normalized.setModelConfig(loadMockDatabaseModelConfig());
        return normalized;
    }

    private RunRequest normalize(RunRequest request) {
        return request == null ? new RunRequest() : request;
    }

    private ModelProperties loadMockDatabaseModelConfig() {
        ModelProperties databaseConfig = copyModelConfig(springProperties == null ? null : springProperties.getModel());
        if (databaseConfig == null) {
            databaseConfig = new ModelProperties();
        }
        // 这里模拟“数据库记录”覆盖统一配置对象，后续 openSession 只接收这一份 modelConfig。
        databaseConfig.setProvider("http");
        databaseConfig.setModelName("deepseek-v4-pro");
        databaseConfig.setTemperature(Double.valueOf(0.1d));
        databaseConfig.setMaxRetries(Integer.valueOf(1));
        return databaseConfig;
    }

    private ModelProperties copyModelConfig(ModelProperties source) {
        if (source == null) {
            return null;
        }
        ModelProperties copy = new ModelProperties();
        copy.setProvider(source.getProvider());
        copy.setEndpoint(source.getEndpoint());
        copy.setModelName(source.getModelName());
        copy.setApiKey(source.getApiKey());
        copy.setApiKeyEnv(source.getApiKeyEnv());
        copy.setTemperature(source.getTemperature());
        copy.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
        copy.setReadTimeoutMillis(source.getReadTimeoutMillis());
        copy.setMaxRetries(source.getMaxRetries());
        return copy;
    }
}
