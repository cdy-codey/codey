package com.codey.verify;

/**
 * 根据目标文件情况决定编辑态或完成态应走哪套校验场景。
 */
final class VerificationScenarioResolver {
    private final VerificationRuleRegistry ruleRegistry;
    private final VerificationTargetResolver targetResolver;

    VerificationScenarioResolver(VerificationRuleRegistry ruleRegistry,
                                 VerificationTargetResolver targetResolver) {
        this.ruleRegistry = ruleRegistry;
        this.targetResolver = targetResolver;
    }

    VerificationScenario resolveEditScenario(VerificationTarget target) {
        if (!target.hasTargetFile()) {
            return VerificationScenario.notApplicable("Edit verification not applicable: no concrete target file was resolved");
        }
        if (!targetResolver.supportsStructuredPageVerification(target.getTargetFile())) {
            return VerificationScenario.notApplicable("Edit verification not applicable: current target is outside page verification scope");
        }
        return VerificationScenario.run(ruleRegistry.getEditRules());
    }

    VerificationScenario resolveCompletionScenario(VerificationTarget target) {
        if (!target.hasTargetFile()) {
            return VerificationScenario.notApplicable("Completion verification not applicable: no concrete target file was resolved");
        }
        if (!targetResolver.supportsStructuredPageVerification(target.getTargetFile())) {
            return VerificationScenario.notApplicable("Completion verification not applicable: current target is outside page verification scope");
        }
        if (!target.hasApiSpecFile()) {
            return VerificationScenario.run(ruleRegistry.getEditRules());
        }
        return VerificationScenario.run(ruleRegistry.getCompletionRules());
    }
}
