package com.agentengine.engine.model;

import com.agentengine.engine.beans.config.ModelConfig;
import com.agentengine.engine.utils.ResourceUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ModelConfigValidator {
  private ModelConfigValidator() {}

  public static List<String> validate(final Path path) {
    List<String> errors = new ArrayList<>();
    ModelConfig config = ResourceUtils.loadModelConfig(path.toString());
    if (config == null) {
      errors.add("Unable to read model config: " + path);
      return errors;
    }
    if (config.getProvider() == null || config.getProvider().isBlank()) {
      errors.add("provider is required");
    } else {
      try {
        ModelConfig.Provider.valueOf(config.getProvider());
      } catch (IllegalArgumentException ex) {
        errors.add("provider must be one of OLLAMA, LLAMA_CPP, OPEN_AI");
      }
    }
    if (config.getModel() == null || config.getModel().isBlank()) {
      errors.add("model is required");
    }
    if (config.getResponseFormat() != null
        && !"text".equalsIgnoreCase(config.getResponseFormat())
        && !"json".equalsIgnoreCase(config.getResponseFormat())) {
      errors.add("response_format must be text or json");
    }
    if (config.isThoughtsEnabled()) {
      if (config.getThoughtsStartTag() == null || config.getThoughtsStartTag().isBlank()) {
        errors.add("thoughts_start_tag is required when thoughts_enabled is true");
      }
      if (config.getThoughtsEndTag() == null || config.getThoughtsEndTag().isBlank()) {
        errors.add("thoughts_end_tag is required when thoughts_enabled is true");
      }
    }
    return errors;
  }

  public static void main(final String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: ModelConfigValidator <path>");
      System.exit(1);
    }
    Path path = Paths.get(args[0]);
    List<String> errors = validate(path);
    if (errors.isEmpty()) {
      System.out.println("OK: " + path);
      return;
    }
    System.err.println("Invalid model config: " + path);
    errors.forEach(error -> System.err.println("- " + error));
    System.exit(2);
  }
}
