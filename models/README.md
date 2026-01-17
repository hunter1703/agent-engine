# Model Config Schema

This directory contains model configuration files consumed by the engine. Each JSON/YAML file
maps to `ModelConfig` and is referenced from agent configs.

## Required Fields
- `provider`: one of `OLLAMA`, `LLAMA_CPP`, `OPEN_AI`
- `model`: model name or identifier

## Optional Fields
- `base_url`: provider base URL
- `response_format`: `text` or `json`
- `thoughts_enabled`: boolean
- `thoughts_start_tag`, `thoughts_end_tag`: tags for thought blocks
- `temperature`, `top_k`, `top_p`, `repeat_penalty`
- `num_predict`: maximum tokens to generate
- `max_context_length`: context window size
- `stop_tokens`: list of stop strings
- `context_config`: optional context config object with `type`

## Example
```json
{
  "provider": "OLLAMA",
  "model": "qwen3-coder:30b",
  "response_format": "text",
  "temperature": 0.2
}
```

## Validation
Run the model config validator:
```bash
./gradlew :engine:validateModelConfig --args="models/qwq_32b.json"
```
