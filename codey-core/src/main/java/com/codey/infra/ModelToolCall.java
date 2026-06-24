package com.codey.infra;

import java.util.Collections;
import java.util.Map;

/**
 * 标准 OpenAI tool call 的本地映射。
 */
public class ModelToolCall {
    private String id;
    private String name;
    private Map<String, Object> arguments = Collections.<String, Object>emptyMap();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments == null ? Collections.<String, Object>emptyMap() : arguments;
    }
}
