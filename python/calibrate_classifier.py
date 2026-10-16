#!/usr/bin/env python3
"""Calibrates an image classifier's `matchThreshold` and `sharpness` on the boards in training.zip.

The archive holds one folder per board, the label, each with its images, directly or in nested
folders; it is read in memory and never extracted. Every image is embedded by the model server (`/v1/embeddings`), which may return several
vectors for it, such as one per face. Each image is then classified against all the others
(leave-one-out) the way `ImageClassificationAgent` does: for each vector found in it, the vectors
of the other images at least `matchThreshold` similar are grouped by label, a label's similarity is
the mean of its `closest_chunks_per_tag` closest, and the best over the image's vectors is kept. A
label with no such vector is not listed.

The threshold is the highest one at which the true label is still among an image's three
most similar listed labels almost as often (`--keep`) as with the lowest threshold, so the labels
listed are as few as that allows. The sharpness is fitted so that a listed label's probability, a
logistic function of its similarity above the threshold, matches how often it is the true label.

Usage:
    python calibrate_classifier.py --model arcface [--server http://localhost:8081]
        [--closest-chunks-per-tag 3] [--workers 4] [--keep 0.97] [--cache DIR]

Prints only aggregate figures: how often the true label is first and in the top three at a range of
thresholds, and the values to put in the agent's config. With --cache, the embeddings are kept in
DIR (vectors only, no names) and reused by the next run on the same archive.
"""

from __future__ import annotations

import argparse
import base64
import sys
import warnings
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
import requests

from training_data import TrainingArchive
BATCH_SIZE = 8
THRESHOLDS = np.arange(0.20, 0.96, 0.02)


def embed_images(
    server: str, model: str, archive: TrainingArchive, entries: list[str], workers: int
) -> list[np.ndarray]:
    """For each image, an array of the vectors the server found in it (possibly none)."""

    def embed_batch(start: int) -> list[list[list[float]]]:
        batch = entries[start : start + BATCH_SIZE]
        body = {
            "model": model,
            "input": [base64.b64encode(archive.read(entry)).decode("ascii") for entry in batch],
        }
        response = requests.post(f"{server}/v1/embeddings", json=body, timeout=600)
        response.raise_for_status()
        vectors: list[list[list[float]]] = [[] for _ in batch]
        for entry in response.json()["data"]:
            vectors[entry["index"]].append(entry["embedding"])
        return vectors

    with ThreadPoolExecutor(max_workers=workers) as pool:
        batches = list(pool.map(embed_batch, range(0, len(entries), BATCH_SIZE)))
    return [
        np.asarray(vectors, dtype=np.float32).reshape(-1, 768)
        for batch in batches
        for vectors in batch
    ]


def closest_per_label(
    vectors: list[np.ndarray], labels: list[int], label_count: int, closest: int
) -> dict[int, np.ndarray]:
    """For each image with vectors, an array (its vectors, labels, `closest`) of the similarities of
    the label's closest vectors among the other images' vectors, closest first, padded with -inf."""
    owners = np.concatenate([np.full(len(v), i) for i, v in enumerate(vectors)])
    all_vectors = np.concatenate(vectors)
    by_label = np.argsort(np.array(labels)[owners], kind="stable")
    all_vectors, owners = all_vectors[by_label], owners[by_label]
    label_of_vector = np.array(labels)[owners]
    bounds = np.searchsorted(label_of_vector, np.arange(label_count + 1))

    result: dict[int, np.ndarray] = {}
    for i, own in enumerate(vectors):
        if not len(own):
            continue
        similarities = own @ all_vectors.T
        similarities[:, owners == i] = -np.inf
        top = np.full((len(own), label_count, closest), -np.inf, dtype=np.float32)
        for label in range(label_count):
            part = similarities[:, bounds[label] : bounds[label + 1]]
            take = min(closest, part.shape[1])
            if take:
                top[:, label, :take] = -np.sort(-np.partition(part, -take, axis=1)[:, -take:], axis=1)
        result[i] = top
    return result


def label_similarities(top: np.ndarray, threshold: float) -> np.ndarray:
    """Per label, the mean of the closest similarities at least `threshold`, the best over the
    image's vectors; NaN for a label with none."""
    close = top >= threshold
    count = close.sum(axis=2)
    mean = np.where(count > 0, np.where(close, top, 0.0).sum(axis=2) / np.maximum(count, 1), np.nan)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", RuntimeWarning)  # a label with no vector at all
        return np.nanmax(mean, axis=0)


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
    parser.add_argument("--model", required=True, help="Embedding model name known to the server")
    parser.add_argument("--server", default="http://localhost:8081")
    parser.add_argument("--closest-chunks-per-tag", type=int, default=3)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--keep", type=float, default=0.97,
                        help="Share of the loosest threshold's top-3 hits the chosen one must keep")
    parser.add_argument("--cache", type=Path, help="Folder to keep the embeddings in between runs")
    args = parser.parse_args()

    archive = TrainingArchive()
    names: list[str] = []
    labels: list[int] = []
    paths: list[str] = []
    for board, images in sorted(archive.board_vs_images().items()):
        names.append(board)
        labels += [len(names) - 1] * len(images)
        paths += images
    if len(names) < 2:
        print("Need at least two boards", file=sys.stderr)
        return 1

    cache_file = args.cache / f"{args.model}-{len(paths)}.npz" if args.cache else None
    if cache_file and cache_file.exists():
        stored = np.load(cache_file)
        vectors = np.split(stored["vectors"], stored["ends"][:-1])
    else:
        vectors = embed_images(args.server, args.model, archive, paths, args.workers)
        if cache_file:
            args.cache.mkdir(parents=True, exist_ok=True)
            np.savez(cache_file, vectors=np.concatenate(vectors),
                     ends=np.cumsum([len(v) for v in vectors]))
    with_vectors = [i for i, v in enumerate(vectors) if len(v)]
    print(f"{len(paths)} images, {len(names)} labels, {len(with_vectors)} with at least one vector, "
          f"{sum(len(v) for v in vectors)} vectors", flush=True)

    top = closest_per_label(vectors, labels, len(names), args.closest_chunks_per_tag)

    def classify(threshold: float) -> dict[int, np.ndarray]:
        return {i: label_similarities(top[i], threshold) for i in with_vectors}

    rows = []
    for threshold in THRESHOLDS:
        found = classify(float(threshold))
        first = top3 = listed = 0
        for i, similarity in found.items():
            present = ~np.isnan(similarity)
            listed += int(present.sum())
            if present[labels[i]]:
                rank = int((similarity[present] > similarity[labels[i]]).sum())
                first += rank == 0
                top3 += rank < 3
        n = len(with_vectors)
        rows.append((float(threshold), first / n, top3 / n, listed / n))
    print("threshold  true label first  in top 3  labels listed per image")
    for threshold, first, top3, per_image in rows[::2]:
        print(f"   {threshold:.2f}        {first:7.0%}      {top3:7.0%}      {per_image:8.1f}")
    best_top3 = rows[0][2]
    threshold, first, top3, per_image = max(
        (row for row in rows if row[2] >= args.keep * best_top3), key=lambda row: row[0])
    print(f"chosen threshold {threshold:.2f}: true label first for {first:.0%}, in the top 3 for "
          f"{top3:.0%} (loosest: {best_top3:.0%}), {per_image:.1f} labels listed per image")

    # Fit the sharpness on labels listed a little below the chosen threshold, so both true and false
    # labels are present around it.
    wide = classify(threshold - 0.10)
    similarities = np.concatenate([s[~np.isnan(s)] for s in wide.values()])
    truth = np.concatenate([
        (np.arange(len(names))[~np.isnan(s)] == labels[i]).astype(float) for i, s in wide.items()
    ])
    sharpness = fit_sharpness(similarities, truth, threshold)
    print(f'agent config: "matchThreshold": {threshold:.2f}, "sharpness": {sharpness:.1f}, '
          f'"closestChunksPerTag": {args.closest_chunks_per_tag}')
    return 0


if __name__ == "__main__":
    sys.exit(main())
