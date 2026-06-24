package com.codey.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 集中维护编辑态和完成态使用的校验规则集合。
 */
final class VerificationRuleRegistry {
    private final List<VerificationRule> editRules;
    private final List<VerificationRule> completionRules;

    VerificationRuleRegistry() {
        this(
                Arrays.<VerificationRule>asList(
                        new PageStructureRule(),
                        new EditMarkerRule()
                ),
                Arrays.<VerificationRule>asList(
                        new PageStructureRule(),
                        new ApiSpecRule(),
                        new ApiBindingRule(),
                        new PaginationRule(),
                        new ResponseFieldsRule()
                )
        );
    }

    VerificationRuleRegistry(List<VerificationRule> editRules, List<VerificationRule> completionRules) {
        this.editRules = immutableCopy(editRules);
        this.completionRules = immutableCopy(completionRules);
    }

    List<VerificationRule> getEditRules() {
        return editRules;
    }

    List<VerificationRule> getCompletionRules() {
        return completionRules;
    }

    private List<VerificationRule> immutableCopy(List<VerificationRule> rules) {
        if (rules == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<VerificationRule>(rules));
    }
}
