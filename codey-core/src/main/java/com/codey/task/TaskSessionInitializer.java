package com.codey.task;

import com.codey.client.FormContext;
import com.codey.config.AgentSession;
import com.codey.form.FormSelector;
import com.codey.skill.Skill;

/**
 * 负责根据任务输入初始化运行时会话，收敛 task -> session 的映射逻辑。
 */
public class TaskSessionInitializer {
    private final FormSelector formSelector;

    public TaskSessionInitializer() {
        this(null);
    }

    public TaskSessionInitializer(FormSelector formSelector) {
        this.formSelector = formSelector;
    }

    public AgentSession initialize(GenerateTask task, Skill skill) {
        AgentSession session = new AgentSession(task == null ? null : task.getSessionId());
        if (task == null) {
            return session;
        }
        if (skill != null && skill.definition() != null) {
            session.setSkillName(skill.definition().getName());
        }
        session.setModelConfig(task.getModelConfig());
        String wd = task.getWorkingDirectory();
        if (wd == null || wd.trim().isEmpty()) {
            wd = session.getSessionId();
        }
        session.setWorkingDirectory(wd);
        session.setTargetPagePath(task.getPagePath());
        session.setApiSpecPath(task.getApiSpecPath());
        session.setUserGoal(task.getGoal());
        session.setIdentities(task.getIdentities());
        session.setTenantId(task.getTenantId());
        // 页面/API 仍属于运行时上下文，但前端主动传入的文件与说明要单独记为 userContext，
        // 这样后续工具读写不会把脏路径反灌回提示词。
        session.appendContextFile(task.getPagePath());
        session.appendContextFile(task.getApiSpecPath());
        for (String contextFile : task.getContextFiles()) {
            session.appendUserContextFile(contextFile);
        }
        for (String contextNote : task.getContextNotes()) {
            session.appendUserContextNote(contextNote);
        }
        for (String chatHistoryEntry : task.getChatHistory()) {
            session.appendChatHistory(chatHistoryEntry);
        }
        session.setCoreRules(task.getCoreRules());
        session.setSingleFileMode(task.isSingleFileMode());
        session.setIncludeThinking(task.isIncludeThinking());
        session.setFormMode(task.isFormMode());
        session.setFormContext(resolveFormContext(task));
        return session;
    }

    /**
     * 表单模式下按表单名称匹配业务提供的表单定义；未启用表单或未指定名称时返回 null。
     */
    private FormContext resolveFormContext(GenerateTask task) {
        if (task == null || !task.isFormMode()) {
            return null;
        }
        if (formSelector == null) {
            throw new IllegalStateException("表单模式缺少 FormSelector，无法按名称解析表单定义");
        }
        FormContext context = formSelector.resolve(task.getFormName());
        // 前端显式传入可见字段列表时，覆盖业务 FormProvider 的默认可见字段，过滤界面未展示的噪音字段
        if (context != null && task.getFormVisibleFields() != null && !task.getFormVisibleFields().isEmpty()) {
            context.setVisibleFields(task.getFormVisibleFields());
        }
        return context;
    }
}
