package com.codey.verify;

import com.codey.infra.WorkspaceGateway;
import com.codey.config.AgentSession;
import com.codey.skill.SkillDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 默认校验器门面，负责串起目标解析、场景选择和规则执行流程。
 */
public class BasicVerifier implements Verifier {
    private final WorkspaceGateway workspaceGateway;
    private final ObjectMapper objectMapper;
    private final VerificationTargetResolver targetResolver;
    private final VerificationScenarioResolver scenarioResolver;
    private final VerificationPipeline verificationPipeline;

    public BasicVerifier(WorkspaceGateway workspaceGateway, ObjectMapper objectMapper) {
        this.workspaceGateway = workspaceGateway;
        this.objectMapper = objectMapper;
        this.targetResolver = new VerificationTargetResolver();
        this.scenarioResolver = new VerificationScenarioResolver(
                new VerificationRuleRegistry(),
                this.targetResolver
        );
        this.verificationPipeline = new VerificationPipeline();
    }

    BasicVerifier(WorkspaceGateway workspaceGateway,
                  ObjectMapper objectMapper,
                  VerificationTargetResolver targetResolver,
                  VerificationScenarioResolver scenarioResolver,
                  VerificationPipeline verificationPipeline) {
        this.workspaceGateway = workspaceGateway;
        this.objectMapper = objectMapper;
        this.targetResolver = targetResolver;
        this.scenarioResolver = scenarioResolver;
        this.verificationPipeline = verificationPipeline;
    }

    @Override
    public VerifyResult verifyEdit(AgentSession session, SkillDefinition skill) {
        if (session.getEditResults().isEmpty()) {
            return VerifyResult.notApplicable("Edit verification not applicable: no edit result available");
        }

        try {
            VerificationTarget target = targetResolver.resolve(session);
            VerificationScenario scenario = scenarioResolver.resolveEditScenario(target);
            if (!scenario.isApplicable()) {
                return VerifyResult.notApplicable(scenario.getNotApplicableMessage());
            }

            String pageContent = workspaceGateway.readFile(target.getTargetFile());
            return verificationPipeline.verify(scenario.getRules(), session, skill, pageContent, null);
        } catch (Exception exception) {
            return VerifyResult.failed("Verifier failed: " + exception.getMessage());
        }
    }

    @Override
    public VerifyResult verifyCompletion(AgentSession session, SkillDefinition skill) {
        try {
            VerificationTarget target = targetResolver.resolve(session);
            VerificationScenario scenario = scenarioResolver.resolveCompletionScenario(target);
            if (!scenario.isApplicable()) {
                return VerifyResult.notApplicable(scenario.getNotApplicableMessage());
            }

            JsonNode apiRoot = loadApiRoot(target);
            String pageContent = workspaceGateway.readFile(target.getTargetFile());
            VerifyResult result = verificationPipeline.verify(
                    scenario.getRules(),
                    session,
                    skill,
                    pageContent,
                    apiRoot
            );
            if (result.isPassed() && session.getEditResults().isEmpty()) {
                // 当前文件已满足目标时，完成态允许在没有编辑记录的情况下通过。
                return VerifyResult.passed("Completion verification passed: current files already satisfy target");
            }
            return result;
        } catch (Exception exception) {
            return VerifyResult.failed("Completion verification failed: " + exception.getMessage());
        }
    }

    private JsonNode loadApiRoot(VerificationTarget target) throws Exception {
        if (!target.hasApiSpecFile()) {
            return null;
        }
        String apiContent = workspaceGateway.readFile(target.getApiSpecFile());
        return objectMapper.readTree(apiContent);
    }
}
