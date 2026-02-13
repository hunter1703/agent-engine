package com.agentengine.engine.model;

import static java.lang.StringTemplate.STR;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ModelUtils {
  private static final Logger LOGGER = Logger.getLogger(ModelUtils.class.getName());
  private static final Duration READY_TIMEOUT = Duration.ofSeconds(60);
  private static final Duration READY_POLL_INTERVAL = Duration.ofMillis(500);
  private static final Map<String, ManagedServer> SERVERS = new ConcurrentHashMap<>();
  private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  
  // Constants for server configuration generation
  private static final Random RANDOM = new Random();
  private static final int MIN_PORT = 18000;
  private static final int MAX_PORT = 65535;

  private ModelUtils() {
  }

  /**
   * Generates server configuration for OPEN_AI_COMPATIBLE models when no explicit server settings exist.
   *
   * @param modelConfig The model configuration to update
   * @return true if configuration was generated, false otherwise
   */
  public static boolean generateServerConfig(final ModelConfig modelConfig) {
    if (modelConfig == null) {
      return false;
    }

    // Only generate for OPEN_AI_COMPATIBLE models
    if (!ModelConfig.Provider.OPEN_AI_COMPATIBLE.name().equalsIgnoreCase(modelConfig.getType())) {
      return false;
    }

    if (StringUtils.isNotBlank(modelConfig.getBaseUrl())
        || StringUtils.isNotBlank(modelConfig.getServerCommand())
        || CollectionUtils.isNotEmpty(modelConfig.getServerArgs())) {
      return false;
    }

    // Generate a random port greater than 18000
    final int port = generateRandomPort();

    // Set the base URL
    final String baseUrl = STR."http://127.0.0.1:\{port}/v1";
    modelConfig.setBaseUrl(baseUrl);

    // Set the server command to always be llama-server
    modelConfig.setServerCommand(getLlamaServerCommand());

    // Build server args from the model property
    final List<String> serverArgs = buildServerArgs(modelConfig.getModel());
    // Update the port in the server args to match the generated port
    updatePortInServerArgs(serverArgs, port);
    modelConfig.setServerArgs(serverArgs);

    return true;
  }

  /**
   * Generates a random port number greater than 18000.
   *
   * @return A random port number between MIN_PORT and MAX_PORT
   */
  private static int generateRandomPort() {
    // Generate a random port between MIN_PORT and MAX_PORT
    return RANDOM.nextInt((MAX_PORT - MIN_PORT) + 1) + MIN_PORT;
  }

  /**
   * Gets the llama server command based on the operating system.
   *
   * @return The llama server command
   */
  private static String getLlamaServerCommand() {
    // Determine the OS and return the appropriate command
    String osName = System.getProperty("os.name").toLowerCase();

    if (osName.contains("mac")) {
      // On macOS, typically installed via Homebrew
      return "/opt/homebrew/bin/llama-server";
    } else if (osName.contains("win")) {
      // On Windows, could be llama-server.exe in various locations
      return "llama-server.exe";
    } else {
      // On Linux, could be in various locations
      return "/usr/bin/llama-server";
    }
  }

  /**
   * Builds server arguments from the model path.
   *
   * @param model The model path
   * @return A list of server arguments
   */
  private static List<String> buildServerArgs(String model) {
    List<String> args = new ArrayList<>();

    // Add the model argument
    if (StringUtils.isNotBlank(model)) {
      args.add("-m");
      args.add(model);
    }

    // Add host and port arguments
    args.add("--host");
    args.add("127.0.0.1");

    // Add port argument with placeholder that will be updated later
    args.add("--port");
    args.add("0"); // Placeholder, will be updated with the actual port

    return args;
  }

  /**
   * Updates the port argument in the server args to match the generated port.
   *
   * @param serverArgs The server arguments list
   * @param port The port to set
   */
  public static void updatePortInServerArgs(List<String> serverArgs, int port) {
    if (serverArgs == null) {
      return;
    }

    // Find the --port argument and update its value
    for (int i = 0; i < serverArgs.size(); i++) {
      if ("--port".equals(serverArgs.get(i)) && i + 1 < serverArgs.size()) {
        serverArgs.set(i + 1, String.valueOf(port));
        break;
      }
    }
  }

  public static void ensureRunning(final ModelConfig config) {
    if (config == null) {
      return;
    }
    if (!ModelConfig.Provider.OPEN_AI_COMPATIBLE.name().equalsIgnoreCase(config.getType())) {
      return;
    }
    final ServerAddress address = resolveAddress(config.getBaseUrl());
    if (address == null) {
      return;
    }
    if (isReachable(address)) {
      waitForReady(address, READY_TIMEOUT);
      return;
    }
    if (StringUtils.isBlank(config.getServerCommand())) {
      LOGGER.warning(
          "OPEN_AI_COMPATIBLE server unavailable and no serverCommand configured for model: " + config.getModel());
      return;
    }
    ManagedServer server = SERVERS.compute(address.baseUrl(), (key, existing) -> {
      if (existing != null && existing.process().isAlive()) {
        return existing;
      }
      return startServer(config, address);
    });
    if (server != null) {
      waitForReady(address, READY_TIMEOUT);
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
      LOGGER.info("Started OpenAI-compatible server for " + address.baseUrl());
      return new ManagedServer(address.baseUrl(), process, command);
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Failed to start OpenAI-compatible server for " + address.baseUrl(), ex);
      return null;
    }
  }

  private static void registerShutdownHook() {
    if (!SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
      return;
    }
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      for (ManagedServer server : SERVERS.values()) {
        if (server.process().isAlive()) {
          server.process().destroy();
        }
      }
    }));
  }

  private static boolean isReachable(final ServerAddress address) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(address.host(), address.port()), 500);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static void waitForReady(final ServerAddress address, final Duration timeout) {
    URI modelsEndpoint = buildModelsEndpoint(address.baseUrl());
    if (modelsEndpoint == null) {
      return;
    }
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (isModelReady(modelsEndpoint)) {
        return;
      }
      try {
        Thread.sleep(READY_POLL_INTERVAL.toMillis());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    LOGGER.warning(STR."OPEN_AI_COMPATIBLE server did not report ready within timeout for \{address.baseUrl()}");
  }

  static URI buildModelsEndpoint(final String baseUrl) {
    if (StringUtils.isBlank(baseUrl)) {
      return null;
    }
    try {
      URI baseUri = new URI(baseUrl);
      String path = baseUri.getPath();
      String normalizedPath = (StringUtils.isBlank(path) || "/".equals(path)) ? "/v1" : path;
      if (normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
        normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
      }
      String modelsPath = STR."\{normalizedPath}/models";
      return new URI(baseUri.getScheme(), baseUri.getUserInfo(), baseUri.getHost(), baseUri.getPort(), modelsPath, null,
          null);
    } catch (URISyntaxException ex) {
      LOGGER.log(Level.WARNING, STR."Invalid baseUrl: \{baseUrl}", ex);
      return null;
    }
  }

  private static boolean isModelReady(final URI modelsEndpoint) {
    try {
      HttpRequest request = HttpRequest.newBuilder(modelsEndpoint).timeout(Duration.ofSeconds(2)).GET().build();
      HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
      int statusCode = response.statusCode();
      if (statusCode == 404) {
        return true;
      }
      return statusCode >= 200 && statusCode < 300;
    } catch (Exception ex) {
      return false;
    }
  }

  record ServerAddress(String baseUrl, String host, int port) {
  }

  record ManagedServer(String baseUrl, Process process, List<String> command) {
  }
}
