package com.codey.form;

import com.codey.client.FormProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 表单提供者注册表，收集业务实现的 FormProvider，按表单名称索引。
 */
public class FormRegistry {
    private final Map<String, FormProvider> providers = new LinkedHashMap<String, FormProvider>();

    public FormRegistry(List<FormProvider> providerList) {
        if (providerList != null) {
            for (FormProvider provider : providerList) {
                register(provider);
            }
        }
    }

    public void register(FormProvider provider) {
        if (provider == null || isBlank(provider.formName())) {
            throw new IllegalArgumentException("form provider formName must not be blank");
        }
        String name = provider.formName().trim();
        if (providers.containsKey(name)) {
            throw new IllegalStateException("Duplicate form provider: " + name);
        }
        providers.put(name, provider);
    }

    public Optional<FormProvider> findByName(String name) {
        if (isBlank(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(name.trim()));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
