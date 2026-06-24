package com.codey.meta;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一处理会话身份与 skill/tool 元数据的匹配规则。
 */
public final class IdentityMatcher {
    private IdentityMatcher() {
    }

    public static boolean matches(List<String> sessionIdentities,
                                  List<String> supportedIdentities,
                                  IdentityMatchMode matchMode) {
        Set<String> sessionSet = normalize(sessionIdentities);
        Set<String> supportedSet = normalize(supportedIdentities);
        if (sessionSet.isEmpty() || supportedSet.isEmpty()) {
            return true;
        }
        IdentityMatchMode normalizedMode = matchMode == null ? IdentityMatchMode.ANY : matchMode;
        if (IdentityMatchMode.ALL.equals(normalizedMode)) {
            return sessionSet.containsAll(supportedSet);
        }
        for (String identity : supportedSet) {
            if (sessionSet.contains(identity)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalize(List<String> values) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            normalized.add(value.trim().toLowerCase());
        }
        return normalized;
    }
}
