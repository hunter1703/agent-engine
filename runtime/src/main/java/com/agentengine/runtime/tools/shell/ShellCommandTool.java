package com.agentengine.runtime.tools.shell;

import com.agentengine.runtime.api.tools.ToolDescriptor;
import com.agentengine.runtime.api.tools.ToolRiskLevel;
import com.agentengine.runtime.plugin.annotations.DiscoverableTool;
import com.agentengine.runtime.plugin.annotations.ToolConstructor;
import com.agentengine.runtime.plugin.annotations.ToolSchema;
import com.agentengine.runtime.plugin.tools.Tool;
import com.google.adk.tools.ToolContext;
import io.vertx.json.schema.common.dsl.Schemas;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@DiscoverableTool
public final class ShellCommandTool extends Tool {
  private static final Pattern BLOCKED = Pattern.compile("(^|[\\s;|&()])(/bin/)?rm(\\s|$)");
  private static final int MAX_OUTPUT_CHARS = 12_000;
  private static final String TOOL_NAME = "run_cmd";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Execute shell commands using `bash -lc`. Supports pipes `|`, redirects `>`, semi-colons `;`, and logic operators `&&`, `||`. Command must be single-line; no heredocs; avoid rm.",
      buildConfigSchema(), ToolRiskLevel.HIGH);
  private final Duration timeout;

  public ShellCommandTool() {
    this(null);
  }

  @ToolConstructor
  public ShellCommandTool(
      @ToolSchema(name = "timeout_seconds", description = "timeout in seconds for the shell command execution.", optional = true) final Long timeoutSecs) {
    super(DESCRIPTOR);
    this.timeout = timeoutSecs == null ? Duration.ofMinutes(30) : Duration.ofSeconds(timeoutSecs);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
      @ToolSchema(name = "command", description = "The shell command to execute. Can include pipes, redirects, and logic operators.") final String command) {
    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("Empty command");
    }
    if (BLOCKED.matcher(command).find()) {
      toolContext.requestConfirmation("Should the : " + command + " be executed?");
      return Map.of();
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
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> buildConfigSchema() {
    // noinspection unchecked
    return Schemas.objectSchema()
        .property("timeout_seconds",
            Schemas.intSchema()
                .withKeyword("description",
                    "Optional timeout in seconds for the shell command execution. Defaults to 30 seconds if not provided.")
                .withKeyword("default", 30))
        .toJson().mapTo(Map.class);
  }
}
