package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.agentengine.engine.beans.config.ModelConfig;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LlamaCppServerUtilsTest {

  @Test
  void resolveAddressReadsHostAndPort() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("http://127.0.0.1:17004/v1");

    assertThat(address.host()).isEqualTo("127.0.0.1");
    assertThat(address.port()).isEqualTo(17004);
  }

  @Test
  void resolveAddressDefaultsPortForHttp() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("http://localhost/v1");

    assertThat(address.port()).isEqualTo(80);
  }

  @Test
  void resolveAddressDefaultsPortForHttps() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("https://localhost/v1");

    assertThat(address.port()).isEqualTo(443);
  }

  @Test
  void resolveAddressReturnsNullForInvalidUrl() {
    assertThat(LlamaCppServerUtils.resolveAddress("not a url")).isNull();
  }

  @Test
  void ensureRunningSkipsWhenMissingConfig() {
    assertThatNoException().isThrownBy(() -> LlamaCppServerUtils.ensureRunning(null));
  }

  @Test
  void ensureRunningSkipsWhenServerCommandMissing() {
    ModelConfig config = new ModelConfig();
    config.setProvider(ModelConfig.Provider.LLAMA_CPP.name());
    config.setModel("llama");
    config.setBaseUrl("http://127.0.0.1:1");

    assertThatNoException().isThrownBy(() -> LlamaCppServerUtils.ensureRunning(config));
  }

  @Test
  void ensureRunningSkipsNonLlamaProviders() {
    ModelConfig config = new ModelConfig();
    config.setProvider(ModelConfig.Provider.OPEN_AI.name());

    assertThatNoException().isThrownBy(() -> LlamaCppServerUtils.ensureRunning(config));
  }

  @Test
  void startServerSupportsCustomCommand() throws Exception {
    ModelConfig config = new ModelConfig();
    config.setServerCommand("true");
    LlamaCppServerUtils.ServerAddress address = new LlamaCppServerUtils.ServerAddress("http://localhost:1", "localhost",
        1);

    Method startServer = LlamaCppServerUtils.class.getDeclaredMethod("startServer", ModelConfig.class,
        LlamaCppServerUtils.ServerAddress.class);
    startServer.setAccessible(true);

    Object server = startServer.invoke(null, config, address);

    assertThat(server).isNotNull();
  }

  @Test
  void startServerHonorsWorkdir() throws Exception {
    Path workdir = Files.createTempDirectory("llama");
    ModelConfig config = new ModelConfig();
    config.setServerCommand("true");
    config.setServerWorkdir(workdir.toString());
    LlamaCppServerUtils.ServerAddress address = new LlamaCppServerUtils.ServerAddress("http://localhost:1", "localhost",
        1);

    Method startServer = LlamaCppServerUtils.class.getDeclaredMethod("startServer", ModelConfig.class,
        LlamaCppServerUtils.ServerAddress.class);
    startServer.setAccessible(true);

    Object server = startServer.invoke(null, config, address);

    assertThat(server).isNotNull();
  }

  @Test
  void waitForStartupHandlesTimeout() throws Exception {
    LlamaCppServerUtils.ServerAddress address = new LlamaCppServerUtils.ServerAddress("http://localhost:1", "localhost",
        1);
    Method waitForStartup = LlamaCppServerUtils.class.getDeclaredMethod("waitForStartup",
        LlamaCppServerUtils.ServerAddress.class, Duration.class);
    waitForStartup.setAccessible(true);

    assertThatNoException().isThrownBy(() -> waitForStartup.invoke(null, address, Duration.ofMillis(1)));
  }

  @Test
  void internalLambdasExecuteSafely() throws Exception {
    ModelConfig config = new ModelConfig();
    config.setServerCommand("true");
    LlamaCppServerUtils.ServerAddress address = new LlamaCppServerUtils.ServerAddress("http://localhost:1", "localhost",
        1);

    Method ensureRunningLambda = LlamaCppServerUtils.class.getDeclaredMethod("lambda$ensureRunning$0",
        ModelConfig.class, LlamaCppServerUtils.ServerAddress.class, String.class,
        LlamaCppServerUtils.ManagedServer.class);
    ensureRunningLambda.setAccessible(true);
    Object managed = ensureRunningLambda.invoke(null, config, address, address.baseUrl(), null);
    assertThat(managed).isNotNull();

    Method registerLambda = LlamaCppServerUtils.class.getDeclaredMethod("lambda$registerShutdownHook$1");
    registerLambda.setAccessible(true);
    assertThatNoException().isThrownBy(() -> registerLambda.invoke(null));

    Method isReachable = LlamaCppServerUtils.class.getDeclaredMethod("isReachable",
        LlamaCppServerUtils.ServerAddress.class);
    isReachable.setAccessible(true);
    assertThatNoException().isThrownBy(() -> isReachable.invoke(null, address));
  }

  @Test
  void waitForStartupReturnsWhenReachable() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      LlamaCppServerUtils.ServerAddress address = new LlamaCppServerUtils.ServerAddress(
          "http://localhost:" + socket.getLocalPort(), "localhost", socket.getLocalPort());
      Method waitForStartup = LlamaCppServerUtils.class.getDeclaredMethod("waitForStartup",
          LlamaCppServerUtils.ServerAddress.class, Duration.class);
      waitForStartup.setAccessible(true);

      assertThatNoException().isThrownBy(() -> waitForStartup.invoke(null, address, Duration.ofMillis(200)));
    }
  }

  @Test
  void registerShutdownHookDestroysActiveProcess() throws Exception {
    Process process = new ProcessBuilder("sleep", "1").start();
    LlamaCppServerUtils.ManagedServer server = new LlamaCppServerUtils.ManagedServer("test", process,
        List.of("sleep", "1"));
    var serversField = LlamaCppServerUtils.class.getDeclaredField("SERVERS");
    serversField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, LlamaCppServerUtils.ManagedServer> servers = (Map<String, LlamaCppServerUtils.ManagedServer>) serversField
        .get(null);
    servers.put("test", server);

    Method registerLambda = LlamaCppServerUtils.class.getDeclaredMethod("lambda$registerShutdownHook$1");
    registerLambda.setAccessible(true);
    registerLambda.invoke(null);

    process.waitFor(1, TimeUnit.SECONDS);
    servers.clear();
  }

  @Test
  void isReachableReturnsTrueWhenSocketOpen() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      LlamaCppServerUtils.ServerAddress address = new LlamaCppServerUtils.ServerAddress(
          "http://localhost:" + socket.getLocalPort(), "localhost", socket.getLocalPort());
      Method isReachable = LlamaCppServerUtils.class.getDeclaredMethod("isReachable",
          LlamaCppServerUtils.ServerAddress.class);
      isReachable.setAccessible(true);

      Object result = isReachable.invoke(null, address);

      assertThat(result).isEqualTo(true);
    }
  }
}
