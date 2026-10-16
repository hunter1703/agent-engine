# Python Services & Integrations

This module contains Python-based runtime services and integrations for `agent-engine`.

## Components

1. **`model/` (Local Model Server)**:
   - High-throughput loopback embedding service running on `http://127.0.0.1:8081`.
   - Exposes standard OpenAI-compatible `POST /v1/embeddings` endpoint. An input image yields zero or more entries of `data`, each carrying the `index` of its image.
   - Models:
     - `arcface`: SCRFD face detection + ArcFace 512-d embedding (L2 normalized, zero-padded with 256 zeros to 768-d), one per detected face.
     - `siglip`: `google/siglip-base-patch16-256` yielding native 768-d unit vectors on MPS/CUDA/CPU, one per image.

2. **`pinterest_mitm.py` (Pinterest MITM Addon)**:
   - `mitmproxy` addon intercepting Pinterest iOS `GET /v3/users/boards/board_picker_shortlist/`.
   - Calls the local Agent Engine (`POST /v1/agent/{agentId}/send-temp`) with the pin image attachment; the answer is the classifier's `{"labels": [...]}`.
   - Resolves visual classification matches against boards and reorders or injects the best board to position 0.

## Running

### Prerequisites
Install dependencies with `uv`:
```bash
cd python
uv sync
```

### Running the Model Server
```bash
cd python
uv run python -m model.server
# Or via uvicorn directly:
uv run uvicorn model.server:app --host 0.0.0.0 --port 8081
```

### Running the Pinterest MITM Proxy
```bash
cd python
mitmweb -v --listen-host 0.0.0.0 --listen-port 9000 --web-port 9001 -s pinterest_mitm.py
```

Environment variables for `pinterest_mitm.py`:
- `AGENT_ENGINE_URL`: default `http://localhost:8080`
- `AGENT_ENGINE_CA_BUNDLE`: CA certificate file that verifies the Agent Engine, e.g. mkcert's `$(mkcert -CAROOT)/rootCA.pem`
- `AGENT_ENGINE_INSECURE`: set to `true` to skip TLS verification of the Agent Engine only; Pinterest's certificate is always verified
- `AGENT_ID`: the classifier agent asked about each pin, default `image_classifier`
- `AGENT_SESSION_COOKIE`: default `systemSession`
- `MIN_BOARD_SCORE`: default `0.5`
- `DEADLINE_SECONDS`: default `8.0`; the longest the addon holds a shortlist response (and each request it makes), after which the response is forwarded unmodified
