package com.agentengine.util.common.config;

import com.agentengine.util.common.StringUtils;

public final class ApplicationConfigUtils {
    private static final String PROPERTY_PREFIX = "infra.default-server.";

    private ApplicationConfigUtils() {}

    public static String getDefaultServerId(final ApplicationConfig applicationConfig, final String serverType) {
        final String defaultServer = applicationConfig.getString(PROPERTY_PREFIX + serverType);
        if (StringUtils.isBlank(defaultServer)) {
            throw new IllegalStateException(
                    "No default server for '" + serverType + "': set " + PROPERTY_PREFIX + serverType);
        }
        return defaultServer;
    }
}
