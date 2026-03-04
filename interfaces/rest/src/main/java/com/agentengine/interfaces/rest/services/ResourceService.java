package com.agentengine.interfaces.rest.services;

import com.agentengine.engine.api.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Service for retrieving resources with caching. */
@Singleton
public class ResourceService {
  private final Map<String, String> resourceCache = new ConcurrentHashMap<>();

  /**
   * Retrieves a resource by its type with caching enabled.
   *
   * @param resourceType the type of resource to retrieve
   * @param extension the file extension (e.g., "json", "yaml")
   * @return the resource content as a string
   * @throws IOException if there's an error reading the resource
   */
  public String getResource(String resourceType, String extension) throws IOException {
    String cacheKey = resourceType.toLowerCase() + "." + extension;
    String cached = resourceCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    String resourcePath = "schemas/" + cacheKey;
    String loaded = load(resourceType, resourcePath);
    // putIfAbsent avoids the check-then-put race; the redundant load is harmless
    String existing = resourceCache.putIfAbsent(cacheKey, loaded);
    return existing != null ? existing : loaded;
  }

  private String load(String resourceType, String resourcePath) throws IOException {
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (Objects.isNull(inputStream)) {
        throw new IOException("Resource for type '" + resourceType + "' not found at path: " + resourcePath);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * Convenience method to get JSON resources.
   *
   * @param resourceType the type of resource to retrieve
   * @return the resource content as a string
   * @throws IOException if there's an error reading the resource
   */
  public Map<String, Object> getJsonResource(String resourceType) throws IOException {
    return JsonUtils.fromJson(getResource(resourceType, "json"), new TypeReference<>() {});
  }
}
