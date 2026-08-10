# Model Config Schema

This directory contains model configuration JSON files imported into MongoDB. Each file maps to
`ModelConfig` and is referenced from agent configs by ID. The setup script imports JSON files
only.


## Required Fields
- `type`: one of `ollama`, `open_ai_compatible`, `gemini`
- `model`: model name or identifier

## Optional Fields
- `baseUrl`: backend base URL
- `toolCallingEnabled`: enable native tool calling (defaults to false)
- `temperature`, `topK`, `topP`, `repeatPenalty`
- `numPredict`: maximum tokens to generate
- `maxContextLength`: context window size
- `stopTokens`: list of stop strings
- `instructions`: model-level guidance appended to system instructions
- `serverCommand`: OpenAI-compatible server command to launch (open_ai_compatible)
- `apiKey`: Gemini API key (gemini)
- `serverArgs`: list of server arguments
- `serverWorkdir`: working directory for the server process

## Example
```json
{
  "type": "ollama",
  "model": "qwen3-coder:30b",
  "temperature": 0.2
}
```

## open_ai_compatible Auto-Start
Auto-generated server settings are only applied when `baseUrl`, `serverCommand`, and `serverArgs`
are omitted from the model configuration.

```json
{
  "type": "open_ai_compatible",
  "model": "qwq-32b",
  "baseUrl": "http://127.0.0.1:17004/v1",
  "serverCommand": "/path/to/llama-server",
  "serverArgs": ["-m", "/path/to/qwq-32b.gguf", "--host", "127.0.0.1", "--port", "17004"]
}
```

## gemini Example
```json
{
  "type": "gemini",
  "model": "gemini-2.0-flash",
  "apiKey": "YOUR_API_KEY",
  "toolCallingEnabled": true
}
```

## MongoDB IDs
When imported into MongoDB, the file basename (without extension) becomes the `_id` used by the
agent service.
