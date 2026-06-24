package com.codey.verify;

import java.util.Collections;
import java.util.List;

/**
 * 表示当前是否需要执行校验，以及应使用的规则集合。
 */
final class VerificationScenario {
    private final List<VerificationRule> rules;
    private final String notApplicableMessage;

    private VerificationScenario(List<VerificationRule> rules, String notApplicableMessage) {
        this.rules = rules == null ? Collections.<VerificationRule>emptyList() : rules;
        this.notApplicableMessage = notApplicableMessage;
    }

    static VerificationScenario run(List<VerificationRule> rules) {
        return new VerificationScenario(rules, null);
    }

    static VerificationScenario notApplicable(String notApplicableMessage) {
        return new VerificationScenario(Collections.<VerificationRule>emptyList(), notApplicableMessage);
    }

    boolean isApplicable() {
        return notApplicableMessage == null;
    }

    List<VerificationRule> getRules() {
        return rules;
    }

    String getNotApplicableMessage() {
        return notApplicableMessage;
    }
}
