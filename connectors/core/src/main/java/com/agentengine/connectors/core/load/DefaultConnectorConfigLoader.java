package com.agentengine.connectors.core.load;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.util.common.JsonUtils;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Singleton
public final class DefaultConnectorConfigLoader implements ConnectorConfigLoader {

  @Override
  public ConnectorDefinition load(final Path path) {
    try {
      final String content = Files.readString(path);
      return parse(path.getFileName().toString(), content);
    } catch (IOException ex) {
      throw new ConnectorConfigLoadException("Failed to load connector config from path: " + path, ex);
    }
  }

  @Override
  public ConnectorDefinition load(final String rawConfig) {
    try {
      return parse("", rawConfig);
    } catch (RuntimeException ex) {
      throw new ConnectorConfigLoadException("Failed to parse connector config", ex);
    }
  }

  private ConnectorDefinition parse(final String fileName, final String content) {
    try {
      final String normalizedName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
      if (normalizedName.endsWith(".yaml") || normalizedName.endsWith(".yml")) {
        return JsonUtils.fromYaml(content, ConnectorDefinition.class);
      }
      if (normalizedName.endsWith(".json")) {
        return JsonUtils.fromJson(content, ConnectorDefinition.class);
      }
      return looksLikeJson(content)
          ? JsonUtils.fromJson(content, ConnectorDefinition.class)
          : JsonUtils.fromYaml(content, ConnectorDefinition.class);
    } catch (RuntimeException ex) {
      throw new ConnectorConfigLoadException("Failed to parse connector config", ex);
    }
  }

  private static boolean looksLikeJson(final String content) {
    if (content == null) {
      return false;
    }
    final String trimmed = content.trim();
    return trimmed.startsWith("{") || trimmed.startsWith("[");
  }
}
