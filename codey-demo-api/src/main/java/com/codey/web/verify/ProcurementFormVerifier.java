package com.codey.web.verify;

import com.codey.config.AgentSession;
import com.codey.infra.WorkspacePathSupport;
import com.codey.skill.SkillDefinition;
import com.codey.verify.Verifier;
import com.codey.verify.VerifyResult;
import com.codey.web.tool.ProcurementContextJsonValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 采购表单专用校验器：在 write_file 写入 context.json 后立即自动校验，
 * 避免模型额外再发起一次校验工具调用，减少一次往返。
 *
 * 仅对 procurement-form-agent 会话生效，其他会话保持原有默认行为。
 */
@Component
public class ProcurementFormVerifier implements Verifier {
    private static final String SKILL_NAME = "procurement-form-agent";

    private final ProcurementContextJsonValidator validator;
    private final Path workspaceRoot;

    public ProcurementFormVerifier(ProcurementContextJsonValidator validator,
                                   @Qualifier("WorkspaceRoot") Path workspaceRoot) {
        this.validator = validator;
        this.workspaceRoot = workspaceRoot;
    }

    @Override
    public VerifyResult verifyEdit(AgentSession session, SkillDefinition skill) {
        if (!isProcurementSkill(skill)) {
            return VerifyResult.notApplicable("Not a procurement form skill");
        }
        Path target = resolveContextJson(session);
        if (target == null) {
            return VerifyResult.notApplicable("Last edited file is not procurement context.json");
        }
        return verify(target);
    }

    @Override
    public VerifyResult verifyCompletion(AgentSession session, SkillDefinition skill) {
        // 完成态复用编辑后校验，确保任务结束时 context.json 仍满足结构与业务规则。
        return verifyEdit(session, skill);
    }

    private boolean isProcurementSkill(SkillDefinition skill) {
        return skill != null && skill.hasSkill(SKILL_NAME);
    }

    private Path resolveContextJson(AgentSession session) {
        if (session == null) {
            return null;
        }
        String lastEdited = session.getLastEditedFilePath();
        if (isBlank(lastEdited)) {
            return null;
        }
        String normalized = lastEdited.replace("\\", "/");
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!"context.json".equalsIgnoreCase(fileName)) {
            return null;
        }
        try {
            return WorkspacePathSupport.resolveToolPath(workspaceRoot, session.getWorkingDirectory(), lastEdited);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private VerifyResult verify(Path target) {
        ProcurementContextJsonValidator.ValidationReport report = validator.validateFile(target);
        if (report.isValid()) {
            return VerifyResult.passed("采购 context.json 校验通过");
        }
        return VerifyResult.failed("采购 context.json 校验未通过：" + validator.toPrettyJson(report));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
