package com.agentengine.interfaces.rest.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Responses API endpoint to ensure compatibility with
 * Codex CLI
 */
@ConfigMapping(prefix = "agent.responses-api")
public interface ResponsesApiConfig {

  /**
   * Enables the Responses API endpoint for Codex CLI compatibility
   */
  @WithDefault("true")
  boolean enabled();

  /**
   * Sets the default model name reported to Codex CLI
   */
  @WithDefault("gpt-4-compatible")
  String defaultModel();

  /**
   * Controls whether to include detailed token usage in responses
   */
  @WithDefault("false")
  boolean includeTokenUsage();

  /**
   * Controls whether to include reasoning/analysis steps in responses
   */
  @WithDefault("true")
  boolean includeReasoning();
}