package com.codey.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 表单模式的结构化字段上下文。
 * 由调用方传入字段定义，AI 据此理解表单结构并直接输出 JSON 结果。
 */
public class FormContext {
    /** 表单字段定义列表 */
    private List<FormField> fields = new ArrayList<FormField>();
    /** 表单级填写上下文（自然语言描述），提供字段填写所需的业务背景信息 */
    private String context;
    /** 角色扮演描述（自然语言），设定模型填写表单的行为与语气，可为空 */
    private String role;
    /** 可见字段名称列表：非空时仅序列化列表内的字段，用于过滤表单中未展示字段的噪音 */
    private List<String> visibleFields = new ArrayList<String>();

    public List<FormField> getFields() {
        return fields;
    }

    public void setFields(List<FormField> fields) {
        this.fields = fields == null ? new ArrayList<FormField>() : fields;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getVisibleFields() {
        return visibleFields;
    }

    public void setVisibleFields(List<String> visibleFields) {
        this.visibleFields = visibleFields == null ? new ArrayList<String>() : visibleFields;
    }

    /**
     * 单个表单字段定义。
     */
    public static class FormField {
        /** 字段标识，也是输出 JSON 的 key */
        private String name;
        /** 是否被 {@link FormIgnore} 注解标记：为 true 时该字段不参与序列化，由 core 底层统一过滤 */
        private boolean ignored;
        /** 字段显示名 */
        private String label;
        /** 字段类型：string / number / boolean / enum / date / array / object */
        private String type;
        /** 是否必填 */
        private boolean required;
        /** 字段说明 */
        private String description;
        /** 枚举类型时的可选值列表 */
        private List<FormOption> options = new ArrayList<FormOption>();
        /** 字段特殊规则（自由文本描述，如金额范围、格式要求、联动规则等） */
        private List<String> rules = new ArrayList<String>();
        /** 子字段列表：type=array 时表示数组元素字段，type=object 时表示对象字段，支持任意深度递归 */
        private List<FormField> children = new ArrayList<FormField>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isIgnored() {
            return ignored;
        }

        public void setIgnored(boolean ignored) {
            this.ignored = ignored;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<FormOption> getOptions() {
            return options;
        }

        public void setOptions(List<FormOption> options) {
            this.options = options == null ? new ArrayList<FormOption>() : options;
        }

        public List<String> getRules() {
            return rules;
        }

        public void setRules(List<String> rules) {
            this.rules = rules == null ? new ArrayList<String>() : rules;
        }

        public List<FormField> getChildren() {
            return children;
        }

        public void setChildren(List<FormField> children) {
            this.children = children == null ? new ArrayList<FormField>() : children;
        }
    }

    /**
     * 枚举选项定义，同时携带枚举值与中文说明。
     */
    public static class FormOption {
        /** 枚举值 */
        private String value;
        /** 枚举项中文说明 */
        private String label;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
