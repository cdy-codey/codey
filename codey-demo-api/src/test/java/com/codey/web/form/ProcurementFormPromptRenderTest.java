package com.codey.web.form;

import com.codey.client.FormContext;
import com.codey.config.AgentSession;
import com.codey.loop.PromptAssembler;
import com.codey.web.tool.TargetFormFieldQueryTool;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * 临时脚手架：用真实采购表单上下文（ProcurementFormContextBuilder）渲染表单模式注入后的最终 system prompt 到文件。
 */
public class ProcurementFormPromptRenderTest {

    @Test
    public void renderRealFormModePromptToFile() throws Exception {
        // 复用业务真实表单构建器，字段定义与实体注解保持一致。
        TargetFormFieldQueryTool tool = new TargetFormFieldQueryTool();
        ProcurementFormContextBuilder builder = new ProcurementFormContextBuilder();
        // 测试环境无 Spring 容器，手动注入 @Resource 依赖。
        Field toolField = ProcurementFormContextBuilder.class.getDeclaredField("targetFormFieldQueryTool");
        toolField.setAccessible(true);
        toolField.set(builder, tool);

        FormContext context = builder.build();

        AgentSession session = new AgentSession("render-procurement");
        session.setFormMode(true);
        session.setFormContext(context);

        PromptAssembler assembler = new PromptAssembler();
        String systemPrompt = assembler.buildSystemPrompt(session, null, Collections.<String>emptyList());

        Files.write(Paths.get("F:\\cdy\\codey\\form-mode-prompt-sample.md"),
                systemPrompt.getBytes(StandardCharsets.UTF_8));
    }
}
