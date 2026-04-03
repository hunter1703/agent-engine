package com.agentengine.util.mongodb.config;

import jakarta.inject.Singleton;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ApplicationConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfig.class);
    private static final String CLASSPATH_CONFIG_PATH = "application.properties";
    private static final String GLOBAL_CONFIG_PATH = "/config/global.properties";
    private static final String APPLICATION_CONFIG_PATH = "/config/application.properties";

    private final Properties properties;

    public ApplicationConfig() {
        this.properties = loadProperties();
    }

    public String getString(final String key) {
        return properties.getProperty(key);
    }

    public String getString(final String key, final String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public List<String> getListOfString(final String key) {
        final String rawValue = properties.getProperty(key, "");
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static Properties loadProperties() {
        final Properties loadedProperties = new Properties();
        loadPropertiesFromClasspath(loadedProperties);
        loadPropertiesFromPath(loadedProperties, GLOBAL_CONFIG_PATH);
        loadPropertiesFromPath(loadedProperties, APPLICATION_CONFIG_PATH);
        return loadedProperties;
    }

    private static void loadPropertiesFromClasspath(final Properties loadedProperties) {
        try (InputStream inputStream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(CLASSPATH_CONFIG_PATH)) {
            if (inputStream == null) {
                LOG.warn("Application config not found in classpath at '{}' - skipping", CLASSPATH_CONFIG_PATH);
                return;
            }
            loadedProperties.load(inputStream);
            LOG.info("Loaded application config from classpath {}", CLASSPATH_CONFIG_PATH);
        } catch (final IOException exception) {
            LOG.warn("Failed loading classpath application config '{}'", CLASSPATH_CONFIG_PATH, exception);
        }
    }

    private static void loadPropertiesFromPath(final Properties loadedProperties, final String path) {
        try (FileInputStream inputStream = new FileInputStream(path)) {
            loadedProperties.load(inputStream);
            LOG.info("Loaded application config from {}", path);
        } catch (final IOException exception) {
            LOG.warn("Application config not found at '{}' - skipping", path);
        }
    }
}
