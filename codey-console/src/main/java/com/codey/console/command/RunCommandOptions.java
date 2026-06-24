package com.codey.console.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ? console ????????????????
 */
final class RunCommandOptions {
    private final String skillName;
    private final boolean interactiveMode;
    private final boolean verifyModelOnly;
    private final String workingDirectory;
    private final String appConfigPath;
    private final List<String> contextFiles;
    private final List<String> contextNotes;
    private final List<String> identities;
    private final String skillsDir;
    private final String modelConfigPath;
    private final String modelProvider;
    private final String modelEndpoint;
    private final String modelName;
    private final String modelApiKey;
    private final Boolean modelDebugEnabled;
    private final String modelDebugDir;

    RunCommandOptions(String skillName,
                      boolean interactiveMode,
                      boolean verifyModelOnly,
                      String workingDirectory,
                      String appConfigPath,
                      List<String> contextFiles,
                      List<String> contextNotes,
                      List<String> identities,
                      String skillsDir,
                      String modelConfigPath,
                      String modelProvider,
                      String modelEndpoint,
                      String modelName,
                      String modelApiKey,
                      Boolean modelDebugEnabled,
                      String modelDebugDir) {
        this.skillName = skillName;
        this.interactiveMode = interactiveMode;
        this.verifyModelOnly = verifyModelOnly;
        this.workingDirectory = workingDirectory;
        this.appConfigPath = appConfigPath;
        this.contextFiles = immutableCopy(contextFiles);
        this.contextNotes = immutableCopy(contextNotes);
        this.identities = immutableCopy(identities);
        this.skillsDir = skillsDir;
        this.modelConfigPath = modelConfigPath;
        this.modelProvider = modelProvider;
        this.modelEndpoint = modelEndpoint;
        this.modelName = modelName;
        this.modelApiKey = modelApiKey;
        this.modelDebugEnabled = modelDebugEnabled;
        this.modelDebugDir = modelDebugDir;
    }

    String getSkillName() {
        return skillName;
    }

    boolean isInteractiveMode() {
        return interactiveMode;
    }

    boolean isVerifyModelOnly() {
        return verifyModelOnly;
    }

    String getWorkingDirectory() {
        return workingDirectory;
    }

    String getAppConfigPath() {
        return appConfigPath;
    }

    List<String> getContextFiles() {
        return contextFiles;
    }

    List<String> getContextNotes() {
        return contextNotes;
    }

    List<String> getIdentities() {
        return identities;
    }

    String getSkillsDir() {
        return skillsDir;
    }

    String getModelConfigPath() {
        return modelConfigPath;
    }

    String getModelProvider() {
        return modelProvider;
    }

    String getModelEndpoint() {
        return modelEndpoint;
    }

    String getModelName() {
        return modelName;
    }

    String getModelApiKey() {
        return modelApiKey;
    }

    Boolean getModelDebugEnabled() {
        return modelDebugEnabled;
    }

    String getModelDebugDir() {
        return modelDebugDir;
    }

    private List<String> immutableCopy(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
