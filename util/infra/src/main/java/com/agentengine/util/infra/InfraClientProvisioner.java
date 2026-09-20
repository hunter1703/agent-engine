package com.agentengine.util.infra;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.common.config.ApplicationConfigUtils;

public abstract class InfraClientProvisioner {
    private final ApplicationConfig applicationConfig;

    protected InfraClientProvisioner(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    protected String resolvedServerId(final String serverType, final String serverId) {
        if (StringUtils.isNotBlank(serverId)) {
            return serverId;
        }
        return ApplicationConfigUtils.getDefaultServerId(applicationConfig, serverType);
    }
}
