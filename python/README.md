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
   - Resolves visual classification matches against boards and answers the picker request itself with the best matching boards (up to `MAX_BOARD_RESULTS`), marked the way Pinterest marks its own shortlist, without asking Pinterest.
   - Keeps the pictures the app loads for the last 100 pins and serves them from a small web server on `PICTURE_PORT`, so the classifier reads a picture from this machine instead of downloading it from Pinterest. Nothing is stored in the backend. When a picture was not seen, the classifier is given Pinterest's address for it, as before.

## Running

### Prerequisites
Install dependencies with `uv`:
```bash
cd python
uv sync
```

### One entry point: `modelae`
Install the command once, as editable so it keeps using the files in this folder (`training.zip`,
`cookies.txt`, `mitm.py`):
```bash
cd python
uv tool install --editable .
```
Then, from any folder:
```bash
modelae download <pinterest-url>   # gallery-dl -> training.zip (replaced when done)
modelae training                   # model server + index the images of training.zip
modelae start                      # model server + proxy for the iPhone, no dashboard
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
uv run python -m model.server                   # the model server alone
uv run python ingest_knowledge.py --skip-existing
AGENT_ENGINE_URL=https://agents.com AGENT_ENGINE_INSECURE=true \
uv run mitmweb -v --listen-host 0.0.0.0 --listen-port 9000 --web-port 9001 -s mitm.py
```

Phone side: import `clash.yaml` into the Clash app on the iPhone. It sends only
`api.pinterest.com` to the Mac, found by its Bonjour name (`r2d2.local`) so no address is needed, and
everything else goes direct. The mitmproxy certificate must be installed and trusted on the phone.

Use Clash in **Rule** mode. The profile rejects QUIC (UDP on ports 443 and 80), which the Mac
proxies cannot carry: without that the Pinterest app uses HTTP/3 and its traffic bypasses the
proxy, so only Global mode appeared to work. With the rule, the app falls back to HTTP/2 over TCP.

Environment variables for `mitm.py`:
- `AGENT_ENGINE_URL`: default `http://localhost:8080`
- `AGENT_ENGINE_CA_BUNDLE`: CA certificate file that verifies the Agent Engine, e.g. mkcert's `$(mkcert -CAROOT)/rootCA.pem`
- `AGENT_ENGINE_INSECURE`: set to `true` to skip TLS verification of the Agent Engine only; Pinterest's certificate is always verified
- `AGENT_ID`: the classifier agent asked about each pin, default `image_classifier`
- `AGENT_SESSION_COOKIE`: default `systemSession`
- `MIN_BOARD_SCORE`: default `0.5`
- `MAX_BOARD_RESULTS`: default `4`; the number of boards the picker shows. A longer answer is cut to it, because the app also removes the boards of a shortlist from its list of all boards
- `PICTURE_HOST`: default `host.docker.internal`; how the Agent Engine reaches this machine, to read a pin's picture from it
- `PICTURE_PORT`: default `9002`; the port the proxy serves the pictures it has seen on
- `DEADLINE_SECONDS`: default `8.0`; the longest the addon holds a shortlist response (and each request it makes), after which the response is forwarded unmodified
