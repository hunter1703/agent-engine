package com.agentengine.util.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class YamlUtils {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

  private YamlUtils() {}

  public static Map<String, Object> toMap(final String yaml) {
    if (yaml == null || yaml.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(yaml, new TypeReference<>() {});
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static <T> T fromFile(final Path path, final Class<T> clazz) {
    try (var stream = Files.newInputStream(path)) {
      return OBJECT_MAPPER.readValue(stream, clazz);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
