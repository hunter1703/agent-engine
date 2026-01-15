# CLI Harness

This CLI is a minimal stdio harness that speaks JSON-over-stdio to a local agent server. It focuses on streaming and lightweight agent management.

## Install

```bash
pip install -r requirements.txt
```

## Discover Agents

The CLI discovers `agents/<name>` at runtime and requires a config file
(`agents/<name>/config.json|yaml|yml` unless you pass `--config`).

```bash
python -m cli.main agents list
```

## Prompt Preview

Type `/prompt` inside a chat session to print the next prompt that will be sent to the model.
See `examples/cli/runtime/commands/` for additional command modules.

## Validate Config

```bash
python -m cli.main agents check shell
```

## Chat

```bash
python -m cli.main chat shell
```

Hide thoughts:

```bash
python -m cli.main chat shell --hide-thoughts
```

Type `/quit` or `/exit` to leave.

## Stdio Protocol

The server lives under `cli/runtime/server/main.py`, reads JSON lines, and writes JSON lines.
The CLI starts the server process and streams output.

### Requests

```json
{"id":"<uuid>","method":"invoke","user_message":"hi"}
```

### Events

```json
{"id":"<uuid>","event":"delta","text":"Hello"}
{"id":"<uuid>","event":"thoughts","text":"..."}
{"id":"<uuid>","event":"finalAnswer","text":"Hello"}
```
