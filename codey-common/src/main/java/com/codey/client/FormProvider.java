package com.codey.client;

/**
 * 表单定义提供者接口：业务实现该接口，向底层提供表单字段结构、角色、上下文与可见字段。
 * 底层按表单名称（formName）精确匹配到具体实现，前端只需传 formName，无需透传完整表单 JSON。
 */
public interface FormProvider {
    /**
     * 表单唯一名称，作为匹配键。
     */
    String formName();

    /**
     * 返回表单定义（字段、角色、上下文、可见字段）。
     */
    FormContext formContext();

    /**
     * 表单模式绑定的业务 skill 名称；为空时表单模式不注入额外业务 skill。
     * 表单模式只注入该 skill，忽略前端额外传入的其他 skill，避免拼凑无关提示词。
     */
    default String skillName() {
        return null;
    }
}
