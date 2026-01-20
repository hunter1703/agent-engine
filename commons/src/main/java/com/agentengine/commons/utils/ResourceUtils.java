package com.agentengine.commons.utils;


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ResourceUtils {

  private ResourceUtils() {
  }

  public static String loadResourceAsString(final String path) {
    try (InputStream stream = TemplateUtils.class.getResourceAsStream(path)) {
      if (stream == null) {
        return "";
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }
}
