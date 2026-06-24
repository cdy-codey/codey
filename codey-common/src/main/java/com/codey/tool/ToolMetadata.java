package com.codey.tool;

import com.codey.meta.IdentityMatchMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具编排所需的内部元数据。
 */
public class ToolMetadata {
    private List<String> supportedIdentities = new ArrayList<String>();
    private IdentityMatchMode identityMatchMode = IdentityMatchMode.ANY;
    private String group;
    private String bundle;
    private boolean defaultExposed = true;

    public static ToolMetadata standard() {
        return new ToolMetadata();
    }

    public List<String> getSupportedIdentities() {
        return supportedIdentities;
    }

    public void setSupportedIdentities(List<String> supportedIdentities) {
        this.supportedIdentities = copyList(supportedIdentities);
    }

    public IdentityMatchMode getIdentityMatchMode() {
        return identityMatchMode;
    }

    public void setIdentityMatchMode(IdentityMatchMode identityMatchMode) {
        this.identityMatchMode = identityMatchMode == null ? IdentityMatchMode.ANY : identityMatchMode;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public boolean isDefaultExposed() {
        return defaultExposed;
    }

    public void setDefaultExposed(boolean defaultExposed) {
        this.defaultExposed = defaultExposed;
    }

    private List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
    }
}
