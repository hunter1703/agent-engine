"""Local image embedding model server.

Exposes an OpenAI-compatible /v1/embeddings endpoint over loopback (127.0.0.1:8081).
Each input image yields zero or more embeddings, returned as separate entries of `data` whose
`index` is the input's position:
  - 'arcface': InsightFace (SCRFD face detection + ArcFace 512-d, normalized and zero-padded to
    768-d), one embedding per detected face; an image with no face yields none
  - 'siglip': SigLIP (google/siglip-base-patch16-256, 768-d unit vector), one embedding per image
"""

import base64
import io
import logging
import os
import threading
from contextlib import asynccontextmanager
from typing import List, Union

from fastapi import FastAPI, HTTPException
import numpy as np
from PIL import Image
from pydantic import BaseModel, Field
import uvicorn

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("model_server")

MIN_FACE_SCORE = 0.7



@asynccontextmanager
async def lifespan(_app: FastAPI):
    """Loads the models at startup so the first request does not pay for it."""
    for loader in (get_insightface, get_siglip):
        try:
            loader()
        except Exception:
            logger.exception("Could not preload with %s; it loads on first use", loader.__name__)
    yield


app = FastAPI(title="Local Image Embedding Model Server", version="0.1.0", lifespan=lifespan)

# ---------------------------------------------------------------------------
# Global Model Holders (Lazy Loaded)
# ---------------------------------------------------------------------------

_insightface_app = None
_siglip_model = None
_siglip_processor = None
_torch_device = None

# Requests run on a thread pool: a model is loaded by one thread at a time, and a model runs one
# inference at a time.
_load_lock = threading.Lock()
_infer_lock = threading.Lock()


def get_device():
    global _torch_device
    if _torch_device is None:
        import torch

        if torch.backends.mps.is_available():
            _torch_device = torch.device("mps")
        elif torch.cuda.is_available():
            _torch_device = torch.device("cuda")
        else:
            _torch_device = torch.device("cpu")
        logger.info("Using PyTorch device: %s", _torch_device)
    return _torch_device


def get_insightface():
    if _insightface_app is None:
        with _load_lock:
            _load_insightface()
    return _insightface_app


def _load_insightface():
    global _insightface_app
    if _insightface_app is None:
        logger.info("Loading InsightFace (buffalo_l)...")
        import onnxruntime as ort
        from insightface.app import FaceAnalysis

        available_providers = ort.get_available_providers()
        logger.info("Available ONNX Runtime execution providers: %s", available_providers)

        # CUDA if available, else CPU: CoreML measured slower than CPU for these models
        selected_providers = []
        for provider in ["CUDAExecutionProvider", "CPUExecutionProvider"]:
            if provider in available_providers:
                selected_providers.append(provider)
        if not selected_providers:
            selected_providers = ["CPUExecutionProvider"]

        logger.info("Selected providers for InsightFace: %s", selected_providers)

        app_instance = FaceAnalysis(
            name="buffalo_l",
            allowed_modules=["detection", "recognition"],
            providers=selected_providers,
        )
        ctx_id = 0 if "CUDAExecutionProvider" in selected_providers else -1
        app_instance.prepare(ctx_id=ctx_id, det_size=(640, 640))
        _insightface_app = app_instance
        logger.info("InsightFace loaded successfully.")
    return _insightface_app


def get_siglip():
    if _siglip_model is None or _siglip_processor is None:
        with _load_lock:
            _load_siglip()
    return _siglip_model, _siglip_processor


def _load_siglip():
    global _siglip_model, _siglip_processor
    if _siglip_model is None or _siglip_processor is None:
        logger.info("Loading SigLIP model (google/siglip-base-patch16-256)...")
        from transformers import AutoImageProcessor, AutoModel

        model_name = "google/siglip-base-patch16-256"
        device = get_device()
        _siglip_processor = AutoImageProcessor.from_pretrained(model_name)
        _siglip_model = AutoModel.from_pretrained(model_name).to(device)
        _siglip_model.eval()
        logger.info("SigLIP loaded successfully on %s.", device)
    return _siglip_model, _siglip_processor


# ---------------------------------------------------------------------------
# Schemas (OpenAI-compatible)
# ---------------------------------------------------------------------------


class EmbeddingRequest(BaseModel):
    model: str
    input: Union[str, List[str]] = Field(
        ..., description="Base64 encoded image string or list of base64 strings"
    )


class EmbeddingData(BaseModel):
    object: str = "embedding"
    index: int
    embedding: List[float]


class EmbeddingUsage(BaseModel):
    prompt_tokens: int = 0
    total_tokens: int = 0


class EmbeddingResponse(BaseModel):
    object: str = "list"
    data: List[EmbeddingData]
    model: str
    usage: EmbeddingUsage = Field(default_factory=EmbeddingUsage)


# ---------------------------------------------------------------------------
# Embedding Logic
# ---------------------------------------------------------------------------


def decode_image(b64_str: str) -> Image.Image:
    """Decodes a base64 string (with optional data: URI prefix) to a PIL Image in RGB mode."""
    if not b64_str:
        raise ValueError("Empty image input")
    if "," in b64_str and b64_str.startswith("data:"):
        b64_str = b64_str.split(",", 1)[1]
    raw_bytes = base64.b64decode(b64_str)
    image = Image.open(io.BytesIO(raw_bytes))
    return image.convert("RGB")


def embed_arcface(image: Image.Image) -> List[List[float]]:
    """One 512-d ArcFace embedding per detected face, L2 normalized and zero-padded to 768-d."""
    app_instance = get_insightface()
    # InsightFace expects BGR numpy array
    bgr_array = np.array(image)[:, :, ::-1]

    embeddings = []
    for face in app_instance.get(bgr_array):
        if face.det_score < MIN_FACE_SCORE:
            continue
        embedding = face.embedding
        norm = np.linalg.norm(embedding)
        if norm > 0:
            embedding = embedding / norm
        embeddings.append(np.pad(embedding, (0, 768 - len(embedding))).tolist())
    return embeddings


def embed_siglip(image: Image.Image) -> List[List[float]]:
    """The 768-d SigLIP image embedding, L2 normalized."""
    import torch

    model, processor = get_siglip()
    device = get_device()

    with torch.inference_mode():
        inputs = processor(images=image, return_tensors="pt").to(device)
        output = model.get_image_features(**inputs)
        features = output if torch.is_tensor(output) else output.pooler_output
        features = features / features.norm(dim=-1, keepdim=True)
        return [features[0].cpu().numpy().tolist()]


# ---------------------------------------------------------------------------
# API Routes
# ---------------------------------------------------------------------------


@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "supported_models": ["arcface", "siglip"],
        "vector_dimension": 768,
    }


@app.post("/v1/embeddings", response_model=EmbeddingResponse)
@app.post("/embeddings", response_model=EmbeddingResponse)
def create_embeddings(request: EmbeddingRequest):
    model_name = request.model.lower().strip()
    if model_name not in ("arcface", "siglip"):
        raise HTTPException(
            status_code=400,
            detail=f"Unsupported model '{request.model}'. Supported models: 'arcface', 'siglip'",
        )

    inputs = [request.input] if isinstance(request.input, str) else request.input
    results = []

    for index, b64 in enumerate(inputs):
        try:
            image = decode_image(b64)
        except Exception as e:
            logger.warning("Failed to decode image at index %d: %s", index, e)
            raise HTTPException(status_code=400, detail=f"Invalid base64 image at index {index}: {e}")

        with _infer_lock:
            vectors = embed_arcface(image) if model_name == "arcface" else embed_siglip(image)

        results.extend(EmbeddingData(index=index, embedding=vector) for vector in vectors)

    return EmbeddingResponse(model=request.model, data=results)


def main():
    host = os.environ.get("MODEL_SERVER_HOST", "0.0.0.0")
    port = int(os.environ.get("MODEL_SERVER_PORT", "8081"))
    uvicorn.run("model.server:app", host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
