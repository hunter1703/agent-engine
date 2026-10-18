"""Pinterest mitmproxy addon (mitm.py).

Answers Pinterest's board picker request with the boards that visually match the pin, as
classified by the local Agent Engine.

Architecture:
  Pinterest iOS app -> mitmproxy (port 9000)
    -> answers GET /v3/users/boards/board_picker_shortlist/ itself, without asking Pinterest
    -> takes the pin (for its image url) and the user's boards
    -> asks the classifier AGENT_ID (Agent Engine /v1/agent/{id}/send-temp) about the image
    -> resolves board names -> boards
    -> returns the best MAX_BOARD_RESULTS matching boards
    -> returns them to the Pinterest iOS app in the shape of Pinterest's own shortlist

The images of the last MAX_REMEMBERED_PINS pins in Pinterest's responses to the app are
remembered, so the pin is not fetched when the picker is requested. The user's boards are kept for BOARDS_TTL_SECONDS. The work runs off mitmproxy's event loop and is bounded by DEADLINE_SECONDS.
When it does not finish in time, fails, or finds no match, the request goes on to Pinterest. So
does the first picker request of a user, whose shortlist names the user.

Run:
  mitmweb -v --listen-host 0.0.0.0 --listen-port 9000 --web-port 9001 -s mitm.py
"""

import asyncio
import hashlib
import json
import logging
import os
import re
import subprocess
import threading
import time
import traceback
import urllib3
import uuid
from collections import OrderedDict

from mitmproxy import ctx, http
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
# The board picker request does not say which pin is being saved; the app opens the pin with a
# request for its details, at the same moment.
PIN_DETAILS_PATH = re.compile(r"^/v3/pins/(\d+)/$")
PIN_MAX_AGE_SECONDS = 60.0
MAX_REMEMBERED_PINS = 100

DEADLINE_SECONDS = float(os.environ.get("DEADLINE_SECONDS", "8.0"))
MIN_BOARD_SCORE = float(os.environ.get("MIN_BOARD_SCORE", "0.5"))
# The picker shows four suggestions; the app also removes the boards of a longer shortlist from
# its list of all boards.
MAX_BOARD_RESULTS = int(os.environ.get("MAX_BOARD_RESULTS", "4"))
IMAGE_MIN_SIZE = 474
PICTURE_HOST_SUFFIX = "pinimg.com"
MAX_PICTURE_BYTES = 1_000_000
# Where the backend reads a picture the proxy serves: this machine, as the backend's containers
# see it, on PICTURE_PORT.
PICTURE_HOST = os.environ.get("PICTURE_HOST", "host.docker.internal")
PICTURE_PORT = int(os.environ.get("PICTURE_PORT", "9002"))
PICKER_ENDPOINT_NAME = "v3_get_board_picker_boards_shortlist"
BOARDS_TTL_SECONDS = 300.0
BOARDS_PAGE_SIZE = 250
OPEN_FILES_CHECK_SECONDS = 30
OPEN_FILES_REPORT_ABOVE = 150

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
    """Logs to mitmproxy's event log, which the terminal and the web view's Event Log both show."""
    try:
        ctx.log.info(f"[addon] {msg}")
    except Exception:  # no mitmproxy context yet, while the script is loading
        print(f"[addon] {msg}", flush=True)


def dbg_block(title, body, max_len=1500):
    s = body if isinstance(body, str) else json.dumps(body, default=str)
    if len(s) > max_len:
        s = s[:max_len] + f"... [truncated, total {len(s)} chars]"
    dbg(f"--- {title} ---")
    for line in s.splitlines() or [s]:
        dbg(f"    {line}")
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


class RecentPictures:
    """The images of the last `limit` pins: their addresses, preferred first, and the content of
    those the app has loaded."""

    def __init__(self, limit):
        self._limit = limit
        self._pin_id_to_urls = OrderedDict()
        self._url_to_pin_id = {}
        self._url_to_picture = {}

    def remember(self, pin_id, urls):
        if not urls:
            return
        if self._pin_id_to_urls.get(pin_id) == urls:
            self._pin_id_to_urls.move_to_end(pin_id)
            return
        self._forget(pin_id)
        self._pin_id_to_urls[pin_id] = urls
        for url in urls:
            self._url_to_pin_id[url] = pin_id
        while len(self._pin_id_to_urls) > self._limit:
            self._forget(next(iter(self._pin_id_to_urls)))

    def urls(self, pin_id):
        return self._pin_id_to_urls.get(pin_id, [])

    def keep(self, url, content, mime_type):
        """Keeps the content of an image of a remembered pin, unless it is large."""
        if url in self._url_to_pin_id and 0 < len(content) <= MAX_PICTURE_BYTES:
            self._url_to_picture[url] = (content, mime_type)

    def loaded_url(self, pin_id):
        """The first address of the pin's image whose content has been kept, if any."""
        return next((url for url in self.urls(pin_id) if url in self._url_to_picture), None)

    def picture(self, token):
        """The (content, mime type) of the kept image whose address has this token."""
        return next(
            (picture for url, picture in self._url_to_picture.items() if self.token(url) == token),
            None,
        )

    @staticmethod
    def token(url):
        return hashlib.sha1(url.encode("utf-8")).hexdigest()[:20]

    def _forget(self, pin_id):
        for url in self._pin_id_to_urls.pop(pin_id, []):
            self._url_to_pin_id.pop(url, None)
            self._url_to_picture.pop(url, None)


class PinterestBoardPicker:

    def __init__(self):
        # Per user (by credential): the id of the user, their boards with when they were read, and
        # the pin opened last with when.
        self._auth_to_user_id = {}
        self._auth_to_boards = {}
        self._auth_refreshing_boards = set()
        self._auth_to_opened = {}
        # The images of the pins seen in Pinterest's responses to the app, and the pictures loaded.
        self._recent_pictures = RecentPictures(MAX_REMEMBERED_PINS)
        self._picture_server = None

    # -- mitmproxy hooks -----------------------------------------------------

    def running(self) -> None:
        asyncio.ensure_future(self._watch_open_files())
        asyncio.ensure_future(self._serve_pictures())

    def done(self) -> None:
        if self._picture_server is not None:
            self._picture_server.close()

    async def request(self, flow: http.HTTPFlow) -> None:
        """Answers the board picker request itself, so Pinterest is not asked for a shortlist. When
        it cannot, the request goes on to Pinterest."""
        if flow.request.pretty_host != TARGET_HOST or flow.request.method != "GET":
            return
        auth = flow.request.headers.get("authorization")
        path = flow.request.path.split("?")[0]
        opened = PIN_DETAILS_PATH.match(path)
        if opened and auth:
            self._auth_to_opened[auth] = (opened.group(1), time.time())
        if path != TARGET_PATH or not auth:
            return

        pin_id = flow.request.query.get("pin") or self._opened_pin(auth)
        if pin_id:
            self._auth_to_opened[auth] = (pin_id, time.time())
        user_id = self._auth_to_user_id.get(auth)
        if not pin_id or not user_id:
            dbg(f"board picker passed on (pin_id={pin_id!r}, user_id={user_id})")
            return

        dbg("=" * 70)
        dbg(f"answering for pin {pin_id}")
        t0 = time.time()
        try:
            boards = await asyncio.wait_for(
                self._recommended_boards(
                    pin_id, auth, flow.request.headers.get("cookie"), user_id,
                    flow.request.query.get("fields"),
                ),
                DEADLINE_SECONDS,
            )
        except asyncio.TimeoutError:
            dbg(f"deadline of {DEADLINE_SECONDS}s exceeded; passing the request on")
            return
        except Exception as e:
            dbg(f"answer FAILED: {type(e).__name__}: {e}")
            traceback.print_exc()
            return
        if not boards:
            dbg("  -> no match, passing the request on")
            return

        body = {
            "status": "success",
            "code": 0,
            "message": "OK",
            "endpoint_name": PICKER_ENDPOINT_NAME,
            "data": boards,
        }
        flow.response = http.Response.make(
            200, json.dumps(body), {"Content-Type": "application/json"}
        )
        dbg(f"ANSWERED with {len(boards)} boards. Elapsed: {(time.time()-t0)*1000:.0f}ms")
        dbg("=" * 70)

    async def response(self, flow: http.HTTPFlow) -> None:
        if flow.response.status_code != 200:
            return
        if flow.request.pretty_host.endswith(PICTURE_HOST_SUFFIX):
            self._recent_pictures.keep(
                flow.request.pretty_url,
                flow.response.content,
                flow.response.headers.get("content-type", "image/jpeg"),
            )
            return
        if flow.request.pretty_host != TARGET_HOST:
            return
        try:
            data = flow.response.json()
        except ValueError:
            return
        self._remember_pin_images(data)
        auth = flow.request.headers.get("authorization")
        if flow.request.path.startswith(TARGET_PATH) and auth:
            # Its shortlist names the user, so later requests can be answered.
            user_id = self._extract_user_id(data.get("data", []))
            if user_id and self._auth_to_user_id.get(auth) != user_id:
                self._auth_to_user_id[auth] = user_id
                dbg(f"learned user id {user_id}")
                asyncio.ensure_future(asyncio.to_thread(
                    self._get_boards, user_id, auth, flow.request.headers.get("cookie"),
                    flow.request.query.get("fields"),
                ))

    async def _serve_pictures(self):
        """Serves the pictures the app has loaded on PICTURE_PORT, each at an address of its own,
        so that the backend can read one from here rather than from Pinterest's servers."""
        try:
            self._picture_server = await asyncio.start_server(
                self._answer_picture_request, "0.0.0.0", PICTURE_PORT
            )
        except OSError as e:
            dbg(f"pictures cannot be served on port {PICTURE_PORT}: {e}")

    async def _answer_picture_request(self, reader, writer):
        try:
            head = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), 5)
            token = head.split(b" ", 2)[1].decode(errors="replace").strip("/").split(".")[0]
            picture = self._recent_pictures.picture(token)
            if picture is None:
                writer.write(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            else:
                content, mime_type = picture
                writer.write(
                    b"HTTP/1.1 200 OK\r\nContent-Type: " + mime_type.encode()
                    + b"\r\nContent-Length: " + str(len(content)).encode()
                    + b"\r\nConnection: close\r\n\r\n" + content
                )
            await writer.drain()
        except (asyncio.TimeoutError, asyncio.IncompleteReadError, asyncio.LimitOverrunError,
                ConnectionError, IndexError):
            pass
        finally:
            writer.close()

    @staticmethod
    def _served_address(url):
        return f"http://{PICTURE_HOST}:{PICTURE_PORT}/{RecentPictures.token(url)}.jpg"

    async def _watch_open_files(self):
        """Every OPEN_FILES_CHECK_SECONDS, logs what holds the proxy's open files while it has more
        than OPEN_FILES_REPORT_ABOVE: how many, and its TCP connections by state and by the five
        remote addresses with the most."""
        while True:
            await asyncio.sleep(OPEN_FILES_CHECK_SECONDS)
            open_files = len(os.listdir("/dev/fd"))
            if open_files > OPEN_FILES_REPORT_ABOVE:
                dbg(f"open files: {open_files}; {await self._describe_connections()}")

    @staticmethod
    async def _describe_connections():
        try:
            process = await asyncio.create_subprocess_exec(
                "lsof", "-nP", "-a", "-p", str(os.getpid()), "-iTCP",
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            )
            output, _ = await process.communicate()
        except OSError as e:
            return f"connections not listed ({e})"
        state_vs_count, remote_vs_count = {}, {}
        for line in output.decode(errors="replace").splitlines()[1:]:
            fields = line.split()
            state = fields[-1].strip("()") if fields and fields[-1].startswith("(") else "-"
            state_vs_count[state] = state_vs_count.get(state, 0) + 1
            remote = fields[-2].split("->")[-1].rsplit(":", 1)[0] if "->" in line else "-"
            remote_vs_count[remote] = remote_vs_count.get(remote, 0) + 1
        top = sorted(remote_vs_count.items(), key=lambda item: -item[1])[:5]
        return f"TCP by state {state_vs_count}; most connections to {top}"

    def _remember_pin_images(self, node):
        """Keeps the image of every pin in a response, at most MAX_REMEMBERED_PINS of them."""
        if isinstance(node, list):
            for item in node:
                self._remember_pin_images(item)
        elif isinstance(node, dict):
            if node.get("type") == "pin" and node.get("id"):
                self._recent_pictures.remember(str(node["id"]), self._image_urls(node))
            for value in node.values():
                if isinstance(value, (list, dict)):
                    self._remember_pin_images(value)

    def _opened_pin(self, auth):
        """The id of the pin the user opened last, if recently."""
        pin_id, opened_at = self._auth_to_opened.get(auth, (None, 0.0))
        return pin_id if time.time() - opened_at <= PIN_MAX_AGE_SECONDS else None

    # -- Recommendation ------------------------------------------------------

    async def _recommended_boards(self, pin_id, auth, cookie, user_id, fields):
        """The boards the pin's image matches, best first, shaped as shortlist entries; empty when
        there is no match. `fields` are the board fields to ask Pinterest for, if the boards are
        read. The pin is fetched when its image has not been seen."""
        image_was_seen = bool(self._recent_pictures.urls(pin_id))
        started = time.time()
        if image_was_seen:
            all_boards = await asyncio.to_thread(self._get_boards, user_id, auth, cookie, fields)
        else:
            dbg(f"pin {pin_id}: image not seen, fetching the pin")
            pin, all_boards = await asyncio.gather(
                asyncio.to_thread(self._get_pin, pin_id, auth, cookie),
                asyncio.to_thread(self._get_boards, user_id, auth, cookie, fields),
            )
            self._recent_pictures.remember(pin_id, self._image_urls(pin) if pin else [])
        urls = self._recent_pictures.urls(pin_id)
        if not urls or not all_boards:
            return []

        names = [b.get("name", "").strip() for b in all_boards if b.get("name")]
        dbg(f"pin {pin_id}: {len(names)} candidate names")
        prepared = time.time()
        loaded_url = self._recent_pictures.loaded_url(pin_id)
        served = loaded_url is not None
        ranked = await asyncio.to_thread(
            self._match, self._served_address(loaded_url) if served else urls[0], names
        )
        if served and not ranked:
            # The backend may not have reached this machine; the image's own address is the check.
            ranked = await asyncio.to_thread(self._match, urls[0], names)
        dbg(f"agent ranked matches: {ranked}")
        dbg(
            f"timing: pin image {'seen' if image_was_seen else 'fetched'}, "
            f"picture {'served from here' if served else 'read by address'}, "
            f"pin and boards {(prepared - started) * 1000:.0f} ms, "
            f"classifier {(time.time() - prepared) * 1000:.0f} ms"
        )

        id_vs_entry = {}
        for name, probability in ranked:
            board = self._name_to_board(all_boards, name)
            if board and board["id"] not in id_vs_entry:
                reason = 2 if not id_vs_entry else 1
                id_vs_entry[board["id"]] = self._as_shortlist_entry(board, probability, reason)
        return list(id_vs_entry.values())

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

    def _image_urls(self, pin):
        """
        The addresses of a pin's images, preferred first: the smallest one at least IMAGE_MIN_SIZE
        wide, then larger ones, then smaller ones, then the original.
        Pinterest returns images in two shapes:
          A) nested map: pin["images"]["236x"|"474x"|"originals"] = {"url": ...}
          B) flat fields: pin["image_square_url"|"image_medium_url"|"image_large_url"]
                          with matching "_size_pixels" objects
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

        wide_enough = sorted((c for c in candidates if c[0] is not None and c[0] >= IMAGE_MIN_SIZE))
        smaller = sorted((c for c in candidates if c[0] is not None and c[0] < IMAGE_MIN_SIZE), reverse=True)
        unknown_size = [c for c in candidates if c[0] is None]
        urls = [c[1] for c in wide_enough + smaller] + ([originals_url] if originals_url else [])
        urls += [c[1] for c in unknown_size]
        return list(dict.fromkeys(urls))

    def _get_boards(self, user_id, auth, cookie, fields):
        """The user's boards: read on first use, then returned at once from what was read, which a
        background read replaces once it is older than BOARDS_TTL_SECONDS."""
        boards, read_at = self._auth_to_boards.get(auth, (None, 0.0))
        if not boards:
            return self._read_boards(user_id, auth, cookie, fields)
        if time.time() - read_at > BOARDS_TTL_SECONDS and auth not in self._auth_refreshing_boards:
            self._auth_refreshing_boards.add(auth)
            threading.Thread(
                target=self._refresh_boards, args=(user_id, auth, cookie, fields), daemon=True
            ).start()
        return boards

    def _refresh_boards(self, user_id, auth, cookie, fields):
        try:
            self._read_boards(user_id, auth, cookie, fields)
        finally:
            self._auth_refreshing_boards.discard(auth)

    def _read_boards(self, user_id, auth, cookie, fields):
        boards = self._get_all_boards(user_id, auth, cookie, fields)
        if boards:
            self._auth_to_boards[auth] = (boards, time.time())
        return boards

    def _get_all_boards(self, user_id, auth, cookie, fields):
        url = f"https://api.pinterest.com/v3/users/{user_id}/boards/feed/"
        params = {
            "page_size": str(BOARDS_PAGE_SIZE),
            "sort": "last_pinned_to",
            "filter_all_pins": "true",
            "filter_collage": "true",
        }
        if fields:
            params["fields"] = fields
        headers = {"Authorization": auth}
        if cookie:
            headers["Cookie"] = cookie

        boards = []
        t0 = time.time()
        while True:
            try:
                r = pinterest_session.get(
                    url, headers=headers, params=params, timeout=DEADLINE_SECONDS
                )
                if r.status_code != 200:
                    dbg_block("boards error body", r.text, max_len=800)
                    return boards
                payload = r.json()
            except Exception as e:
                dbg(f"  boards fetch FAILED: {type(e).__name__}: {e}")
                return boards
            page = payload.get("data", []) or []
            boards += page
            bookmark = payload.get("bookmark")
            if not page or not bookmark or bookmark == "-end-":
                break
            params["bookmark"] = bookmark
        dbg(f"  boards: {len(boards)} in {(time.time()-t0)*1000:.0f}ms")
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
        dbg(
            to_curl(
                "POST",
                agent_url,
                headers,
                {"session": "<redacted>"},
                body,
                verify=not AGENT_ENGINE_INSECURE,
            )
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
            dbg(r.text)
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
        usable = [
            label
            for label in labels
            if isinstance(label, dict) and "label" in label and "probability" in label
        ]
        # The probability of strong matches saturates near 1, so the similarity breaks ties.
        usable.sort(key=lambda label: (-float(label["probability"]), -float(label.get("score", 0))))
        out = [(str(label["label"]), float(label["probability"])) for label in usable]
        dbg(f"  parsed matches -> {out}")
        return out

    # -- Response rewrite ----------------------------------------------------

    @staticmethod
    def _name_to_board(all_boards, name):
        """Case-insensitive, whitespace-normalized name or direct ID -> board."""
        for b in all_boards:
            if str(b.get("id")) == str(name):
                return b

        target = " ".join(name.strip().lower().split())
        for b in all_boards:
            if " ".join((b.get("name") or "").strip().lower().split()) == target:
                return b
        return None

    @staticmethod
    def _as_shortlist_entry(board, probability, reason):
        """A board as Pinterest's shortlist has it. Pinterest gives the first board reason 2 and the
        others reason 1, and the app shows the shortlist only when it is marked that way."""
        entry = dict(board)
        entry.setdefault("action", {})
        entry["recommendation_reason"] = json.dumps({"reason": reason, "score": probability})
        entry.setdefault("ideas_deep_link_url", None)
        entry.setdefault("has_achievement", False)
        entry.setdefault("is_ads_only", False)
        return entry


# ---------------------------------------------------------------------------

dbg("addon loaded")
addons = [PinterestBoardPicker()]
dbg("addon registered")
