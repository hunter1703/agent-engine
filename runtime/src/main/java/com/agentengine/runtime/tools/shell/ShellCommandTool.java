package com.agentengine.runtime.tools.shell;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.annotations.ToolConstructor;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.session.SessionActorFactory;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.utils.ToolUtils;
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
            "Execute shell commands using `bash -lc`. Supports pipes `|`, redirects `>`, semi-colons `;`, and logic operators `&&`, `||`. Command must be single-line; no heredocs; commands matching `rm` require confirmation.",
            ToolRiskLevel.MEDIUM);
    private final Duration timeout;

    public ShellCommandTool() {
        this(null, null);
    }

    @ToolConstructor
    public ShellCommandTool(
            SessionActorFactory sessionActorFactory,
            @ToolSchema(
                            name = "timeout_seconds",
                            description = "timeout in seconds for the shell command execution.",
                            optional = true)
                    final Long timeoutSecs) {
        super(DESCRIPTOR, sessionActorFactory);
        this.timeout = timeoutSecs == null ? Duration.ofMinutes(30) : Duration.ofSeconds(timeoutSecs);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext,
            @ToolSchema(
                            name = "command",
                            description =
                                    "The shell command to execute. Can include pipes, redirects, and logic operators.")
                    final String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Empty command");
        }
        if (BLOCKED.matcher(command).find()) {
            if (toolContext.toolConfirmation().isEmpty()) {
                ToolUtils.requestConfirmationAndPause(
                        sessionActorFactory,
                        toolContext,
                        String.format("Should the command `%s` be executed?", command),
                        Map.of("command", command));
                return Map.of();
            }
            if (!toolContext.toolConfirmation().get().confirmed()) {
                return Map.of("error", "This command is rejected.");
            }
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
