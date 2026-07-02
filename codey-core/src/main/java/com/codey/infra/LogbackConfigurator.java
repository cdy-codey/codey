package com.codey.infra;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 根据应用配置以编程方式初始化 logback，避免额外维护 XML 配置文件。
 */
public class LogbackConfigurator {
    private static final String DEFAULT_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";
    private static final String DEFAULT_DIRECTORY = "logs";
    private static final String DEFAULT_FILE_NAME = "codey.log";
    private static final String DEFAULT_ARCHIVE_PATTERN = "codey.%d{yyyy-MM-dd}.log";
    private static final int DEFAULT_MAX_HISTORY_DAYS = 30;

    public void configure(AppConfig appConfig) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        AppConfig.LoggingConfig loggingConfig = appConfig == null ? null : appConfig.getLogging();
        PatternLayoutEncoder encoder = createEncoder(context, resolvePattern(loggingConfig));

        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(createConsoleAppender(context, encoder));

        if (loggingConfig != null && loggingConfig.isFileEnabled()) {
            rootLogger.addAppender(createFileAppender(context, loggingConfig, encoder));
        }
    }

    private PatternLayoutEncoder createEncoder(LoggerContext context, String pattern) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();
        return encoder;
    }

    private ConsoleAppender<ILoggingEvent> createConsoleAppender(LoggerContext context, PatternLayoutEncoder encoder) {
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName("STDOUT");
        appender.setEncoder(encoder);
        appender.start();
        return appender;
    }

    private RollingFileAppender<ILoggingEvent> createFileAppender(LoggerContext context,
                                                                  AppConfig.LoggingConfig loggingConfig,
                                                                  PatternLayoutEncoder encoder) {
        Path logDirectory = Paths.get(resolveDirectory(loggingConfig)).toAbsolutePath().normalize();
        createDirectories(logDirectory);

        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName("FILE");
        appender.setFile(logDirectory.resolve(resolveFileName(loggingConfig)).toString());
        appender.setEncoder(encoder);

        TimeBasedRollingPolicy<ILoggingEvent> policy = new TimeBasedRollingPolicy<ILoggingEvent>();
        policy.setContext(context);
        policy.setParent(appender);
        policy.setCleanHistoryOnStart(true);
        policy.setMaxHistory(resolveMaxHistoryDays(loggingConfig));
        policy.setFileNamePattern(logDirectory.resolve(resolveArchivePattern(loggingConfig)).toString());
        policy.start();

        appender.setRollingPolicy(policy);
        appender.start();
        return appender;
    }

    private void createDirectories(Path logDirectory) {
        try {
            Files.createDirectories(logDirectory);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create log directory: " + logDirectory, exception);
        }
    }

    private String resolvePattern(AppConfig.LoggingConfig loggingConfig) {
        if (loggingConfig == null || isBlank(loggingConfig.getPattern())) {
            return DEFAULT_PATTERN;
        }
        return loggingConfig.getPattern().trim();
    }

    private String resolveDirectory(AppConfig.LoggingConfig loggingConfig) {
        if (loggingConfig == null || isBlank(loggingConfig.getDirectory())) {
            return DEFAULT_DIRECTORY;
        }
        return loggingConfig.getDirectory().trim();
    }

    private String resolveFileName(AppConfig.LoggingConfig loggingConfig) {
        if (loggingConfig == null || isBlank(loggingConfig.getFileName())) {
            return DEFAULT_FILE_NAME;
        }
        return loggingConfig.getFileName().trim();
    }

    private String resolveArchivePattern(AppConfig.LoggingConfig loggingConfig) {
        if (loggingConfig == null || isBlank(loggingConfig.getArchiveFileNamePattern())) {
            return DEFAULT_ARCHIVE_PATTERN;
        }
        return loggingConfig.getArchiveFileNamePattern().trim();
    }

    private int resolveMaxHistoryDays(AppConfig.LoggingConfig loggingConfig) {
        if (loggingConfig == null || loggingConfig.getMaxHistoryDays() == null) {
            return DEFAULT_MAX_HISTORY_DAYS;
        }
        return loggingConfig.getMaxHistoryDays().intValue() > 0
                ? loggingConfig.getMaxHistoryDays().intValue()
                : DEFAULT_MAX_HISTORY_DAYS;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
