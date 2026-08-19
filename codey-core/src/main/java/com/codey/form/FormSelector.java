package com.codey.form;

import com.codey.client.FormContext;
import com.codey.client.FormProvider;

/**
 * 根据表单名称精确匹配业务提供的表单定义，供会话初始化时解析 FormContext。
 */
public class FormSelector {
    private final FormRegistry formRegistry;

    public FormSelector(FormRegistry formRegistry) {
        this.formRegistry = formRegistry;
    }

    /**
     * 按名称解析表单定义；名称为空时返回 null（未启用表单或未指定表单名）。
     */
    public FormContext resolve(String formName) {
        if (formName == null || formName.trim().isEmpty()) {
            return null;
        }
        FormProvider provider = formRegistry.findByName(formName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown form: " + formName));
        return provider.formContext();
    }

    /**
     * 按名称解析表单绑定的业务 skill 名称；名称为空或未声明时返回 null。
     * 与 {@link #resolve(String)} 保持同一查找口径，避免表单与 skill 解析不一致。
     */
    public String resolveSkillName(String formName) {
        if (formName == null || formName.trim().isEmpty()) {
            return null;
        }
        FormProvider provider = formRegistry.findByName(formName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown form: " + formName));
        return provider.skillName();
    }
}
