#!/usr/bin/env python3
"""Paint a second original RigStudio character sheet: "Rafi" (sporty boy, spiky hair).

    python3 tools/make_arena_character2.py [--out docs/assets/arena-character-2-sheet.png] [--preview out.png]

Same contract as the other sheet painters: all 60 template slots filled with flat original
artwork in normalised slot coordinates, so the PNG imports straight into RigStudio
(2048x2048 RGBA, zero stray ink, riggable with all four views, 5 expressions, 11 mouths).

Design brief (distinct from the bundled sample and from "Mimi"):
  * short spiky dark-navy hair with a highlight and jagged fringe
  * mustard t-shirt with a white chest stripe, olive shorts with a waistband
  * bare arms with white wristbands, white socks with a red stripe, red sneakers
  * bolder straight eyebrows; rounder "open" vowel mouths
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
SKIN = (240, 198, 158)
SKIN_SHADE = (214, 172, 134)
HAIR = (43, 46, 74)
HAIR_LIGHT = (78, 84, 124)
SHIRT = (244, 180, 44)
SHIRT_DARK = (208, 146, 28)
STRIPE = (250, 248, 242)
SHORTS = (88, 100, 62)
SHORTS_DARK = (68, 78, 48)
SOCK = (250, 250, 246)
SOCK_STRIPE = (214, 60, 54)
SHOE = (214, 60, 54)
SHOE_ACCENT = (250, 250, 246)
SOLE = (42, 44, 60)
EYE_WHITE = (248, 248, 246)
IRIS = (52, 60, 96)
INK = (44, 40, 48)
MOUTH_INK = (146, 58, 54)
TEETH = (244, 242, 238)
TONGUE = (206, 96, 92)


# --- body parts ----------------------------------------------------------------------------
def front_head(p: Painter) -> None:
    # ears first (behind the skull), then skull, then the spiky hair cap.
    p.fill(p.union(p.ellipse(0.135, 0.60, 0.048, 0.075), p.ellipse(0.865, 0.60, 0.048, 0.075)), SKIN_SHADE)
    p.fill(p.ellipse(0.5, 0.56, 0.37, 0.40), SKIN)
    cap = p.difference(p.ellipse(0.5, 0.42, 0.405, 0.375), p.rect(0.0, 0.415, 1.0, 1.0))
    p.fill(cap, HAIR)
    # Jagged fringe hanging onto the forehead.
    p.fill(p.union(
        diamond(0.28, 0.44, 0.055, 0.055),
        diamond(0.50, 0.475, 0.065, 0.065),
        diamond(0.72, 0.44, 0.055, 0.055),
    ), HAIR)
    # Spikes standing up along the top edge.
    p.fill(p.union(
        diamond(0.27, 0.145, 0.052, 0.105),
        diamond(0.415, 0.105, 0.058, 0.130),
        diamond(0.585, 0.105, 0.058, 0.130),
        diamond(0.73, 0.145, 0.052, 0.105),
    ), HAIR)
    p.fill(p.ellipse(0.42, 0.30, 0.105, 0.034), HAIR_LIGHT)
    # Short sideburns.
    p.fill(p.union(p.rect(0.105, 0.44, 0.155, 0.56), p.rect(0.845, 0.44, 0.895, 0.56)), HAIR)


def tee_torso(p: Painter) -> None:
    # Mustard tee over the chest; olive shorts take over at the waist.
    p.fill(p.union(
        p.capsule(0.34, 0.16, 0.66, 0.16, 0.13),
        p.rect(0.34, 0.14, 0.66, 0.60),
    ), SHIRT)
    p.fill(p.rect(0.34, 0.29, 0.66, 0.40), STRIPE)
    p.fill(p.rect(0.335, 0.585, 0.665, 0.615), SHIRT_DARK)
    p.fill(trapezoid(0.60, 0.94, 0.170, 0.300), SHORTS)
    p.fill(p.rect(0.30, 0.615, 0.70, 0.685), SHORTS_DARK)
    p.fill(p.ellipse(0.5, 0.105, 0.115, 0.045), SHIRT_DARK)


def sleeve_arm(p: Painter) -> None:
    p.fill(p.capsule(0.5, 0.18, 0.5, 0.92, 0.16), SKIN)
    p.fill(p.ellipse(0.5, 0.17, 0.40, 0.165), SHIRT)
    p.fill(p.rect(0.20, 0.29, 0.80, 0.325), SHIRT_DARK)


def forearm_band(p: Painter) -> None:
    p.fill(p.capsule(0.5, 0.10, 0.5, 0.90, 0.16), SKIN)
    p.fill(p.rect(0.29, 0.78, 0.71, 0.885), STRIPE)


def hand(p: Painter) -> None:
    p.fill(p.union(p.ellipse(0.5, 0.55, 0.32, 0.38), p.ellipse(0.315, 0.50, 0.105, 0.15)), SKIN)


def thigh_shorts(p: Painter) -> None:
    p.fill(p.capsule(0.5, 0.06, 0.5, 0.80, 0.34), SHORTS)
    p.fill(p.rect(0.22, 0.74, 0.78, 0.845), SHORTS_DARK)
    p.fill(p.capsule(0.5, 0.80, 0.5, 0.95, 0.205), SKIN)


def shin_sock(p: Painter) -> None:
    leg = p.capsule(0.5, 0.06, 0.5, 0.94, 0.30)
    p.fill(leg, SKIN)
    p.fill(lambda x, y: leg(x, y) and y >= 0.42, SOCK)
    p.fill(p.rect(0.28, 0.47, 0.72, 0.545), SOCK_STRIPE)


def sneaker(p: Painter, facing: int = 0) -> None:
    body = p.union(p.rect(0.22, 0.28, 0.76, 0.64),
                   p.ellipse(0.60 + 0.09 * facing, 0.60, 0.26, 0.17),
                   p.ellipse(0.30 - 0.03 * facing, 0.52, 0.11, 0.16))
    p.fill(body, SHOE)
    p.fill(p.ellipse(0.62 + 0.09 * facing, 0.615, 0.155, 0.095), SHOE_ACCENT)
    p.fill(p.rect(0.14, 0.64, 0.90, 0.78), SOLE)
    p.fill(p.capsule(0.28 + 0.04 * facing, 0.43, 0.68 + 0.04 * facing, 0.365, 0.030), SHOE_ACCENT)


def side_head(p: Painter, facing_left: bool) -> None:
    d = -1 if facing_left else 1
    p.fill(p.ellipse(0.5 + 0.02 * d, 0.56, 0.335, 0.385), SKIN)
    p.fill(p.ellipse(0.5 + 0.335 * d, 0.645, 0.075, 0.055), SKIN)
    cap = p.difference(p.ellipse(0.5 + 0.05 * d, 0.42, 0.365, 0.35), p.rect(0.0, 0.415, 1.0, 1.0))
    p.fill(cap, HAIR)
    p.fill(p.union(
        diamond(0.5 + 0.24 * d, 0.15, 0.05, 0.10),
        diamond(0.5 + 0.02 * d, 0.10, 0.055, 0.125),
        diamond(0.5 - 0.20 * d, 0.15, 0.05, 0.10),
    ), HAIR)
    p.fill(diamond(0.5 + 0.30 * d, 0.46, 0.05, 0.05), HAIR)
    p.fill(p.ellipse(0.5 + 0.02 * d, 0.29, 0.09, 0.032), HAIR_LIGHT)
    p.fill(p.capsule(0.5 - 0.30 * d, 0.44, 0.5 - 0.325 * d, 0.60, 0.05), HAIR)


def side_torso(p: Painter) -> None:
    p.fill(p.union(
        p.capsule(0.42, 0.14, 0.58, 0.14, 0.115),
        p.rect(0.37, 0.12, 0.63, 0.58),
    ), SHIRT)
    p.fill(p.rect(0.37, 0.28, 0.63, 0.375), STRIPE)
    p.fill(trapezoid(0.58, 0.92, 0.145, 0.26), SHORTS)
    p.fill(p.rect(0.355, 0.585, 0.645, 0.65), SHORTS_DARK)


def back_head(p: Painter) -> None:
    p.fill(p.ellipse(0.5, 0.52, 0.40, 0.44), HAIR)
    p.fill(p.union(
        diamond(0.29, 0.13, 0.052, 0.105),
        diamond(0.42, 0.095, 0.058, 0.125),
        diamond(0.58, 0.095, 0.058, 0.125),
        diamond(0.71, 0.13, 0.052, 0.105),
    ), HAIR)
    for fx in (0.40, 0.50, 0.60):
        p.fill(p.capsule(fx, 0.30, fx, 0.86, 0.026), HAIR_LIGHT)
    p.fill(p.ellipse(0.5, 0.905, 0.115, 0.075), SKIN_SHADE)


def back_torso(p: Painter) -> None:
    p.fill(p.union(
        p.capsule(0.34, 0.16, 0.66, 0.16, 0.125),
        p.rect(0.345, 0.14, 0.655, 0.60),
    ), SHIRT)
    p.fill(p.rect(0.345, 0.585, 0.655, 0.615), SHIRT_DARK)
    p.fill(trapezoid(0.60, 0.94, 0.165, 0.295), SHORTS)
    p.fill(p.rect(0.31, 0.615, 0.69, 0.685), SHORTS_DARK)


# --- face ----------------------------------------------------------------------------------
def _eye(p: Painter, cx: float, iris_dy: float = 0.04, brow=None, ry: float = 0.20) -> None:
    p.fill(p.ellipse(cx, 0.52, 0.135, ry), EYE_WHITE)
    p.fill(p.ellipse(cx, 0.52 + iris_dy, 0.086, 0.126 * (ry / 0.20)), IRIS)
    p.fill(p.ellipse(cx, 0.545 + iris_dy, 0.047, 0.070 * (ry / 0.20)), INK)
    p.fill(p.ellipse(cx - 0.030, 0.435 + iris_dy, 0.024, 0.034), EYE_WHITE)
    # Bolder, straighter brow than Mimi's — the boyish read.
    p.fill(p.capsule(cx - 0.115, 0.305, cx + 0.105, 0.295, 0.036), HAIR)
    if brow:
        x0, y0, x1, y1 = brow
        p.fill(p.capsule(cx + x0, y0, cx + x1, y1, 0.036), HAIR)


EYES = {
    "NEUTRAL": lambda p: (_eye(p, 0.30), _eye(p, 0.70)),
    "CLOSED": lambda p: p.fill(p.union(
        p.ring(0.30, 0.48, 0.125, 0.115, 0.17, upper=False),
        p.ring(0.70, 0.48, 0.125, 0.115, 0.17, upper=False)), INK),
    "HAPPY": lambda p: p.fill(p.union(
        p.ring(0.30, 0.58, 0.135, 0.155, 0.16, upper=True),
        p.ring(0.70, 0.58, 0.135, 0.155, 0.16, upper=True)), INK),
    "SAD": lambda p: (
        _eye(p, 0.30, iris_dy=0.075, brow=(-0.105, 0.29, 0.075, 0.205)),
        _eye(p, 0.70, iris_dy=0.075, brow=(-0.075, 0.205, 0.105, 0.29)),
    ),
    "ANGRY": lambda p: (
        _eye(p, 0.30, ry=0.135, brow=(-0.105, 0.215, 0.085, 0.385)),
        _eye(p, 0.70, ry=0.135, brow=(-0.085, 0.385, 0.105, 0.215)),
    ),
}


def _teeth(p: Painter, x0: float, x1: float, y0: float, y1: float) -> None:
    p.fill(p.rect(x0, y0, x1, y1), TEETH)


MOUTHS = {
    "NORMAL": lambda p: p.fill(p.capsule(0.37, 0.52, 0.63, 0.52, 0.044), MOUTH_INK),
    "CLOSED": lambda p: p.fill(p.capsule(0.43, 0.52, 0.57, 0.52, 0.028), MOUTH_INK),
    "A": lambda p: (
        p.fill(p.ellipse(0.5, 0.55, 0.18, 0.26), MOUTH_INK),
        _teeth(p, 0.375, 0.625, 0.335, 0.45),
        p.fill(p.ellipse(0.5, 0.735, 0.10, 0.065), TONGUE),
    ),
    "E": lambda p: (
        p.fill(p.ellipse(0.5, 0.475, 0.24, 0.155), MOUTH_INK),
        _teeth(p, 0.315, 0.685, 0.335, 0.425),
    ),
    "I": lambda p: p.fill(p.capsule(0.31, 0.52, 0.69, 0.52, 0.048), MOUTH_INK),
    "O": lambda p: p.fill(p.ellipse(0.5, 0.53, 0.13, 0.19), MOUTH_INK),
    "U": lambda p: p.fill(p.ellipse(0.5, 0.55, 0.09, 0.12), MOUTH_INK),
    "SMILE": lambda p: (
        p.fill(p.difference(p.ellipse(0.5, 0.42, 0.27, 0.24), p.rect(0.0, 0.0, 1.0, 0.42)), MOUTH_INK),
        _teeth(p, 0.315, 0.685, 0.42, 0.505),
    ),
    "SAD": lambda p: p.fill(
        p.difference(p.ellipse(0.5, 0.68, 0.24, 0.205), p.rect(0.0, 0.68, 1.0, 1.0)), MOUTH_INK),
    "SURPRISED": lambda p: (
        p.fill(p.ellipse(0.5, 0.52, 0.11, 0.17), MOUTH_INK),
        p.fill(p.ellipse(0.5, 0.565, 0.050, 0.088), TONGUE),
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
            tee_torso(painter)
        elif part in ("upper_arm_l", "upper_arm_r", "forearm_l", "forearm_r"):
            (sleeve_arm if part.startswith("upper") else forearm_band)(painter)
        elif part in ("hand_l", "hand_r"):
            hand(painter)
        elif part in ("thigh_l", "thigh_r"):
            thigh_shorts(painter)
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
            (sleeve_arm if part.startswith("upper") else forearm_band)(painter)
        elif part in ("hand_l", "hand_r"):
            hand(painter)
        elif part in ("thigh_l", "thigh_r"):
            thigh_shorts(painter)
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
            (sleeve_arm if part == "upper_arm" else forearm_band)(painter)
        elif part == "hand":
            hand(painter)
        elif part == "thigh":
            thigh_shorts(painter)
        elif part == "shin":
            shin_sock(painter)
        else:
            sneaker(painter, -1 if facing_left else 1)


def main(argv: list[str] | None = None) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description='Paint the "Rafi" character sheet.')
    parser.add_argument("--slots", default=os.path.join(here, "slots.json"))
    parser.add_argument("--out", default=os.path.join(here, "..", "docs", "assets", "arena-character-2-sheet.png"))
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
    print(f"rafii character: {len(template['slots'])} slots painted -> {out} ({size} bytes)")

    if args.preview:
        render_preview(image, template, os.path.abspath(args.preview))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
