package com.agentengine.util.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ResourceUtils.class);

  private ResourceUtils() {}

  public static String loadResourceAsString(final String path) {
    try (InputStream stream = ResourceUtils.class.getResourceAsStream(path)) {
      if (stream == null) {
        return "";
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOG.warn("Failed to load resource as string: {}", path, e);
      return "";
    }
  }
}
