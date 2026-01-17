# Model Config Schema

This directory contains model configuration JSON files imported into MongoDB. Each file maps to
`ModelConfig` and is referenced from agent configs by ID. The setup script imports JSON files
only.

## Required Fields
- `provider`: one of `OLLAMA`, `LLAMA_CPP`, `OPEN_AI`
- `model`: model name or identifier

## Optional Fields
- `baseUrl`: provider base URL
- `responseFormat`: `text` or `json`
- `thoughtsEnabled`: boolean
- `thoughtsStartTag`, `thoughtsEndTag`: tags for thought blocks
- `temperature`, `topK`, `topP`, `repeatPenalty`
- `numPredict`: maximum tokens to generate
- `maxContextLength`: context window size
- `stopTokens`: list of stop strings
- `contextConfig`: optional context config object with `type`
- `serverCommand`: llama.cpp server command to launch (LLAMA_CPP)
- `serverArgs`: list of server arguments
- `serverWorkdir`: working directory for the server process

## Example
```json
{
  "provider": "OLLAMA",
  "model": "qwen3-coder:30b",
  "responseFormat": "text",
  "temperature": 0.2
}
```

## LLAMA_CPP Auto-Start
```json
{
  "provider": "LLAMA_CPP",
  "model": "qwq-32b",
  "baseUrl": "http://127.0.0.1:17004/v1",
  "serverCommand": "/path/to/llama-server",
  "serverArgs": ["-m", "/path/to/qwq-32b.gguf", "--host", "127.0.0.1", "--port", "17004"]
}
```

## MongoDB IDs
When imported into MongoDB, the file basename (without extension) becomes the `_id` used by the
agent service.
