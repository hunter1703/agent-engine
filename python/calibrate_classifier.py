#!/usr/bin/env python3
"""Evaluates and calibrates an image classifier's embedder on a labelled image folder.

The folder holds one subfolder per label, each with that label's images. Every image is embedded by
the model server (`/v1/embeddings`), which may return several vectors for it, such as one per face.
Each image is then scored against every label with the rule `ImageClassificationAgent` applies: a
label's score is the mean, over its `top_sources` best-matching other images, of the best
similarity between a vector of the query image and a vector of that image; a label's probability
is a logistic function of its score. The probability is fitted so that a label scores high for
its own images and low for the others.

Usage:
    python calibrate_classifier.py DATASET --model arcface [--server http://localhost:8081]
        [--top-sources 3]

Prints recall of the true label among the top 1 and top 3 labels, and the `matchThreshold` and
`sharpness` to put in the classifier agent's config.
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


def label_scores(queries: np.ndarray, members: list[np.ndarray], top_sources: int) -> float:
    """Mean of the best similarities, one per member image, of the `top_sources` best images."""
    best = [float((queries @ m.T).max()) for m in members if len(m)]
    if not best:
        return -1.0
    return float(np.mean(sorted(best, reverse=True)[:top_sources]))


def fit_logistic(scores: np.ndarray, truth: np.ndarray) -> tuple[float, float]:
    """Fits probability = sigmoid(sharpness * (score - threshold)) by gradient descent."""
    a, b = 10.0, -4.0
    for _ in range(20000):
        p = 1.0 / (1.0 + np.exp(-(a * scores + b)))
        grad_a = np.mean((p - truth) * scores)
        grad_b = np.mean(p - truth)
        a -= 0.5 * grad_a
        b -= 0.5 * grad_b
    return a, -b / a


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--model", required=True, help="Embedding model name known to the server")
    parser.add_argument("--server", default="http://localhost:8081")
    parser.add_argument("--top-sources", type=int, default=3)
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

    top1 = top3 = 0
    all_scores: list[float] = []
    all_truth: list[float] = []
    for i in with_vectors:
        scores = {}
        for name in names:
            members = [vectors[j] for j in with_vectors if j != i and labels[j] == name]
            scores[name] = label_scores(vectors[i], members, args.top_sources)
        ranked = sorted(names, key=scores.get, reverse=True)
        top1 += ranked[0] == labels[i]
        top3 += labels[i] in ranked[:3]
        for name in names:
            all_scores.append(scores[name])
            all_truth.append(1.0 if name == labels[i] else 0.0)

    n = len(with_vectors)
    print(f"leave-one-out: true label top-1 {top1 / n:.2f}, top-3 {top3 / n:.2f}")
    sharpness, threshold = fit_logistic(np.array(all_scores), np.array(all_truth))
    print(f'agent config: "matchThreshold": {threshold:.2f}, "sharpness": {sharpness:.1f}, '
          f'"topSources": {args.top_sources}')
    return 0


if __name__ == "__main__":
    sys.exit(main())
