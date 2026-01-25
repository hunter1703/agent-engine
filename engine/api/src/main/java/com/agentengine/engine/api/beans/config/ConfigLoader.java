package com.agentengine.engine.api.beans.config;

import java.nio.file.Path;

public interface ConfigLoader {

  AgentConfig loadConfig(Path path);
}
