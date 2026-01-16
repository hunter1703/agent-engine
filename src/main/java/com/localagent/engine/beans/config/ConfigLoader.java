package com.localagent.engine.beans.config;

import com.localagent.engine.utils.JsonUtils;
import com.localagent.engine.utils.YamlUtils;
import jakarta.inject.Singleton;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class ConfigLoader {

  public AgentConfig loadConfig(final Path path) {
    final AgentConfig agentConfig = _load(path);
    agentConfig.validate();
    return agentConfig;
  }

  private static AgentConfig _load(final Path path) {
    if (path == null || !Files.exists(path)) {
      throw new RuntimeException(
          new FileNotFoundException(STR."No agent config found at path : \{path}"));
    }
    final String ext = extension(path.getFileName().toString());
    if ("yaml".equals(ext) || "yml".equals(ext)) {
      return YamlUtils.fromFile(path, AgentConfig.class);
    }
    return JsonUtils.fromFile(path, AgentConfig.class);
  }

  private static String extension(final String name) {
    final int index = name.lastIndexOf('.');
    if (index < 0 || index == name.length() - 1) {
      return "";
    }
    return name.substring(index + 1).toLowerCase();
  }
}
