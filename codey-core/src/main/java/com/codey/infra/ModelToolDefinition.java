package com.codey.infra;

import java.util.Collections;
import java.util.Map;

/**
 * 标准 OpenAI function tool 定义。
 */
public class ModelToolDefinition {
    private String name;
    private String description;
    private Map<String, Object> parameters = Collections.<String, Object>emptyMap();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters == null ? Collections.<String, Object>emptyMap() : parameters;
    }
}
