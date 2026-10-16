"""Calibrates an image classifier's `matchThreshold` and `sharpness` on a labelled image folder.

The folder holds one subfolder per label, each with that label's images. Every image is embedded
by the model server (`/v1/embeddings`), which may return several vectors for it, such as one per
face. Each image is then classified against all the others (leave-one-out) the way
`ImageClassificationAgent` does: for each vector found in it, the vectors of the other images at
least `matchThreshold` similar are grouped by label, a label's similarity is the mean of its
`closest_chunks_per_tag` closest, and the best over the image's vectors is kept. A label with no
such vector is not listed.

The threshold is the one that lists the true label of an image and no other label most often (best
F1). The sharpness is fitted so that a listed label's probability, a logistic function of its
similarity above the threshold, matches how often it is the true label.

Usage:
    python calibrate_classifier.py DATASET --model arcface [--server http://localhost:8081]
        [--closest-chunks-per-tag 3]

Prints the precision and recall of the true label, and the values to put in the agent's config.
"""

from __future__ import annotations

import argparse
import base64
import sys
from pathlib import Path

import numpy as np
import requests

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
BATCH_SIZE = 8


def embed_images(server: str, model: str, paths: list[Path]) -> list[np.ndarray]:
    """For each image, an array of the vectors the server found in it (possibly none)."""
    vectors: list[list[list[float]]] = [[] for _ in paths]
    for start in range(0, len(paths), BATCH_SIZE):
        batch = paths[start : start + BATCH_SIZE]
        body = {
            "model": model,
            "input": [base64.b64encode(p.read_bytes()).decode("ascii") for p in batch],
        }
        response = requests.post(f"{server}/v1/embeddings", json=body, timeout=300)
        response.raise_for_status()
        for entry in response.json()["data"]:
            vectors[start + entry["index"]].append(entry["embedding"])
    return [np.asarray(v, dtype=np.float32).reshape(-1, 768) for v in vectors]


def tag_similarity(
    queries: np.ndarray, pool: np.ndarray, threshold: float, closest: int
) -> float | None:
    """The mean of the `closest` vectors of `pool` at least `threshold` similar to a query vector,
    the best over the query vectors; None when no vector of the pool is."""
    best = None
    sims = queries @ pool.T if len(pool) else np.zeros((len(queries), 0))
    for row in sims:
        close = np.sort(row[row >= threshold])[::-1][:closest]
        if len(close):
            mean = float(close.mean())
            best = mean if best is None else max(best, mean)
    return best


def fit_sharpness(similarities: np.ndarray, truth: np.ndarray, threshold: float) -> float:
    """Fits probability = sigmoid(sharpness * (similarity - threshold)) by gradient descent."""
    a = 10.0
    x = similarities - threshold
    for _ in range(20000):
        p = 1.0 / (1.0 + np.exp(-a * x))
        a -= 0.5 * float(np.mean((p - truth) * x))
    return float(np.clip(a, 1.0, 60.0))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--model", required=True, help="Embedding model name known to the server")
    parser.add_argument("--server", default="http://localhost:8081")
    parser.add_argument("--closest-chunks-per-tag", type=int, default=3)
    args = parser.parse_args()

    labels: list[str] = []
    paths: list[Path] = []
    for folder in sorted(p for p in args.dataset.iterdir() if p.is_dir()):
        for image in sorted(folder.iterdir()):
            if image.suffix.lower() in IMAGE_EXTENSIONS:
                labels.append(folder.name)
                paths.append(image)
    names = sorted(set(labels))
    if len(names) < 2:
        print("Need at least two label folders", file=sys.stderr)
        return 1

    vectors = embed_images(args.server, args.model, paths)
    with_vectors = [i for i, v in enumerate(vectors) if len(v)]
    print(f"{len(paths)} images, {len(names)} labels, {len(with_vectors)} with at least one vector, "
          f"{sum(len(v) for v in vectors)} vectors")

    # For every image and label, the pool is the vectors of the label's other images.
    pools = {
        (i, name): np.concatenate(
            [vectors[j] for j in with_vectors if j != i and labels[j] == name]
            or [np.zeros((0, 768), dtype=np.float32)]
        )
        for i in with_vectors
        for name in names
    }

    def classify(threshold: float) -> dict[tuple[int, str], float]:
        found = {}
        for (i, name), pool in pools.items():
            similarity = tag_similarity(vectors[i], pool, threshold, args.closest_chunks_per_tag)
            if similarity is not None:
                found[(i, name)] = similarity
        return found

    best = None
    for threshold in np.arange(0.20, 0.96, 0.02):
        found = classify(float(threshold))
        true_positive = sum(1 for (i, name) in found if labels[i] == name)
        false_positive = len(found) - true_positive
        recall = true_positive / len(with_vectors)
        precision = true_positive / len(found) if found else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        if best is None or f1 > best[0]:
            best = (f1, float(threshold), precision, recall, false_positive)
    f1, threshold, precision, recall, false_positive = best
    print(f"best threshold {threshold:.2f}: the true label is listed for {recall:.0%} of images, "
          f"{precision:.0%} of listed labels are true ({false_positive} false listings)")

    # Fit the sharpness on labels listed at a threshold a little below the chosen one, so both
    # true and false labels are present around it.
    found = classify(threshold - 0.10)
    similarities = np.array(list(found.values()))
    truth = np.array([1.0 if labels[i] == name else 0.0 for (i, name) in found])
    sharpness = fit_sharpness(similarities, truth, threshold)
    print(f'agent config: "matchThreshold": {threshold:.2f}, "sharpness": {sharpness:.1f}, '
          f'"closestChunksPerTag": {args.closest_chunks_per_tag}')
    return 0


if __name__ == "__main__":
    sys.exit(main())
