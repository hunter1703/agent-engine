package com.agentengine.engine.plugins;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class PluginLoader {
  private static final String PLUGIN_DIR_PROPERTY = "PLUGIN_DIR";
  private static final String DEFAULT_PLUGIN_DIR = "plugins";
  private static final AtomicReference<ClassLoader> LOADER = new AtomicReference<>();

  private PluginLoader() {}

  public static ClassLoader getClassLoader() {
    ClassLoader existing = LOADER.get();
    if (existing != null) {
      return existing;
    }
    ClassLoader loader = buildClassLoader();
    LOADER.compareAndSet(null, loader);
    return LOADER.get();
  }

  static ClassLoader buildClassLoader() {
    Path pluginsDir = resolvePluginsDir();
    if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
      return PluginLoader.class.getClassLoader();
    }
    List<URL> urls = new ArrayList<>();
    try (var paths = Files.list(pluginsDir)) {
      paths
          .filter(path -> path.toString().endsWith(".jar"))
          .forEach(
              path -> {
                try {
                  urls.add(path.toUri().toURL());
                } catch (Exception ignored) {
                }
              });
    } catch (Exception ignored) {
      return PluginLoader.class.getClassLoader();
    }
    if (urls.isEmpty()) {
      return PluginLoader.class.getClassLoader();
    }
    return new URLClassLoader(urls.toArray(new URL[0]), PluginLoader.class.getClassLoader());
  }

  private static Path resolvePluginsDir() {
    String fromProperty = System.getProperty(PLUGIN_DIR_PROPERTY);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return Paths.get(fromProperty);
    }
    String fromEnv = System.getenv(PLUGIN_DIR_PROPERTY);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return Paths.get(fromEnv);
    }
    return Paths.get(DEFAULT_PLUGIN_DIR);
  }
}
