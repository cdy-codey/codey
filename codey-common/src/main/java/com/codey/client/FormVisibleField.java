package com.codey.client;

/**
 * 表单可见字段描述。
 * 前端以对象形式（{@code { label, field }}）传入可见字段时，同时携带界面中文名与实体字段名，
 * 供后端按 {@link #field} 过滤序列化字段，并用 {@link #label} 覆盖字段中文描述。
 */
public class FormVisibleField {
    /** 界面中文名 */
    private String label;
    /** 实体字段名，支持点分路径约束嵌套子字段（如 targetList.targetName） */
    private String field;

    public FormVisibleField() {
    }

    public FormVisibleField(String label, String field) {
        this.label = label;
        this.field = field;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
