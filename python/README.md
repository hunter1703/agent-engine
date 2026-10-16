# Python Services & Integrations

This module contains Python-based runtime services and integrations for `agent-engine`.

## Components

1. **`model/` (Local Model Server)**:
   - High-throughput loopback embedding service running on `http://127.0.0.1:8081`.
   - Exposes standard OpenAI-compatible `POST /v1/embeddings` endpoint. An input image yields zero or more entries of `data`, each carrying the `index` of its image.
   - Models:
     - `arcface`: SCRFD face detection + ArcFace 512-d embedding (L2 normalized, zero-padded with 256 zeros to 768-d), one per detected face.
     - `siglip`: `google/siglip-base-patch16-256` yielding native 768-d unit vectors on MPS/CUDA/CPU, one per image.

2. **`mitm.py` (Pinterest MITM Addon)**:
   - `mitmproxy` addon intercepting Pinterest iOS `GET /v3/users/boards/board_picker_shortlist/`.
   - Calls the local Agent Engine (`POST /v1/agent/{agentId}/send-temp`) with the pin image attachment; the answer is the classifier's `{"labels": [...]}`.
   - Resolves visual classification matches against boards and answers the picker request itself with the best matching boards, as many as the app asks for in its `count` (the app shows no more, and hides the boards of a longer answer from its list of all boards), without asking Pinterest.

## Running

### Prerequisites
Install dependencies with `uv`:
```bash
cd python
uv sync
```

### One entry point: `run.py`
```bash
cd python
uv run python run.py download <pinterest-url>   # gallery-dl -> training.zip (replaced when done)
uv run python run.py training                   # model server + index the images of training.zip
uv run python run.py start                      # model server + proxy for the iPhone, no dashboard
```
`training` and `start` take `--engine-url` (default `$AGENT_ENGINE_URL` or `https://agents.com`) and
`--insecure/--no-insecure` (on by default: the local certificate is self-signed). `training` skips
images that are already indexed unless given `--reindex`. A model server that is already running
is reused and left running.

### Training data
The boards are kept only in `training.zip`, an AES-256 encrypted archive of the folders gallery-dl
downloads. The password is `$TRAINING_ZIP_PASSWORD`, or the one set in `training_data.py`. Nothing
reads it by extracting it: `ingest_knowledge.py` and `calibrate_classifier.py` read one image at a
time in memory. `download` fetches the boards with the cookies in `cookies.txt`
(exported from a logged-in browser) into a temporary folder that is deleted once the archive is
written. `training.zip` and `cookies.txt` are git-ignored.

### Running the parts separately
```bash
cd python
uv run python -m model.server                   # the model server
uv run python ingest_knowledge.py --skip-existing
AGENT_ENGINE_URL=https://agents.com AGENT_ENGINE_INSECURE=true \
uv run mitmweb -v --listen-host 0.0.0.0 --listen-port 9000 --web-port 9001 -s mitm.py
```

Phone side: import `clash.yaml` into the Clash app on the iPhone. It sends only
`api.pinterest.com` to the Mac, trying its Bonjour name and its known addresses in turn, and
everything else goes direct. The mitmproxy certificate must be installed and trusted on the phone.

Environment variables for `mitm.py`:
- `AGENT_ENGINE_URL`: default `http://localhost:8080`
- `AGENT_ENGINE_CA_BUNDLE`: CA certificate file that verifies the Agent Engine, e.g. mkcert's `$(mkcert -CAROOT)/rootCA.pem`
- `AGENT_ENGINE_INSECURE`: set to `true` to skip TLS verification of the Agent Engine only; Pinterest's certificate is always verified
- `AGENT_ID`: the classifier agent asked about each pin, default `image_classifier`
- `AGENT_SESSION_COOKIE`: default `systemSession`
- `MIN_BOARD_SCORE`: default `0.5`
- `MAX_BOARD_RESULTS`: default `10`; the most boards the classifier's answer is cut to, and the answer's length when the app does not ask for a number
- `DEADLINE_SECONDS`: default `8.0`; the longest the addon holds a shortlist response (and each request it makes), after which the response is forwarded unmodified
