package com.agentengine.engine.tools.shell;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolConstructor;
import com.agentengine.engine.api.tools.annotations.ToolParam;
import com.google.adk.tools.Annotations.Schema;
import io.vertx.json.schema.common.dsl.Schemas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@AgentTool
public final class ShellCommandTool implements Tool {
  private static final Pattern BLOCKED = Pattern.compile("(^|[\\s;|&()])(/bin/)?rm(\\s|$)");
  private static final int MAX_OUTPUT_CHARS = 12_000;
  private static final String TOOL_NAME = "run_cmd";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, List.of(ALL), buildConfigSchema());
  private final Duration timeout;

  public ShellCommandTool() {
    this(null);
  }

  @ToolConstructor
  public ShellCommandTool(@ToolParam("timeout_seconds") final Long timeoutSecs) {
    this.timeout = timeoutSecs == null ? Duration.ofMinutes(30) : Duration.ofSeconds(timeoutSecs);
  }

  @Schema(
      name = "run_cmd",
      description =
          "Execute ONE shell command using `bash -lc`. Command must be single-line; no heredocs; avoid rm.")
  public Map<String, Object> execute(
      @Schema(name = "command", description = "The shell command to execute")
          final String command) {
    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("Empty command");
    }
    if (BLOCKED.matcher(command).find()) {
      throw new IllegalArgumentException("Blocked: rm is not allowed");
    }
    final ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command);
    builder.directory(Path.of(System.getProperty("user.home")).toFile());
    builder.redirectErrorStream(true);
    try {
      final Process process = builder.start();
      final boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      String output = readAll(process);
      output = truncate(output.trim());
      final String result;
      if (!finished) {
        process.destroyForcibly();
        result = "(timeout)\n" + output;
      } else {
        final int exit = process.exitValue();
        result = exit == 0 ? output : "(exit=" + exit + ")\n" + output;
      }
      return Map.of("output", result);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  private String readAll(Process process) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line).append("\n");
      }
      return builder.toString();
    }
  }

  private String truncate(final String text) {
    if (text.length() <= MAX_OUTPUT_CHARS) {
      return text;
    }
    return text.substring(0, MAX_OUTPUT_CHARS) + "\n...<truncated>...";
  }

  @Override
  public ToolDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> buildConfigSchema() {
    //noinspection unchecked
    return Schemas.objectSchema()
            .property(
                    "timeout_seconds",
                    Schemas.intSchema()
                            .withKeyword(
                                    "description",
                                    "Optional timeout in seconds for the shell command execution. Defaults to 30 seconds if not provided.")
                            .withKeyword("default", 30))
            .toJson()
            .mapTo(Map.class);
  }
}
