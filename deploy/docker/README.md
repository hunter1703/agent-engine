# Docker Deployment

This builds a production Quarkus image (no dev mode/hot reload) and runs the REST service.

## Build Image

```bash
docker build -f deploy/docker/Dockerfile -t agent-engine:local .
```

## Run Container

```bash
docker run --rm -p 8080:8080 \
  -e PLUGIN_DIR=/opt/agent/plugins \
  -e QUARKUS_HTTP_CORS=true \
  -e QUARKUS_HTTP_CORS_ORIGINS=http://localhost:3000 \
  agent-engine:local
```

If you use MongoDB for configs/sessions, provide the connection string:

```bash
docker run --rm -p 8080:8080 \
  -e MONGODB_CONNECTION_STRING=mongodb://host.docker.internal:27002 \
  agent-engine:local
```

## Local Ollama / llama.cpp

When the model server runs on your laptop (outside Docker), set `baseUrl` in the model config
to `http://host.docker.internal:<port>` so the container can reach it.

Example `ollama` model config:

```json
{
  "type": "ollama",
  "model": "qwen2.5:7b",
  "baseUrl": "http://host.docker.internal:11434"
}
```

Example `open_ai_compatible` config (ensure `serverCommand` and `serverArgs` are omitted):

```json
{
  "type": "open_ai_compatible",
  "model": "qwen3-coder-30b",
  "baseUrl": "http://host.docker.internal:17001/v1"
}
```

If you mount configs into the container, reference them by path in requests using
`agentConfigPath`.

## Webapp Access

Expose the REST service with `-p 8080:8080` and point the web app to
`http://localhost:8080`. If the browser runs on a different origin, set
`QUARKUS_HTTP_CORS=true` and `QUARKUS_HTTP_CORS_ORIGINS` accordingly.
