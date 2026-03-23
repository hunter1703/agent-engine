package com.agentengine.core.utils;

import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ModelUtils {
  private static final Random RANDOM = new Random();
  private static final int MIN_PORT = 18000;
  private static final int MAX_PORT = 65535;

  private ModelUtils() {
  }

  public static boolean generateServerConfig(final ModelConfig modelConfig) {
    if (modelConfig == null) {
      return false;
    }
    if (ModelConfig.Provider.valueOfOrDefault(modelConfig.getType()) != ModelConfig.Provider.OPEN_AI_COMPATIBLE) {
      return false;
    }
    if (StringUtils.isNotBlank(modelConfig.getBaseUrl()) || StringUtils.isNotBlank(modelConfig.getServerCommand())
        || CollectionUtils.isNotEmpty(modelConfig.getServerArgs())) {
      return false;
    }
    final int port = generateRandomPort();
    modelConfig.setBaseUrl("http://127.0.0.1:" + port + "/v1");
    modelConfig.setServerCommand("/opt/homebrew/bin/llama-server");
    final List<String> serverArgs = buildServerArgs(modelConfig.getModel());
    updatePortInServerArgs(serverArgs, port);
    modelConfig.setServerArgs(serverArgs);
    return true;
  }

  private static int generateRandomPort() {
    return RANDOM.nextInt((MAX_PORT - MIN_PORT) + 1) + MIN_PORT;
  }

  private static List<String> buildServerArgs(final String model) {
    final List<String> args = new ArrayList<>();
    if (StringUtils.isNotBlank(model)) {
      args.add("-m");
      args.add(model);
    }
    args.add("--host");
    args.add("127.0.0.1");
    args.add("--port");
    args.add("0");
    return args;
  }

  public static void updatePortInServerArgs(final List<String> serverArgs, final int port) {
    if (serverArgs == null) {
      return;
    }
    for (int i = 0; i < serverArgs.size(); i++) {
      if ("--port".equals(serverArgs.get(i)) && i + 1 < serverArgs.size()) {
        serverArgs.set(i + 1, String.valueOf(port));
        break;
      }
    }
  }
}
