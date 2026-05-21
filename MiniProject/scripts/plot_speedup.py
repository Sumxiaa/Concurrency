#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import sys
import tempfile
from pathlib import Path

cache_root = Path(tempfile.gettempdir()) / "mandelbrot-cache"
cache_root.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("XDG_CACHE_HOME", str(cache_root))
os.environ.setdefault("MPLCONFIGDIR", str(cache_root / "matplotlib"))

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

BENCHMARK_LINE_RE = re.compile(r"threads=(\d+)\s+\|\s+avg=([0-9.]+)\s+ms")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a speedup plot from Mandelbrot benchmark output."
    )
    parser.add_argument(
        "benchmark_file",
        nargs="?",
        help="Text file containing benchmark lines like: threads=4 | avg=637.85 ms | speedup=3.99x",
    )
    parser.add_argument(
        "--threads",
        help="Comma-separated thread counts, e.g. 1,2,4,8",
    )
    parser.add_argument(
        "--times",
        help="Comma-separated average times in milliseconds, e.g. 2542.87,1200.00,637.85,420.00",
    )
    parser.add_argument(
        "--output",
        default="figures/speedup.png",
        help="Output image path. Default: figures/speedup.png",
    )
    return parser.parse_args()


def parse_pairs_from_file(path: Path) -> list[tuple[int, float]]:
    pairs: list[tuple[int, float]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = BENCHMARK_LINE_RE.search(line)
        if match:
            pairs.append((int(match.group(1)), float(match.group(2))))
    return pairs


def parse_pairs_from_lists(thread_text: str, time_text: str) -> list[tuple[int, float]]:
    threads = [int(value.strip()) for value in thread_text.split(",") if value.strip()]
    times = [float(value.strip()) for value in time_text.split(",") if value.strip()]
    if len(threads) != len(times):
        raise ValueError("--threads and --times must contain the same number of values.")
    return list(zip(threads, times))


def normalise_pairs(pairs: list[tuple[int, float]]) -> list[tuple[int, float]]:
    if not pairs:
        raise ValueError("No benchmark data found.")
    normalised = sorted(pairs, key=lambda item: item[0])
    if normalised[0][0] <= 0 or normalised[0][1] <= 0.0:
        raise ValueError("Thread counts and times must be positive.")
    return normalised


def main() -> int:
    args = parse_arguments()

    if args.benchmark_file:
        pairs = parse_pairs_from_file(Path(args.benchmark_file))
    elif args.threads and args.times:
        pairs = parse_pairs_from_lists(args.threads, args.times)
    else:
        raise ValueError("Provide either a benchmark file or both --threads and --times.")

    pairs = normalise_pairs(pairs)
    threads = [thread for thread, _ in pairs]
    times_ms = [time_ms for _, time_ms in pairs]

    baseline_threads = threads[0]
    baseline_time = times_ms[0]
    speedups = [baseline_time / time_ms for time_ms in times_ms]
    ideal = [thread / baseline_threads for thread in threads]

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    plt.figure(figsize=(6, 4))
    plt.plot(threads, speedups, marker="o", linewidth=2, label="Measured speedup")
    plt.plot(threads, ideal, marker="o", linestyle="--", linewidth=1.5, label="Ideal linear speedup")
    plt.xlabel("Number of threads")
    plt.ylabel(f"Speedup over {baseline_threads} thread")
    plt.title("Parallel Mandelbrot Speedup")
    plt.xticks(threads)
    plt.grid(True, alpha=0.3)
    plt.legend()
    plt.tight_layout()
    plt.savefig(output_path, dpi=200)
    plt.close()

    print(f"Saved {output_path}")
    for thread, time_ms, speedup in zip(threads, times_ms, speedups):
        print(f"threads={thread} time_ms={time_ms:.2f} speedup={speedup:.2f}x")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # pragma: no cover - simple CLI error path
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)
