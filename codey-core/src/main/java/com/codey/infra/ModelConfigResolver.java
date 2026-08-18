package com.codey.infra;

import com.codey.config.ModelProperties;

/**
 * 统一模型配置的归一化与合并逻辑，确保启动期配置与会话期覆盖走同一条底层路径。
 */
public final class ModelConfigResolver {

    private ModelConfigResolver() {
    }

    /**
     * 把外部 {@link ModelProperties} 归一化为运行期 {@link ModelConfig}。
     */
    public static ModelConfig fromProperties(ModelProperties properties) {
        return merge(null, properties);
    }

    /**
     * 在基础配置之上应用会话级覆盖，输出最终运行期配置。
     */
    public static ModelConfig merge(ModelConfig baseConfig, ModelProperties overrideProperties) {
        ModelConfig resolved = copy(baseConfig);
        applyProperties(resolved, overrideProperties);
        resolved.setApiKey(resolveApiKey(resolved));
        return resolved;
    }

    private static ModelConfig copy(ModelConfig source) {
        ModelConfig copy = new ModelConfig();
        if (source == null) {
            return copy;
        }
        copy.setProvider(source.getProvider());
        copy.setEndpoint(source.getEndpoint());
        copy.setApiKey(source.getApiKey());
        copy.setApiKeyEnv(source.getApiKeyEnv());
        copy.setModelName(source.getModelName());
        copy.setTemperature(source.getTemperature());
        copy.setDebugEnabled(source.isDebugEnabled());
        copy.setDebugDir(source.getDebugDir());
        copy.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
        copy.setReadTimeoutMillis(source.getReadTimeoutMillis());
        copy.setMaxRetries(source.getMaxRetries());
        copy.setMaxTokens(source.getMaxTokens());
        return copy;
    }

    private static void applyProperties(ModelConfig target, ModelProperties properties) {
        if (target == null || properties == null) {
            return;
        }
        if (!isBlank(properties.getProvider())) {
            target.setProvider(properties.getProvider());
        }
        if (!isBlank(properties.getEndpoint())) {
            target.setEndpoint(properties.getEndpoint());
        }
        if (!isBlank(properties.getModelName())) {
            target.setModelName(properties.getModelName());
        }
        if (!isBlank(properties.getApiKey())) {
            target.setApiKey(properties.getApiKey());
        }
        if (!isBlank(properties.getApiKeyEnv())) {
            target.setApiKeyEnv(properties.getApiKeyEnv());
        }
        if (properties.getTemperature() != null) {
            target.setTemperature(properties.getTemperature());
        }
        if (properties.getConnectTimeoutMillis() != null) {
            target.setConnectTimeoutMillis(properties.getConnectTimeoutMillis().intValue());
        }
        if (properties.getReadTimeoutMillis() != null) {
            target.setReadTimeoutMillis(properties.getReadTimeoutMillis().intValue());
        }
        if (properties.getMaxRetries() != null) {
            target.setMaxRetries(properties.getMaxRetries().intValue());
        }
        if (properties.getMaxTokens() != null) {
            target.setMaxTokens(properties.getMaxTokens());
        }
    }

    private static String resolveApiKey(ModelConfig config) {
        if (config == null) {
            return null;
        }
        if (!isBlank(config.getApiKey())) {
            return config.getApiKey();
        }
        if (!isBlank(config.getApiKeyEnv())) {
            return System.getenv(config.getApiKeyEnv());
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
