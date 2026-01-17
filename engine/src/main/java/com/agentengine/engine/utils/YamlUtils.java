package com.agentengine.engine.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public final class YamlUtils {
  private static final Yaml YAML = new Yaml();

  private YamlUtils() {
  }

  public static Map<String, Object> toMap(final String yaml) {
    if (yaml == null || yaml.isBlank()) {
      return null;
    }
    return YAML.load(yaml);
  }

  public static <T> T fromFile(final Path path, final Class<T> clazz) {
    try {
      return YAML.loadAs(Files.newInputStream(path), clazz);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
