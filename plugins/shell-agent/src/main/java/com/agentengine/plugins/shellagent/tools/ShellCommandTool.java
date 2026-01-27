package com.agentengine.plugins.shellagent.tools;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.Tool;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class ShellCommandTool implements Tool {
  private static final Pattern BLOCKED = Pattern.compile("(^|[\\s;|&()])(/bin/)?rm(\\s|$)");
  private static final int MAX_OUTPUT_CHARS = 12_000;
  private final Duration timeout;

  public ShellCommandTool(final Duration timeout) {
    this.timeout = timeout == null ? Duration.ofMinutes(30) : timeout;
  }

  @Override
  public String name() {
    return "run_cmd";
  }

  @Override
  public String description() {
    return "Execute ONE shell command using `bash -lc`. Command must be single-line; no heredocs; avoid rm.";
  }

  @Override
  public String execute(final Map<String, Object> args) {
    final String command = args == null ? null : String.valueOf(args.get("command"));
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
      final boolean finished =
          process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      String output = readAll(process);
      output = truncate(output.trim());
      if (!finished) {
        process.destroyForcibly();
        return "(timeout)\n" + output;
      }
      int exit = process.exitValue();
      if (exit == 0) {
        return output;
      }
      return "(exit=" + exit + ")\n" + output;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public Map<String, Object> parametersSchema() {
    //noinspection unchecked
    return JsonUtils.fromJson("""
            {
              "type": "object",
              "properties": {
                  "command": {
                  "type": "string",
                  "description": "The shell command to execute"
                  }
              },
              "required": ["command"]
            }
            """, Map.class);
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
}
