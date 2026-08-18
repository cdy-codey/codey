package com.codey.web.form;

import com.codey.client.FormContext;
import com.codey.client.FormProvider;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 采购申请表单的表单模式提供者。
 * 通过独立的 {@link ProcurementFormContextBuilder} 构建结构化表单上下文，
 * 字段定义与实体注解保持同步，前端只需传 formName=procurement-form 即可注入。
 */
@Component
public class ProcurementFormProvider implements FormProvider {

    @Resource
    private ProcurementFormContextBuilder formContextBuilder;

    @Override
    public String formName() {
        return "procurement-form";
    }

    @Override
    public FormContext formContext() {
        return formContextBuilder.build();
    }

    @Override
    public String skillName() {
        // 表单模式只注入采购表单业务 skill，不再混入 ui-json-render-agent 等展示类 skill
        return "procurement-form-agent";
    }
}
