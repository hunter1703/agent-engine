package com.agentengine.util.infra;

import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.common.utils.StringUtils;

public abstract class InfraClientProvisioner {
  private final ApplicationConfig applicationConfig;

  protected InfraClientProvisioner(ApplicationConfig applicationConfig) {
    this.applicationConfig = applicationConfig;
  }

  protected String resolvedServerId(final ServerType serverType, final String serverId) {
    if (StringUtils.isNotBlank(serverId)) {
      return serverId;
    }
    return serverType.defaultServerId(applicationConfig);
  }
}
