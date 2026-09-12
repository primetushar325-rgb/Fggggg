#!/usr/bin/env python3
"""Generate a serialised set of 100 RigStudio character-sheet test images.

The images use the same deterministic generator as ``make_test_sheet.py`` and the
same 2048x2048 template, so every file can be imported and validated by the app.

Usage:
    python3 tools/generate_100_images.py
    python3 tools/generate_100_images.py --out docs/assets/generated-100 --which full
"""
from __future__ import annotations

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_test_sheet  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate 100 serialised character-sheet PNGs")
    parser.add_argument("--out", default=os.path.join(HERE, "..", "docs", "assets", "generated-100"))
    parser.add_argument("--which", choices=["front", "front-side", "full"], default="front-side")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    for number in range(1, 101):
        path = os.path.join(args.out, f"image-{number:03d}.png")
        make_test_sheet.main([
            "--which", args.which,
            "--seed", str(number - 1),
            "--out", path,
        ])
    print(f"generated 100 images in serial order: {os.path.abspath(args.out)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
