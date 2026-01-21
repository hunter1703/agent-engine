package com.agentengine.engine.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLoaderTest {

  @Test
  void getClassLoaderReturnsDefaultWhenNoPluginsDir(@TempDir Path tempDir) {
    System.setProperty("PLUGIN_DIR", tempDir.resolve("non-existent").toString());
    try {
      ClassLoader loader = PluginLoader.buildClassLoader();
      assertThat(loader).isEqualTo(PluginLoader.class.getClassLoader());
    } finally {
      System.clearProperty("PLUGIN_DIR");
    }
  }

  @Test
  void buildClassLoaderLoadsJarsFromDir(@TempDir Path tempDir) throws IOException {
    Path plugins = tempDir.resolve("plugins");
    Files.createDirectories(plugins);
    Files.createFile(plugins.resolve("test.jar"));
    Files.createFile(plugins.resolve("not-a-jar.txt"));

    System.setProperty("PLUGIN_DIR", plugins.toString());
    try {
      ClassLoader loader = PluginLoader.buildClassLoader();
      assertThat(loader).isNotEqualTo(PluginLoader.class.getClassLoader());
      assertThat(loader.getParent()).isEqualTo(PluginLoader.class.getClassLoader());
    } finally {
      System.clearProperty("PLUGIN_DIR");
    }
  }

  @Test
  void getClassLoaderIsSingleton() {
    ClassLoader l1 = PluginLoader.getClassLoader();
    ClassLoader l2 = PluginLoader.getClassLoader();
    assertThat(l1).isSameAs(l2);
  }
}
