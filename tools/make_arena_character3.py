#!/usr/bin/env python3
"""Paint a third original RigStudio character sheet: "Tuni" (chibi kid, cat-ear hoodie).

    python3 tools/make_arena_character3.py [--out docs/assets/arena-character-3-sheet.png] [--preview out.png]

Same contract as the other sheet painters: all 60 template slots filled with flat original
artwork in normalised slot coordinates — imports straight into RigStudio
(2048x2048 RGBA, zero stray ink, riggable, 4 views, 5 expressions, 11 mouths).

Style brief (the "cartoon-app look", distinct from sample / Mimi / Rafi):
  * chibi feel — big head artwork, compact hoodie body
  * BOLD dark outlines on every major shape (drawn as an oversized ink silhouette behind
    each fill; the pipeline trims it with the part, so nothing bleeds across slots)
  * peach cat-ear hoodie with inner-ear accents and a front pocket
  * huge glossy eyes with double highlights, dark slate leggings, pink sneakers
"""

from __future__ import annotations

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import png_rw  # noqa: E402
from make_sample_character import Painter  # noqa: E402
from make_arena_character import diamond, render_preview, trapezoid  # noqa: E402

# --- palette -------------------------------------------------------------------------------
INK = (48, 38, 52)
SKIN = (252, 214, 178)
SKIN_SHADE = (228, 186, 148)
HOODIE = (255, 142, 86)
HOODIE_DARK = (222, 108, 60)
HOODIE_LIGHT = (255, 176, 128)
EAR_INNER = (255, 205, 160)
POCKET = (255, 176, 128)
PANTS = (74, 70, 92)
PANTS_DARK = (56, 52, 72)
SHOE = (255, 118, 158)
SHOE_ACCENT = (252, 250, 246)
SOLE = (255, 214, 196)
EYE_WHITE = (250, 250, 248)
IRIS = (62, 176, 168)
IRIS_DEEP = (34, 120, 122)
MOUTH_INK = (150, 62, 74)
TEETH = (246, 244, 240)
TONGUE = (214, 100, 104)
BLUSH = (255, 168, 160)

OUTLINE = 0.028  # silhouette expansion for the bold-outline look


# --- outlined-shape helpers ----------------------------------------------------------------
def o_ellipse(p: Painter, cx: float, cy: float, rx: float, ry: float, color: tuple) -> None:
    p.fill(p.ellipse(cx, cy, rx + OUTLINE, ry + OUTLINE), INK)
    p.fill(p.ellipse(cx, cy, rx, ry), color)


def o_capsule(p: Painter, x0: float, y0: float, x1: float, y1: float, r: float, color: tuple) -> None:
    p.fill(p.capsule(x0, y0, x1, y1, r + OUTLINE), INK)
    p.fill(p.capsule(x0, y0, x1, y1, r), color)


def o_rect(p: Painter, x0: float, y0: float, x1: float, y1: float, color: tuple) -> None:
    p.fill(p.rect(x0 - OUTLINE, y0 - OUTLINE, x1 + OUTLINE, y1 + OUTLINE), INK)
    p.fill(p.rect(x0, y0, x1, y1), color)


def o_trapezoid(p: Painter, y0: float, y1: float, h0: float, h1: float, color: tuple) -> None:
    p.fill(trapezoid(y0 - OUTLINE, y1 + OUTLINE, h0 + OUTLINE, h1 + OUTLINE), INK)
    p.fill(trapezoid(y0, y1, h0, h1), color)


def o_diamond(p: Painter, cx: float, cy: float, rx: float, ry: float, color: tuple) -> None:
    p.fill(diamond(cx, cy, rx + OUTLINE, ry + OUTLINE), INK)
    p.fill(diamond(cx, cy, rx, ry), color)


# --- body parts ----------------------------------------------------------------------------
def ears(p: Painter) -> None:
    """Cat ears on top of the hood — triangle-ish diamonds with an inner accent."""
    for sx in (-1.0, 1.0):
        cx = 0.5 + sx * 0.235
        o_diamond(p, cx, 0.135, 0.105, 0.135, HOODIE)
        p.fill(diamond(cx + sx * 0.012, 0.148, 0.052, 0.075), EAR_INNER)


def front_head(p: Painter) -> None:
    ears(p)
    # hood rim behind the face, then the big chibi skull
    o_ellipse(p, 0.5, 0.50, 0.435, 0.445, HOODIE)
    o_ellipse(p, 0.5, 0.55, 0.375, 0.395, SKIN)
    # hood edges hugging the cheeks
    p.fill(p.union(
        p.difference(p.ellipse(0.5, 0.50, 0.44, 0.45), p.ellipse(0.5, 0.55, 0.40, 0.42)),
    ), HOODIE_DARK)
    # fringe peeking from under the hood
    p.fill(p.union(
        diamond(0.40, 0.275, 0.055, 0.045),
        diamond(0.55, 0.262, 0.060, 0.050),
        diamond(0.66, 0.29, 0.045, 0.038),
    ), (86, 62, 74))
    # hood drawstrings
    p.fill(p.capsule(0.36, 0.46, 0.345, 0.60, 0.016), INK)
    p.fill(p.capsule(0.64, 0.46, 0.655, 0.60, 0.016), INK)
    p.fill(p.ellipse(0.345, 0.615, 0.026, 0.026), HOODIE_DARK)
    p.fill(p.ellipse(0.655, 0.615, 0.026, 0.026), HOODIE_DARK)
    # blush
    p.fill(p.ellipse(0.315, 0.71, 0.058, 0.036), BLUSH)
    p.fill(p.ellipse(0.685, 0.71, 0.058, 0.036), BLUSH)


def front_torso(p: Painter) -> None:
    # compact hoodie body: rounded shoulders, wide hem, front pocket
    o_capsule(p, 0.34, 0.17, 0.66, 0.17, 0.135, HOODIE)
    o_rect(p, 0.325, 0.16, 0.675, 0.62, HOODIE)
    o_trapezoid(p, 0.60, 0.93, 0.185, 0.30, PANTS)
    p.fill(p.rect(0.325, 0.60, 0.675, 0.66), HOODIE_DARK)
    # pocket + zipper hint
    p.fill(p.rect(0.40, 0.42, 0.60, 0.56), POCKET)
    p.fill(p.rect(0.40, 0.42, 0.60, 0.455), HOODIE_LIGHT)
    p.fill(p.rect(0.4925, 0.16, 0.5075, 0.60), HOODIE_DARK)
    o_ellipse(p, 0.5, 0.10, 0.11, 0.045, HOODIE_DARK)


def sleeve_arm(p: Painter) -> None:
    o_capsule(p, 0.5, 0.16, 0.5, 0.55, 0.175, HOODIE)
    o_capsule(p, 0.5, 0.50, 0.5, 0.92, 0.135, SKIN)
    p.fill(p.rect(0.30, 0.485, 0.70, 0.535), HOODIE_DARK)


def forearm_arm(p: Painter) -> None:
    o_capsule(p, 0.5, 0.08, 0.5, 0.92, 0.135, SKIN)


def hand(p: Painter) -> None:
    o_ellipse(p, 0.5, 0.55, 0.31, 0.37, SKIN)


def thigh_pants(p: Painter) -> None:
    o_capsule(p, 0.5, 0.06, 0.5, 0.62, 0.325, PANTS)
    o_capsule(p, 0.5, 0.58, 0.5, 0.95, 0.21, SKIN)


def shin_sock(p: Painter) -> None:
    o_capsule(p, 0.5, 0.06, 0.5, 0.72, 0.285, SKIN)
    o_capsule(p, 0.5, 0.66, 0.5, 0.94, 0.285, SHOE_ACCENT)
    p.fill(p.rect(0.27, 0.72, 0.73, 0.775), SHOE)


def sneaker(p: Painter, facing: int = 0) -> None:
    o_rect(p, 0.24, 0.28, 0.74, 0.66, SHOE)
    o_ellipse(p, 0.58 + 0.08 * facing, 0.61, 0.245, 0.155, SHOE)
    p.fill(p.ellipse(0.60 + 0.08 * facing, 0.625, 0.14, 0.085), SHOE_ACCENT)
    o_rect(p, 0.16, 0.63, 0.88, 0.76, SOLE)
    p.fill(p.capsule(0.30 + 0.04 * facing, 0.42, 0.66 + 0.04 * facing, 0.36, 0.028), SHOE_ACCENT)


def side_head(p: Painter, facing_left: bool) -> None:
    d = -1 if facing_left else 1
    # one visible ear, slightly rotated feel by shifting it back
    o_diamond(p, 0.5 - 0.16 * d, 0.145, 0.095, 0.125, HOODIE)
    p.fill(diamond(0.5 - 0.148 * d, 0.158, 0.047, 0.070), EAR_INNER)
    o_ellipse(p, 0.5 - 0.02 * d, 0.50, 0.40, 0.42, HOODIE)
    o_ellipse(p, 0.5 + 0.035 * d, 0.55, 0.335, 0.365, SKIN)
    p.fill(p.ellipse(0.5 + 0.34 * d, 0.65, 0.07, 0.05), SKIN)
    p.fill(p.difference(p.ellipse(0.5 - 0.02 * d, 0.50, 0.405, 0.425), p.ellipse(0.5 + 0.035 * d, 0.55, 0.36, 0.39)), HOODIE_DARK)
    p.fill(diamond(0.5 + 0.10 * d, 0.275, 0.055, 0.045), (86, 62, 74))
    p.fill(p.capsule(0.5 - 0.16 * d, 0.50, 0.5 - 0.19 * d, 0.62, 0.015), INK)
    p.fill(p.ellipse(0.5 - 0.19 * d, 0.635, 0.024, 0.024), HOODIE_DARK)


def side_torso(p: Painter) -> None:
    o_capsule(p, 0.43, 0.15, 0.57, 0.15, 0.115, HOODIE)
    o_rect(p, 0.36, 0.14, 0.64, 0.58, HOODIE)
    p.fill(p.rect(0.36, 0.575, 0.64, 0.63), HOODIE_DARK)
    o_trapezoid(p, 0.58, 0.92, 0.155, 0.27, PANTS)


def back_head(p: Painter) -> None:
    # hood seen from behind: two ears + big hood shape, no face
    o_diamond(p, 0.285, 0.14, 0.10, 0.13, HOODIE)
    p.fill(diamond(0.295, 0.155, 0.048, 0.072), EAR_INNER)
    o_diamond(p, 0.715, 0.14, 0.10, 0.13, HOODIE)
    p.fill(diamond(0.705, 0.155, 0.048, 0.072), EAR_INNER)
    o_ellipse(p, 0.5, 0.52, 0.425, 0.44, HOODIE)
    p.fill(p.union(p.capsule(0.40, 0.30, 0.40, 0.82, 0.024),
                   p.capsule(0.50, 0.28, 0.50, 0.84, 0.024),
                   p.capsule(0.60, 0.30, 0.60, 0.82, 0.024)), HOODIE_DARK)
    o_ellipse(p, 0.5, 0.90, 0.115, 0.07, SKIN_SHADE)


def back_torso(p: Painter) -> None:
    o_capsule(p, 0.34, 0.16, 0.66, 0.16, 0.13, HOODIE)
    o_rect(p, 0.33, 0.15, 0.67, 0.62, HOODIE)
    p.fill(p.rect(0.33, 0.60, 0.67, 0.655), HOODIE_DARK)
    o_trapezoid(p, 0.60, 0.93, 0.18, 0.295, PANTS)


# --- face ----------------------------------------------------------------------------------
def _giant_eye(p: Painter, cx: float, iris_dy: float = 0.045, brow=None, ry: float = 0.215) -> None:
    """The chibi look: oversized glossy iris, double catchlight."""
    p.fill(p.ellipse(cx, 0.52, 0.155, ry), EYE_WHITE)
    p.fill(p.ellipse(cx, 0.52 + iris_dy, 0.112, 0.145 * (ry / 0.215)), IRIS)
    p.fill(p.ellipse(cx, 0.545 + iris_dy, 0.058, 0.082 * (ry / 0.215)), IRIS_DEEP)
    p.fill(p.ellipse(cx + 0.01, 0.562 + iris_dy, 0.026, 0.036), INK)
    # double highlight = glossy
    p.fill(p.ellipse(cx - 0.038, 0.44 + iris_dy, 0.032, 0.044), EYE_WHITE)
    p.fill(p.ellipse(cx + 0.045, 0.60 + iris_dy, 0.018, 0.024), EYE_WHITE)
    # soft straight brow
    p.fill(p.capsule(cx - 0.115, 0.30, cx + 0.105, 0.292, 0.030), INK)
    if brow:
        x0, y0, x1, y1 = brow
        p.fill(p.capsule(cx + x0, y0, cx + x1, y1, 0.030), INK)


EYES = {
    "NEUTRAL": lambda p: (_giant_eye(p, 0.295), _giant_eye(p, 0.705)),
    "CLOSED": lambda p: p.fill(p.union(
        p.ring(0.295, 0.50, 0.145, 0.125, 0.16, upper=False),
        p.ring(0.705, 0.50, 0.145, 0.125, 0.16, upper=False)), INK),
    "HAPPY": lambda p: p.fill(p.union(
        p.ring(0.295, 0.585, 0.15, 0.16, 0.15, upper=True),
        p.ring(0.705, 0.585, 0.15, 0.16, 0.15, upper=True)), INK),
    "SAD": lambda p: (
        _giant_eye(p, 0.295, iris_dy=0.08, brow=(-0.105, 0.285, 0.075, 0.205)),
        _giant_eye(p, 0.705, iris_dy=0.08, brow=(-0.075, 0.205, 0.105, 0.285)),
    ),
    "ANGRY": lambda p: (
        _giant_eye(p, 0.295, ry=0.15, brow=(-0.105, 0.205, 0.085, 0.375)),
        _giant_eye(p, 0.705, ry=0.15, brow=(-0.085, 0.375, 0.105, 0.205)),
    ),
}


def _teeth(p: Painter, x0: float, x1: float, y0: float, y1: float) -> None:
    p.fill(p.rect(x0, y0, x1, y1), TEETH)


MOUTHS = {
    "NORMAL": lambda p: p.fill(p.capsule(0.38, 0.52, 0.62, 0.52, 0.046), MOUTH_INK),
    "CLOSED": lambda p: p.fill(p.capsule(0.43, 0.52, 0.57, 0.52, 0.028), MOUTH_INK),
    "A": lambda p: (
        p.fill(p.ellipse(0.5, 0.55, 0.185, 0.265), MOUTH_INK),
        _teeth(p, 0.375, 0.625, 0.335, 0.45),
        p.fill(p.ellipse(0.5, 0.74, 0.10, 0.062), TONGUE),
    ),
    "E": lambda p: (
        p.fill(p.ellipse(0.5, 0.475, 0.245, 0.155), MOUTH_INK),
        _teeth(p, 0.315, 0.685, 0.335, 0.425),
    ),
    "I": lambda p: p.fill(p.capsule(0.31, 0.52, 0.69, 0.52, 0.05), MOUTH_INK),
    "O": lambda p: p.fill(p.ellipse(0.5, 0.53, 0.135, 0.195), MOUTH_INK),
    "U": lambda p: p.fill(p.ellipse(0.5, 0.55, 0.09, 0.12), MOUTH_INK),
    "SMILE": lambda p: (
        p.fill(p.difference(p.ellipse(0.5, 0.42, 0.28, 0.245), p.rect(0.0, 0.0, 1.0, 0.42)), MOUTH_INK),
        _teeth(p, 0.315, 0.685, 0.42, 0.505),
    ),
    "SAD": lambda p: p.fill(
        p.difference(p.ellipse(0.5, 0.68, 0.245, 0.21), p.rect(0.0, 0.68, 1.0, 1.0)), MOUTH_INK),
    "SURPRISED": lambda p: (
        p.fill(p.ellipse(0.5, 0.52, 0.115, 0.175), MOUTH_INK),
        p.fill(p.ellipse(0.5, 0.565, 0.052, 0.09), TONGUE),
    ),
    "ANGRY": lambda p: (
        p.fill(p.rect(0.30, 0.40, 0.70, 0.645), MOUTH_INK),
        _teeth(p, 0.335, 0.665, 0.435, 0.615),
        p.fill(p.union(p.rect(0.409, 0.435, 0.421, 0.615),
                       p.rect(0.494, 0.435, 0.506, 0.615),
                       p.rect(0.579, 0.435, 0.591, 0.615)), MOUTH_INK),
    ),
}


# --- dispatch ------------------------------------------------------------------------------
def paint_slot(painter: Painter, slot: dict) -> None:
    kind = slot["kind"]
    if kind == "EYE":
        EYES[slot["expression"]](painter)
        return
    if kind == "MOUTH":
        MOUTHS[slot["mouthShape"]](painter)
        return

    view = slot["view"]
    part = slot["id"]
    for prefix in ("side_left_", "side_right_", "front_", "back_"):
        if part.startswith(prefix):
            part = part[len(prefix):]
            break

    if view == "FRONT":
        if part == "head":
            front_head(painter)
        elif part == "torso":
            front_torso(painter)
        elif part in ("upper_arm_l", "upper_arm_r", "forearm_l", "forearm_r"):
            (sleeve_arm if part.startswith("upper") else forearm_arm)(painter)
        elif part in ("hand_l", "hand_r"):
            hand(painter)
        elif part in ("thigh_l", "thigh_r"):
            thigh_pants(painter)
        elif part in ("shin_l", "shin_r"):
            shin_sock(painter)
        else:
            sneaker(painter)
    elif view == "BACK":
        if part == "head":
            back_head(painter)
        elif part == "torso":
            back_torso(painter)
        elif part in ("upper_arm_l", "upper_arm_r", "forearm_l", "forearm_r"):
            (sleeve_arm if part.startswith("upper") else forearm_arm)(painter)
        elif part in ("hand_l", "hand_r"):
            hand(painter)
        elif part in ("thigh_l", "thigh_r"):
            thigh_pants(painter)
        elif part in ("shin_l", "shin_r"):
            shin_sock(painter)
        else:
            sneaker(painter)
    else:
        facing_left = view == "SIDE_LEFT"
        if part == "head":
            side_head(painter, facing_left)
        elif part == "torso":
            side_torso(painter)
        elif part in ("upper_arm", "forearm"):
            (sleeve_arm if part == "upper_arm" else forearm_arm)(painter)
        elif part == "hand":
            hand(painter)
        elif part == "thigh":
            thigh_pants(painter)
        elif part == "shin":
            shin_sock(painter)
        else:
            sneaker(painter, -1 if facing_left else 1)


def main(argv: list[str] | None = None) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description='Paint the "Tuni" chibi character sheet.')
    parser.add_argument("--slots", default=os.path.join(here, "slots.json"))
    parser.add_argument("--out", default=os.path.join(here, "..", "docs", "assets", "arena-character-3-sheet.png"))
    parser.add_argument("--preview", default=None, help="also render a rough assembled preview PNG")
    args = parser.parse_args(argv)

    with open(args.slots, "r", encoding="utf-8") as handle:
        template = json.load(handle)

    image = png_rw.Image.blank(template["sheetWidth"], template["sheetHeight"])
    for slot in template["slots"]:
        paint_slot(Painter(image, slot), slot)

    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    size = png_rw.write(image, out)
    print(f"tuni character: {len(template['slots'])} slots painted -> {out} ({size} bytes)")

    if args.preview:
        render_preview(image, template, os.path.abspath(args.preview))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
