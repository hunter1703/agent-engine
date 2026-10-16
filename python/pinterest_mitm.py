"""Pinterest mitmproxy addon (pinterest_mitm.py).

Rewrites Pinterest's board picker shortlist response based on visual semantic match
returned by the local Agent Engine.

Architecture:
  Pinterest iOS app -> mitmproxy (port 9000)
    -> intercepts GET /v3/users/boards/board_picker_shortlist/
    -> fetches the pin (for its image url) and the user's boards, concurrently
    -> asks the classifier AGENT_ID (Agent Engine /v1/agent/{id}/send-temp) about the image
    -> resolves board name -> board ID
    -> reorders or injects the matching board to position 0 in shortlist
    -> forwards modified response to Pinterest iOS app

The work runs off mitmproxy's event loop and is bounded by DEADLINE_SECONDS. When it does not
finish in time, or fails, the shortlist is forwarded unmodified.

Run:
  mitmweb -v --listen-host 0.0.0.0 --listen-port 9000 --web-port 9001 -s pinterest_mitm.py
"""

import asyncio
import json
import logging
import os
import time
import traceback
import urllib3
import uuid

from mitmproxy import http
import requests

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

AGENT_ENGINE_URL = os.environ.get("AGENT_ENGINE_URL", "http://localhost:8080")
AGENT_ID = os.environ.get("AGENT_ID", "image_classifier")
AGENT_SESSION_COOKIE = os.environ.get("AGENT_SESSION_COOKIE", "systemSession")
AGENT_ENGINE_INSECURE = os.environ.get("AGENT_ENGINE_INSECURE", "").lower() in (
    "1",
    "true",
    "yes",
)
AGENT_ENGINE_CA_BUNDLE = os.environ.get("AGENT_ENGINE_CA_BUNDLE")

TARGET_HOST = "api.pinterest.com"
TARGET_PATH = "/v3/users/boards/board_picker_shortlist/"

DEADLINE_SECONDS = float(os.environ.get("DEADLINE_SECONDS", "8.0"))
MIN_BOARD_SCORE = float(os.environ.get("MIN_BOARD_SCORE", "0.5"))
MAX_BOARD_RESULTS = int(os.environ.get("MAX_BOARD_RESULTS", "5"))
IMAGE_MIN_SIZE = 236

log = logging.getLogger("pinterest_addon")


def _new_session(verify):
    new_session = requests.Session()
    new_session.trust_env = False
    new_session.proxies = {"http": None, "https": None}
    new_session.verify = verify
    return new_session


# Pinterest's certificate is always verified: its requests carry the user's credentials.
pinterest_session = _new_session(True)
engine_session = _new_session(
    False if AGENT_ENGINE_INSECURE else (AGENT_ENGINE_CA_BUNDLE or True)
)
if AGENT_ENGINE_INSECURE:
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


# ---------------------------------------------------------------------------
# Logging & Debugging Helpers
# ---------------------------------------------------------------------------


def dbg(msg):
    print(f"[addon] {msg}", flush=True)


def dbg_block(title, body, max_len=1500):
    s = body if isinstance(body, str) else json.dumps(body, default=str)
    if len(s) > max_len:
        s = s[:max_len] + f"... [truncated, total {len(s)} chars]"
    dbg(f"--- {title} ---")
    for line in s.splitlines() or [s]:
        print(f"    {line}", flush=True)
    dbg(f"--- end {title} ---")


def to_curl(method, url, headers, cookies, json_body, verify=True):
    """Render a request as a copy-pasteable curl command."""
    parts = ["curl -sS"]
    if not verify:
        parts.append("--insecure")
    parts.append(f"-X {method}")
    parts.append(f"--url '{url}'")
    for k, v in (headers or {}).items():
        esc = str(v).replace("'", "'\\''")
        parts.append(f"-H '{k}: {esc}'")
    if cookies:
        cookie_str = "; ".join(f"{k}={v}" for k, v in cookies.items())
        parts.append(f"-b '{cookie_str}'")
    if json_body is not None:
        payload = json.dumps(json_body)
        esc = payload.replace("'", "'\\''")
        parts.append(f"--data-raw '{esc}'")
    return " \\\n  ".join(parts)


# ---------------------------------------------------------------------------
# Addon Class
# ---------------------------------------------------------------------------


class PinterestBoardPicker:

    # -- mitmproxy hook ------------------------------------------------------

    async def response(self, flow: http.HTTPFlow) -> None:
        if flow.request.host != TARGET_HOST:
            return
        if not flow.request.path.startswith(TARGET_PATH):
            return
        if flow.response.status_code != 200:
            dbg("board picker response skipped (non-200)")
            return

        pin_id = flow.request.query.get("pin")
        auth = flow.request.headers.get("authorization")
        cookie = flow.request.headers.get("cookie")
        if not pin_id or not auth:
            dbg(f"board picker response skipped (pin_id={pin_id!r}, auth={bool(auth)})")
            return

        dbg("=" * 70)
        dbg(f"intercepting {flow.request.method} {flow.request.pretty_url}")
        t0 = time.time()
        try:
            content = await asyncio.wait_for(
                self._rewritten_content(flow.response.content, pin_id, auth, cookie),
                DEADLINE_SECONDS,
            )
        except asyncio.TimeoutError:
            dbg(f"deadline of {DEADLINE_SECONDS}s exceeded; leaving response untouched")
            return
        except Exception as e:
            dbg(f"rewrite FAILED: {type(e).__name__}: {e}")
            traceback.print_exc()
            return

        if content is None:
            dbg("  -> no match, leaving response untouched")
            return
        flow.response.content = content
        dbg(f"RESPONSE REWRITTEN. Elapsed: {(time.time()-t0)*1000:.0f}ms")
        dbg("=" * 70)

    # -- Rewrite -------------------------------------------------------------

    async def _rewritten_content(self, body, pin_id, auth, cookie):
        """The shortlist response body with the matching board first, or None to leave it."""
        try:
            data = json.loads(body)
        except Exception as e:
            dbg(f"JSON parse FAILED: {e}")
            return None

        shortlist = data.get("data", [])
        dbg(f"shortlist size={len(shortlist)}")
        if not shortlist:
            return None
        for i, b in enumerate(shortlist):
            dbg(f"  shortlist[{i}]: id={b.get('id')} name={b.get('name')!r}")

        user_id = self._extract_user_id(shortlist)
        dbg(f"pin_id={pin_id!r} user_id={user_id}")
        if not user_id:
            return None

        pin, all_boards = await asyncio.gather(
            asyncio.to_thread(self._get_pin, pin_id, auth, cookie),
            asyncio.to_thread(self._get_all_boards, user_id, auth, cookie),
        )
        if not pin or not all_boards:
            return None

        image_url = self._extract_image_url(pin)
        if not image_url:
            return None

        names = [b.get("name", "").strip() for b in all_boards if b.get("name")]
        dbg(f"candidate names: {len(names)}")
        if not names:
            return None

        ranked = await asyncio.to_thread(self._match, image_url, names)
        dbg(f"agent ranked matches: {ranked}")

        matched_id = None
        for name, score in ranked:
            resolved = self._name_to_id(all_boards, name)
            dbg(f"  resolving {name!r} (score={score}) -> id {resolved}")
            if resolved:
                matched_id = resolved
                break
        if not matched_id:
            dbg("no returned match resolved to a board id")
            return None

        self._apply(shortlist, all_boards, matched_id)
        return json.dumps(data).encode("utf-8")

    # -- Pinterest helpers ---------------------------------------------------

    @staticmethod
    def _extract_user_id(shortlist):
        for b in shortlist:
            uid = (b.get("owner") or {}).get("id")
            if uid:
                return uid
        return None

    def _get_pin(self, pin_id, auth, cookie):
        url = f"https://api.pinterest.com/v3/pins/{pin_id}/"
        headers = {"Authorization": auth}
        if cookie:
            headers["Cookie"] = cookie
        dbg(f"GET {url}")
        t0 = time.time()
        try:
            r = pinterest_session.get(url, headers=headers, timeout=DEADLINE_SECONDS)
            dbg(f"  pin: status={r.status_code} len={len(r.content)} time={(time.time()-t0)*1000:.0f}ms")
            if r.status_code != 200:
                dbg_block("pin error body", r.text, max_len=500)
                return None
            return r.json().get("data", {})
        except Exception as e:
            dbg(f"  pin fetch FAILED: {type(e).__name__}: {e}")
            return None

    def _extract_image_url(self, pin):
        """
        Pinterest returns images in two shapes:
          A) nested map: pin["images"]["236x"|"474x"|"originals"] = {"url": ...}
          B) flat fields: pin["image_square_url"|"image_medium_url"|"image_large_url"]
                          with matching "_size_pixels" objects
        Pick smallest >= IMAGE_MIN_SIZE.
        """
        candidates = []
        originals_url = None

        # Shape B
        for url_field, size_field in (
            ("image_square_url", "image_square_size_pixels"),
            ("image_medium_url", "image_medium_size_pixels"),
            ("image_large_url",  "image_large_size_pixels"),
        ):
            url = pin.get(url_field)
            if not isinstance(url, str) or not url.startswith(("http://", "https://")):
                continue
            size_obj = pin.get(size_field) or {}
            w = size_obj.get("width") if isinstance(size_obj, dict) else None
            if not isinstance(w, int):
                w = None
            candidates.append((w, url))

        # Shape A
        images = pin.get("images") or {}
        for key, entry in images.items():
            url = (entry or {}).get("url") if isinstance(entry, dict) else None
            if not isinstance(url, str) or not url.startswith(("http://", "https://")):
                continue
            if key == "originals":
                originals_url = url
                continue
            if not key.endswith("x"):
                continue
            try:
                w = int(key[:-1])
            except ValueError:
                continue
            candidates.append((w, url))

        dbg(f"  image candidates: {[(w, u[-40:]) for w, u in candidates]} originals={bool(originals_url)}")

        above = [c for c in candidates if c[0] is not None and c[0] >= IMAGE_MIN_SIZE]
        if above:
            chosen = min(above, key=lambda c: c[0])
            dbg(f"  chose width={chosen[0]} url={chosen[1]}")
            return chosen[1]

        known = [c for c in candidates if c[0] is not None]
        if known:
            chosen = max(known, key=lambda c: c[0])
            dbg(f"  fallback largest: width={chosen[0]} url={chosen[1]}")
            return chosen[1]

        if originals_url:
            dbg(f"  fallback originals: {originals_url}")
            return originals_url

        if candidates:
            return candidates[0][1]

        dbg("  NO IMAGE FOUND")
        return None

    def _get_all_boards(self, user_id, auth, cookie):
        url = f"https://api.pinterest.com/v3/users/{user_id}/boards/feed/"
        params = {
            "page_size": "250",
            "sort": "last_pinned_to",
            "filter_all_pins": "true",
            "filter_collage": "true",
        }
        headers = {"Authorization": auth}
        if cookie:
            headers["Cookie"] = cookie

        dbg(f"GET {url} params={params}")
        t0 = time.time()
        try:
            r = pinterest_session.get(
                url, headers=headers, params=params, timeout=DEADLINE_SECONDS
            )
            dbg(f"  boards: status={r.status_code} len={len(r.content)} time={(time.time()-t0)*1000:.0f}ms")
            if r.status_code != 200:
                dbg_block("boards error body", r.text, max_len=800)
                return []
            payload = r.json()
        except Exception as e:
            dbg(f"  boards fetch FAILED: {type(e).__name__}: {e}")
            return []

        boards = payload.get("data", []) or []
        if payload.get("bookmark") and len(boards) >= 250:
            dbg("  bookmark present (pagination not implemented)")
        return boards

    # -- Agent Engine --------------------------------------------------------

    def _match(self, image_url, names):
        """Asks the classifier which boards the image belongs with. Returns (name, probability)
        pairs, most probable first."""
        if not names:
            return []
        matches = [
            match
            for match in self._classify(AGENT_ID, image_url, names)
            if match[1] >= MIN_BOARD_SCORE
        ]
        matches.sort(key=lambda match: -match[1])
        return matches[:MAX_BOARD_RESULTS]

    def _classify(self, agent_id, image_url, names):
        """One classifier's answer: (board name, probability) pairs, most probable first."""
        body = {
            "messages": [
                {
                    "id": str(uuid.uuid4()),
                    "role": "user",
                    "content": json.dumps({"names": names}),
                }
            ],
            "context": [
                {
                    "description": "attachment",
                    "value": json.dumps({
                        "name": "photo.jpg",
                        "source": image_url,
                        "type": "URL",
                        "mimeType": "image/jpeg",
                        "size": 0,
                    }),
                }
            ],
        }

        agent_url = f"{AGENT_ENGINE_URL}/v1/agent/{agent_id}/send-temp"
        headers = {
            "content-type": "application/json",
            "origin": "http://localhost:3000",
        }
        cookies = {"session": AGENT_SESSION_COOKIE}

        dbg(f"POST {agent_url}")
        dbg(f"  names={len(names)} image={image_url}")

        try:
            with open("/tmp/agent_body.json", "w") as f:
                json.dump(body, f, indent=2)
            dbg("agent body dumped to /tmp/agent_body.json")
        except Exception as e:
            dbg(f"could not dump agent body: {e}")

        dbg("--- agent request as curl ---")
        print(
            to_curl(
                "POST",
                agent_url,
                headers,
                {"session": "<redacted>"},
                body,
                verify=not AGENT_ENGINE_INSECURE,
            ),
            flush=True,
        )
        dbg("--- end curl ---")

        t0 = time.time()
        try:
            r = engine_session.post(
                agent_url,
                json=body,
                headers=headers,
                cookies=cookies,
                timeout=DEADLINE_SECONDS,
            )
            elapsed_ms = (time.time() - t0) * 1000
            dbg(f"  agent response: status={r.status_code} len={len(r.content)} time={elapsed_ms:.0f}ms")
            dbg("--- agent raw response ---")
            print(r.text, flush=True)
            dbg("--- end agent raw response ---")
            r.raise_for_status()
            payload = r.json()
        except requests.exceptions.Timeout:
            dbg(f"  agent TIMEOUT after {DEADLINE_SECONDS}s")
            return []
        except requests.exceptions.ConnectionError as e:
            dbg(f"  agent CONNECTION ERROR: {e}")
            return []
        except Exception as e:
            dbg(f"  agent FAILED: {type(e).__name__}: {e}")
            traceback.print_exc()
            return []

        return self._parse_names(payload)

    @staticmethod
    def _parse_names(payload):
        """The (board name, probability) pairs of the classifier's answer, most probable first."""
        labels = payload.get("labels") if isinstance(payload, dict) else None
        if not isinstance(labels, list):
            dbg("  no labels in the classifier's answer")
            return []
        out = [
            (str(label["label"]), float(label["probability"]))
            for label in labels
            if isinstance(label, dict) and "label" in label and "probability" in label
        ]
        out.sort(key=lambda match: -match[1])
        dbg(f"  parsed matches -> {out}")
        return out

    # -- Response rewrite ----------------------------------------------------

    @staticmethod
    def _name_to_id(all_boards, name):
        """Case-insensitive, whitespace-normalized name or direct ID -> board id."""
        # First check if name directly matches an existing board id
        for b in all_boards:
            if str(b.get("id")) == str(name):
                return b.get("id")

        target = " ".join(name.strip().lower().split())
        for b in all_boards:
            candidate = " ".join((b.get("name") or "").strip().lower().split())
            if candidate == target:
                return b.get("id")
        return None

    @staticmethod
    def _apply(shortlist, all_boards, matched_id):
        # (a) reorder if already in shortlist
        for i, b in enumerate(shortlist):
            if b.get("id") == matched_id:
                if i != 0:
                    shortlist.insert(0, shortlist.pop(i))
                    dbg(f"  reordered {matched_id} to index 0")
                else:
                    dbg("  already at index 0")
                return

        # (b) inject from the user's board list
        chosen = next((b for b in all_boards if b.get("id") == matched_id), None)
        if not chosen:
            dbg(f"  {matched_id} not in the board list, cannot inject")
            return

        injected = dict(chosen)
        injected.setdefault("action", {})
        injected.setdefault(
            "recommendation_reason",
            json.dumps({"reason": 0, "score": 1.0}),
        )
        injected.setdefault("ideas_deep_link_url", None)
        injected.setdefault("has_achievement", False)
        injected.setdefault("is_ads_only", False)

        shortlist.insert(0, injected)
        dbg(f"  injected {matched_id} at index 0")


# ---------------------------------------------------------------------------

dbg("addon loaded")
addons = [PinterestBoardPicker()]
dbg("addon registered")
