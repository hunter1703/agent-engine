package com.agentengine.engine.api.beans.config;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ConfigLoader {

  AgentConfig loadConfig(Path path);
}
