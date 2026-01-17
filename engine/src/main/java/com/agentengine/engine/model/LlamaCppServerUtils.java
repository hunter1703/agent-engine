package com.agentengine.engine.model;

import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.utils.CollectionUtils;
import com.agentengine.engine.utils.StringUtils;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LlamaCppServerUtils {
  private static final Logger LOGGER = Logger.getLogger(LlamaCppServerUtils.class.getName());
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(10);
  private static final Map<String, ManagedServer> SERVERS = new ConcurrentHashMap<>();
  private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

  private LlamaCppServerUtils() {}

  public static void ensureRunning(final ModelConfig config) {
    if (config == null) {
      return;
    }
    if (!ModelConfig.Provider.LLAMA_CPP.name().equalsIgnoreCase(config.getProvider())) {
      return;
    }
    final ServerAddress address = resolveAddress(config.getBaseUrl());
    if (address == null) {
      return;
    }
    if (isReachable(address)) {
      return;
    }
    if (StringUtils.isBlank(config.getServerCommand())) {
      LOGGER.warning(
          "LLAMA_CPP server unavailable and no serverCommand configured for model: "
              + config.getModel());
      return;
    }
    ManagedServer server =
        SERVERS.compute(
            address.baseUrl(),
            (key, existing) -> {
              if (existing != null && existing.process().isAlive()) {
                return existing;
              }
              return startServer(config, address);
            });
    if (server != null) {
      waitForStartup(address, STARTUP_TIMEOUT);
    }
  }

  static ServerAddress resolveAddress(final String baseUrl) {
    if (StringUtils.isBlank(baseUrl)) {
      return null;
    }
    try {
      URI uri = new URI(baseUrl);
      String host = uri.getHost();
      int port = uri.getPort();
      if (host == null) {
        return null;
      }
      if (port <= 0) {
        port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
      }
      return new ServerAddress(baseUrl, host, port);
    } catch (URISyntaxException ex) {
      LOGGER.log(Level.WARNING, "Invalid baseUrl: " + baseUrl, ex);
      return null;
    }
  }

  private static ManagedServer startServer(final ModelConfig config, final ServerAddress address) {
    final List<String> command = new ArrayList<>();
    command.add(config.getServerCommand());
    command.addAll(CollectionUtils.nullSafeList(config.getServerArgs()));
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      if (StringUtils.isNotBlank(config.getServerWorkdir())) {
        builder.directory(Path.of(config.getServerWorkdir()).toFile());
      }
      builder.redirectErrorStream(true);
      Process process = builder.start();
      registerShutdownHook();
      LOGGER.info("Started llama.cpp server for " + address.baseUrl());
      return new ManagedServer(address.baseUrl(), process, command);
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Failed to start llama.cpp server for " + address.baseUrl(), ex);
      return null;
    }
  }

  private static void registerShutdownHook() {
    if (!SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
      return;
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  for (ManagedServer server : SERVERS.values()) {
                    if (server.process().isAlive()) {
                      server.process().destroy();
                    }
                  }
                }));
  }

  private static boolean isReachable(final ServerAddress address) {
    try (Socket socket = new Socket()) {
      socket.connect(new java.net.InetSocketAddress(address.host(), address.port()), 500);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static void waitForStartup(final ServerAddress address, final Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (isReachable(address)) {
        return;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  record ServerAddress(String baseUrl, String host, int port) {}

  record ManagedServer(String baseUrl, Process process, List<String> command) {}
}
