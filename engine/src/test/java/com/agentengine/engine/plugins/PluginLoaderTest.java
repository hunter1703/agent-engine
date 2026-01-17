package com.agentengine.engine.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLoaderTest {
  @TempDir
  Path tempDir;

  @AfterEach
  void clearProperty() {
    System.clearProperty("PLUGIN_DIR");
  }

  @Test
  void buildClassLoaderFallsBackWhenDirectoryMissing() {
    System.setProperty("PLUGIN_DIR", tempDir.resolve("missing").toString());

    ClassLoader loader = PluginLoader.buildClassLoader();

    assertThat(loader).isSameAs(PluginLoader.class.getClassLoader());
  }

  @Test
  void buildClassLoaderLoadsJarUrls() throws Exception {
    Path pluginsDir = tempDir.resolve("plugins");
    Files.createDirectories(pluginsDir);
    Path jarPath = pluginsDir.resolve("plugin.jar");
    try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
      // empty jar is fine
    }
    System.setProperty("PLUGIN_DIR", pluginsDir.toString());

    ClassLoader loader = PluginLoader.buildClassLoader();

    assertThat(loader).isInstanceOf(URLClassLoader.class);
    URLClassLoader urlLoader = (URLClassLoader) loader;
    assertThat(urlLoader.getURLs()).anyMatch(url -> url.toString().endsWith("plugin.jar"));
  }

  @Test
  void buildClassLoaderFallsBackWhenDirectoryEmpty() throws Exception {
    Path pluginsDir = tempDir.resolve("plugins-empty");
    Files.createDirectories(pluginsDir);
    System.setProperty("PLUGIN_DIR", pluginsDir.toString());

    ClassLoader loader = PluginLoader.buildClassLoader();

    assertThat(loader).isSameAs(PluginLoader.class.getClassLoader());
  }
}
