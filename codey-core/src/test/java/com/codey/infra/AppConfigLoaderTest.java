package com.codey.infra;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 app.yaml 中的日志配置可以被正确解析。
 */
class AppConfigLoaderTest {
    @Test
    void shouldLoadLoggingConfigFromYaml() throws IOException {
        Path yamlFile = Files.createTempFile("codey-app-config", ".yaml");
        Files.write(
                yamlFile,
                (
                        "working-directory: F:/workspace\n"
                                + "logging:\n"
                                + "  file-enabled: true\n"
                                + "  directory: runtime-logs\n"
                                + "  file-name: codey-runtime.log\n"
                                + "  archive-file-name-pattern: codey.%d{yyyy-MM-dd_HH}.log\n"
                                + "  max-history-days: 7\n"
                ).getBytes(StandardCharsets.UTF_8)
        );

        try {
            AppConfig config = new AppConfigLoader().load(yamlFile.toString());

            assertThat(config.getDefaultWorkingDirectory()).isEqualTo("F:/workspace");
            assertThat(config.getLogging().isFileEnabled()).isTrue();
            assertThat(config.getLogging().getDirectory()).isEqualTo("runtime-logs");
            assertThat(config.getLogging().getFileName()).isEqualTo("codey-runtime.log");
            assertThat(config.getLogging().getArchiveFileNamePattern()).isEqualTo("codey.%d{yyyy-MM-dd_HH}.log");
            assertThat(config.getLogging().getMaxHistoryDays()).isEqualTo(Integer.valueOf(7));
        } finally {
            Files.deleteIfExists(yamlFile);
        }
    }

    @Test
    void shouldUseDefaultLoggingValuesWhenConfigMissing() {
        AppConfig config = new AppConfigLoader().load("not-exists-app.yaml");

        assertThat(config.getLogging()).isNotNull();
        assertThat(config.getLogging().isFileEnabled()).isFalse();
        assertThat(config.getLogging().getDirectory()).isEqualTo("logs");
        assertThat(config.getLogging().getFileName()).isEqualTo("codey.log");
        assertThat(config.getLogging().getArchiveFileNamePattern()).isEqualTo("codey.%d{yyyy-MM-dd}.log");
        assertThat(config.getLogging().getMaxHistoryDays()).isEqualTo(Integer.valueOf(30));
    }
}
