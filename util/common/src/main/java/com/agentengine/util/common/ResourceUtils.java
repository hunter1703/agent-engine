package com.agentengine.util.common;

import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    } catch (IOException exception) {
      LOG.warn("Failed to load resource as string: {}", path, exception);
      return "";
    }
  }

  /**
   * Names (classloader-resource-path form, e.g. {@code "agents/community/experts/foo.json"}) of
   * every classpath resource directly under {@code directory}. Uses Guava's {@link ClassPath}
   * rather than filesystem APIs so it works identically whether running from a packaged jar or
   * exploded classes (dev mode) — plain directory listing only works for the latter.
   */
  public static List<String> listResourceNames(final String directory) {
    final String prefix = directory.endsWith("/") ? directory : directory + "/";
    try {
      return ClassPath.from(ResourceUtils.class.getClassLoader()).getResources().stream()
          .map(ClassPath.ResourceInfo::getResourceName)
          .filter(name -> name.startsWith(prefix))
          .toList();
    } catch (IOException exception) {
      LOG.warn("Failed to list classpath resources under: {}", directory, exception);
      return List.of();
    }
  }
}
