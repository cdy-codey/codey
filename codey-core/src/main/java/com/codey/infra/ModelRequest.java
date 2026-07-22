package com.codey.infra;

import com.codey.config.ModelProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型调用请求载荷，包含消息、工具定义和可选流监听器。
 */
public class ModelRequest {
    private String sessionId;
    private List<ModelMessage> messages = new ArrayList<ModelMessage>();
    private List<ModelToolDefinition> tools = new ArrayList<ModelToolDefinition>();
    private ModelProperties modelConfig;
    private ModelRequestType requestType = ModelRequestType.BUSINESS;
    private ModelStreamListener streamListener;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<ModelMessage> getMessages() {
        return new ArrayList<ModelMessage>(messages);
    }

    public void setMessages(List<ModelMessage> messages) {
        this.messages = messages == null ? new ArrayList<ModelMessage>() : new ArrayList<ModelMessage>(messages);
    }

    public List<ModelToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ModelToolDefinition> tools) {
        this.tools = tools == null ? new ArrayList<ModelToolDefinition>() : new ArrayList<ModelToolDefinition>(tools);
    }

    @JsonIgnore
    public ModelProperties getModelConfig() {
        return copyModelConfig(modelConfig);
    }

    public void setModelConfig(ModelProperties modelConfig) {
        this.modelConfig = copyModelConfig(modelConfig);
    }

    public ModelRequestType getRequestType() {
        return requestType == null ? ModelRequestType.BUSINESS : requestType;
    }

    public void setRequestType(ModelRequestType requestType) {
        this.requestType = requestType == null ? ModelRequestType.BUSINESS : requestType;
    }

    /**
     * 流式监听器只用于运行期回调，不应进入落盘日志或模型输入快照。
     */
    @JsonIgnore
    public ModelStreamListener getStreamListener() {
        return streamListener;
    }

    public void setStreamListener(ModelStreamListener streamListener) {
        this.streamListener = streamListener;
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
