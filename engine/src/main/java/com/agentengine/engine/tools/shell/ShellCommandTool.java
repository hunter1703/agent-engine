package com.agentengine.engine.tools.shell;

import com.google.adk.tools.Annotations.Schema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class ShellCommandTool {
  private static final Pattern BLOCKED = Pattern.compile("(^|[\\s;|&()])(/bin/)?rm(\\s|$)");
  private static final int MAX_OUTPUT_CHARS = 12_000;
  private final Duration timeout;

  public ShellCommandTool(final Duration timeout) {
    this.timeout = timeout == null ? Duration.ofMinutes(30) : timeout;
  }

  @Schema(name = "run_cmd", description = "Execute ONE shell command using `bash -lc`. Command must be single-line; no heredocs; avoid rm.")
  public Map<String, Object> runCommand(
      @Schema(name = "command", description = "The shell command to execute") final String command) {
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
}
