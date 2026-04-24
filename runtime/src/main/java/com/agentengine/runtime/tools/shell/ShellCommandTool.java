package com.agentengine.runtime.tools.shell;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.annotations.ToolConstructor;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.google.adk.tools.ToolContext;
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
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Executes a single-line shell command and returns the combined stdout and stderr output as a single "
                    + "string. Use when no dedicated tool covers the required operation — building projects, running "
                    + "test suites, installing packages, executing scripts, or other system-level tasks. Prefer "
                    + "dedicated tools (file read/write, search) when they are available. Supports shell constructs "
                    + "including pipes (|), output redirects (>), semicolons (;), and logical operators (&&, ||). "
                    + "Multi-line constructs and heredocs are not supported. Commands that invoke `rm` are paused "
                    + "for explicit user confirmation before executing. The process runs with the user's home "
                    + "directory as its working directory. Output is truncated at 12,000 characters with a trailing "
                    + "...<truncated>... marker. "
                    + "Returns: { output }. Non-zero exit codes prefix the output with (exit=N); timeouts prefix with (timeout).",
            ToolRiskLevel.MEDIUM);
    private final Duration timeout;

    public ShellCommandTool() {
        this(null);
    }

    @ToolConstructor
    public ShellCommandTool(
            @ToolSchema(
                            name = "timeout_seconds",
                            description = "timeout in seconds for the shell command execution.",
                            optional = true)
                    final Long timeoutSecs) {
        super(DESCRIPTOR);
        this.timeout = timeoutSecs == null ? Duration.ofMinutes(30) : Duration.ofSeconds(timeoutSecs);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext,
            @ToolSchema(
                            name = "command",
                            description =
                                    "The shell command string to execute on a single line. Supports pipes (|), redirects (>), "
                                            + "semicolons (;), and logical operators (&&, ||). Heredocs and embedded newlines are "
                                            + "not supported. Commands invoking `rm` will pause for user confirmation.")
                    final String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Empty command");
        }
        if (BLOCKED.matcher(command).find()) {
            if (toolContext.toolConfirmation().isEmpty()) {
                toolContext.requestConfirmation(
                        String.format("Should the command `%s` be executed?", command), Map.of("command", command));
                return Map.of();
            }
            if (!toolContext.toolConfirmation().get().confirmed()) {
                return Map.of("error", "This command is rejected.");
            }
        }
        final ProcessBuilder builder = new ProcessBuilder("sh", "-lc", command);
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
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception.getMessage(), exception);
        }
    }

    private String readAll(Process process) throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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
