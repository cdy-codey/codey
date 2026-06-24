package com.codey.infra;

/**
 * 统一约束模型消息的四种标准角色，避免调用链路里继续混用裸字符串。
 */
public enum ModelMessageRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    private final String wireValue;

    ModelMessageRole(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static ModelMessageRole fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (ModelMessageRole role : values()) {
            if (role.wireValue.equals(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unsupported model message role: " + value);
    }
}
