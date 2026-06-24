package com.codey.tool;

import java.util.Collections;
import java.util.Map;

/**
 * 对外暴露的工具描述。
 */
public class ToolDescriptor {
    private String name;
    private String displayName;
    private String description;
    private Map<String, Object> parameters = Collections.<String, Object>emptyMap();

    public ToolDescriptor() {
    }

    public ToolDescriptor(String name, String displayName, String description, Map<String, Object> parameters) {
        setName(name);
        setDisplayName(displayName);
        setDescription(description);
        setParameters(parameters);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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
