# Model Config Schema

This directory contains model configuration JSON files imported into MongoDB. Each file maps to
`ModelConfig` and is referenced from agent configs by ID. The setup script imports JSON files
only.


## Required Fields
- `type`: one of `OLLAMA`, `OPEN_AI_COMPATIBLE`, `GEMINI`
- `model`: model name or identifier

## Optional Fields
- `baseUrl`: backend base URL
- `toolCallingEnabled`: enable native tool calling (defaults to false)
- `toolCallingSupported`: provider supports native tool calling (defaults to false)
- `temperature`, `topK`, `topP`, `repeatPenalty`
- `numPredict`: maximum tokens to generate
- `maxContextLength`: context window size
- `stopTokens`: list of stop strings
- `contextManagerConfig`: optional context config object with `type`
- `serverCommand`: OpenAI-compatible server command to launch (OPEN_AI_COMPATIBLE)
- `apiKey`: Gemini API key (GEMINI)
- `serverArgs`: list of server arguments
- `serverWorkdir`: working directory for the server process

## Example
```json
{
  "type": "OLLAMA",
  "model": "qwen3-coder:30b",
  "temperature": 0.2
}
```

## OPEN_AI_COMPATIBLE Auto-Start
Auto-generated server settings are only applied when `baseUrl`, `serverCommand`, and `serverArgs`
are omitted from the model configuration.

```json
{
  "type": "OPEN_AI_COMPATIBLE",
  "model": "qwq-32b",
  "baseUrl": "http://127.0.0.1:17004/v1",
  "serverCommand": "/path/to/llama-server",
  "serverArgs": ["-m", "/path/to/qwq-32b.gguf", "--host", "127.0.0.1", "--port", "17004"]
}
```

## GEMINI Example
```json
{
  "type": "GEMINI",
  "model": "gemini-2.0-flash",
  "apiKey": "YOUR_API_KEY",
  "toolCallingEnabled": true,
  "toolCallingSupported": true
}
```

## MongoDB IDs
When imported into MongoDB, the file basename (without extension) becomes the `_id` used by the
agent service.
