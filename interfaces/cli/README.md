# CLI Interface

The CLI module runs the stdio JSON protocol against the engine.

## Run
```bash
./gradlew :interfaces:cli:run --args="server"
```

The CLI reads JSON lines on stdin and writes JSON events on stdout. It emits engine
events verbatim (for example: `tool_plan`, `tool_execution`, `reasoning_start`).
