package com.agentengine.agent.infra.tools.shell;

import com.agentengine.agent.infra.annotations.DiscoverableTool;
import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.agents.tools.ToolConstructor;
import com.agentengine.util.agents.tools.ToolParam;
import com.google.adk.tools.ToolContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@DiscoverableTool
public final class ShellCommandTool extends Tool {
  private static final Pattern BLOCKED = Pattern.compile("(^|[\\s;|&()])(/bin/)?rm(\\s|$)");
  private static final int MAX_OUTPUT_CHARS = 12_000;
  private static final String TOOL_NAME = "run_cmd";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          """
          Executes a single-line shell command and returns structured execution details including output,
          exit code, success status, and working directory. Use when no dedicated tool covers the required
          operation — building projects, running test suites, installing packages, or system-level tasks.
          Prefer dedicated tools (file read/write/grep) when available. Supports shell constructs including
          pipes (|), redirects (>), semicolons (;), and logical operators (&&, ||). Multi-line constructs and
          heredocs are not supported. Commands that invoke `rm` are paused for explicit user confirmation.
          Pass 'cwd' to specify the working directory (defaults to the user's home directory).
          Output exceeding 12,000 characters is truncated with guidance on using head/tail/grep.
          Returns: { output, exit_code, success, working_directory, truncated?, total_chars?, message? }.""",
          ToolRiskLevel.MEDIUM);
  private final Duration timeout;

  public ShellCommandTool() {
    this(null);
  }

  @ToolConstructor
  public ShellCommandTool(
      @ToolParam(
              name = "timeout_seconds",
              description = "timeout in seconds for the shell command execution.",
              optional = true)
          final Long timeoutSecs) {
    super(DESCRIPTOR);
    this.timeout = timeoutSecs == null ? Duration.ofMinutes(30) : Duration.ofSeconds(timeoutSecs);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolArg(
              name = "command",
              description =
                  """
                  The shell command string to execute on a single line. Supports pipes (|), redirects (>),
                  semicolons (;), and logical operators (&&, ||). Heredocs and embedded newlines are
                  not supported. Commands invoking `rm` will pause for user confirmation.""")
          final String command,
      @ToolArg(
              name = "cwd",
              description =
                  """
                  Working directory in which to execute the command. Defaults to the user home directory.
                  Can be an absolute path or relative to the user home directory.""",
              optional = true)
          final String cwd) {
    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("Empty command");
    }
    if (BLOCKED.matcher(command).find()) {
      if (toolContext.toolConfirmation().isEmpty()) {
        toolContext.requestConfirmation(
            String.format("Should the command `%s` be executed?", command),
            Map.of("command", command));
        return ToolOutput.direct(Map.of());
      }
      if (!toolContext.toolConfirmation().get().confirmed()) {
        return ToolOutput.direct(Map.of("error", "This command is rejected."));
      }
    }

    final Path workingDir;
    if (cwd != null && !cwd.isBlank()) {
      Path cwdPath = Path.of(cwd);
      final Path candidate =
          cwdPath.isAbsolute()
              ? cwdPath.normalize()
              : Path.of(System.getProperty("user.home"), cwd).normalize();
      if (!Files.isDirectory(candidate)) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "Specified working directory does not exist or is not a directory: " + cwd));
      }
      workingDir = candidate;
    } else {
      workingDir = Path.of(System.getProperty("user.home"));
    }

    final ProcessBuilder builder = new ProcessBuilder("sh", "-lc", command);
    builder.directory(workingDir.toFile());
    builder.redirectErrorStream(true);
    try {
      final Process process = builder.start();
      final boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      final String rawOutput = readAll(process).trim();
      final TruncationResult truncation = truncate(rawOutput);

      final Map<String, Object> response = new LinkedHashMap<>();
      final int exit = finished ? process.exitValue() : -1;
      final boolean success = finished && exit == 0;

      final String formattedOutput;
      if (!finished) {
        process.destroyForcibly();
        formattedOutput = "(timeout)\n" + truncation.text();
        response.put("timeout", true);
      } else {
        formattedOutput =
            exit == 0 ? truncation.text() : "(exit=" + exit + ")\n" + truncation.text();
      }

      response.put("output", formattedOutput);
      response.put("exit_code", exit);
      response.put("success", success);
      response.put("working_directory", workingDir.toString());

      if (truncation.truncated()) {
        response.put("truncated", true);
        response.put("total_chars", truncation.totalChars());
        response.put(
            "message",
            "Output truncated at "
                + MAX_OUTPUT_CHARS
                + " characters ("
                + (truncation.totalChars() - MAX_OUTPUT_CHARS)
                + " characters omitted). Consider piping to head, tail, or grep, or redirecting to a file.");
      }

      return ToolOutput.direct(response);
    } catch (IOException | InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(exception.getMessage(), exception);
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

  private record TruncationResult(String text, boolean truncated, int totalChars) {}

  private TruncationResult truncate(final String text) {
    if (text.length() <= MAX_OUTPUT_CHARS) {
      return new TruncationResult(text, false, text.length());
    }
    final int omitted = text.length() - MAX_OUTPUT_CHARS;
    final String truncatedText =
        text.substring(0, MAX_OUTPUT_CHARS)
            + "\n...<truncated "
            + omitted
            + " characters. Pipe output to head/tail/grep or redirect to a file>...";
    return new TruncationResult(truncatedText, true, text.length());
  }
}
