#!/usr/bin/env python3
"""Paint a brand-new RigStudio character sheet: "Mimi" (violet hair, coral dress).

    python3 tools/make_arena_character.py [--out docs/assets/arena-character-sheet.png] [--preview out.png]

Same contract as tools/make_sample_character.py — every one of the 60 template slots
painted with flat original artwork defined in normalised slot coordinates, so the
sheet imports straight into RigStudio V3 (2048x2048 RGBA, zero stray ink).

Design brief (deliberately different from the bundled sample character):
  * long violet hair with fringe, highlight streak and a bow
  * coral dress with puff sleeves, white collar, ribbon waist and pleated skirt
  * bare arms, striped socks, white sneakers with pink accents
  * 5 eye expressions + 11 mouth shapes redrawn in a rounder "cute" style
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import png_rw  # noqa: E402
from make_sample_character import Painter  # noqa: E402  (reusable predicate painter)

# --- palette -------------------------------------------------------------------------------
SKIN = (246, 206, 172)
SKIN_SHADE = (221, 177, 142)
HAIR = (86, 58, 138)
HAIR_DARK = (64, 42, 108)
HAIR_LIGHT = (132, 98, 186)
DRESS = (230, 100, 120)
DRESS_DARK = (196, 72, 96)
COLLAR = (252, 248, 242)
SOCK = (250, 248, 244)
SOCK_STRIPE = (230, 100, 120)
SHOE = (252, 250, 246)
SHOE_ACCENT = (255, 148, 168)
SOLE = (66, 70, 116)
BOW = (255, 128, 150)
BOW_DARK = (214, 84, 108)
EYE_WHITE = (248, 248, 246)
IRIS = (108, 74, 168)
INK = (44, 40, 48)
MOUTH_INK = (150, 62, 74)
TEETH = (244, 242, 238)
TONGUE = (216, 106, 118)
BLUSH = (250, 198, 202)


# --- extra predicates ----------------------------------------------------------------------
def trapezoid(y0: float, y1: float, half0: float, half1: float):
    """Vertical band whose half-width interpolates from half0 (at y0) to half1 (at y1)."""
    def predicate(x: float, y: float) -> bool:
        if not (y0 <= y <= y1):
            return False
        t = (y - y0) / (y1 - y0)
        return abs(x - 0.5) <= half0 + (half1 - half0) * t
    return predicate


def stripes(bands: int, phase: int = 0):
    return lambda _x, y: (int(y * bands) + phase) % 2 == 0


def diamond(cx: float, cy: float, rx: float, ry: float):
    return lambda x, y: abs(x - cx) / rx + abs(y - cy) / ry <= 1.0


# --- body parts ----------------------------------------------------------------------------
def bow(p: Painter, cx: float, cy: float, s: float = 1.0) -> None:
    p.fill(p.union(diamond(cx - 0.075 * s, cy, 0.075 * s, 0.055 * s),
                   diamond(cx + 0.075 * s, cy, 0.075 * s, 0.055 * s)), BOW)
    p.fill(p.ellipse(cx, cy, 0.028 * s, 0.030 * s), BOW_DARK)


def front_head(p: Painter) -> None:
    # hair mass behind the skull, then face, fringe, side falls, bow, blush
    p.fill(p.ellipse(0.5, 0.52, 0.46, 0.50), HAIR_DARK)
    p.fill(p.ellipse(0.5, 0.56, 0.365, 0.40), SKIN)
    p.fill(p.union(
        p.ellipse(0.31, 0.335, 0.135, 0.125),
        p.ellipse(0.50, 0.295, 0.145, 0.135),
        p.ellipse(0.69, 0.335, 0.135, 0.125),
    ), HAIR)
    p.fill(p.ellipse(0.40, 0.235, 0.105, 0.045), HAIR_LIGHT)
    p.fill(p.union(p.capsule(0.125, 0.32, 0.105, 0.94, 0.095),
                   p.capsule(0.875, 0.32, 0.895, 0.94, 0.095)), HAIR)
    p.fill(p.union(p.capsule(0.135, 0.36, 0.125, 0.80, 0.030),
                   p.capsule(0.865, 0.36, 0.875, 0.80, 0.030)), HAIR_LIGHT)
    p.fill(p.ellipse(0.335, 0.70, 0.058, 0.036), BLUSH)
    p.fill(p.ellipse(0.665, 0.70, 0.058, 0.036), BLUSH)
    bow(p, 0.78, 0.20)


def front_torso(p: Painter) -> None:
    # puff-shoulder bodice + pleated skirt, white collar, ribbon waist
    p.fill(p.union(
        p.capsule(0.34, 0.16, 0.66, 0.16, 0.13),
        p.rect(0.335, 0.14, 0.665, 0.55),
        trapezoid(0.54, 0.94, 0.168, 0.365),
    ), DRESS)
    p.fill(trapezoid(0.875, 0.94, 0.325, 0.365), DRESS_DARK)
    for fx in (0.40, 0.4667, 0.5333, 0.60):
        p.fill(p.rect(fx - 0.004, 0.56, fx + 0.004, 0.86), DRESS_DARK)
    p.fill(p.ellipse(0.5, 0.105, 0.115, 0.048), COLLAR)
    p.fill(p.rect(0.335, 0.545, 0.665, 0.578), DRESS_DARK)
    bow(p, 0.5, 0.561, 0.55)
    p.fill(p.union(p.ellipse(0.5, 0.26, 0.016, 0.016), p.ellipse(0.5, 0.335, 0.016, 0.016)), COLLAR)


def upper_arm_sleeve(p: Painter, shade: tuple[int, int, int] = DRESS) -> None:
    p.fill(p.ellipse(0.5, 0.22, 0.40, 0.195), shade)
    p.fill(p.rect(0.235, 0.375, 0.765, 0.435), DRESS_DARK if shade == DRESS else shade)
    p.fill(p.capsule(0.5, 0.435, 0.5, 0.92, 0.185), SKIN)


def forearm_arm(p: Painter, shade=None) -> None:
    p.fill(p.capsule(0.5, 0.10, 0.5, 0.90, 0.185), SKIN)


def hand(p: Painter) -> None:
    p.fill(p.union(p.ellipse(0.5, 0.55, 0.32, 0.38), p.ellipse(0.315, 0.50, 0.105, 0.15)), SKIN)


def thigh(p: Painter, _shade=None) -> None:
    p.fill(p.capsule(0.5, 0.06, 0.5, 0.94, 0.33), SKIN)


def shin_sock(p: Painter, _shade=None) -> None:
    sock = p.capsule(0.5, 0.06, 0.5, 0.94, 0.335)
    p.fill(sock, SOCK)
    p.fill(lambda x, y: sock(x, y) and stripes(7)(x, y), SOCK_STRIPE)
    p.fill(p.rect(0.235, 0.03, 0.765, 0.09), SOCK_STRIPE)


def sneaker(p: Painter, facing: int = 0) -> None:
    body = p.union(p.rect(0.22, 0.28, 0.76, 0.64),
                   p.ellipse(0.60 + 0.09 * facing, 0.60, 0.26, 0.17),
                   p.ellipse(0.30 - 0.03 * facing, 0.52, 0.11, 0.16))
    p.fill(body, SHOE)
    p.fill(p.rect(0.14, 0.64, 0.90, 0.78), SOLE)
    p.fill(p.capsule(0.24 + 0.04 * facing, 0.44, 0.72 + 0.04 * facing, 0.38, 0.032), SHOE_ACCENT)
    p.fill(p.union(p.ellipse(0.50, 0.40, 0.026, 0.026), p.ellipse(0.585, 0.485, 0.026, 0.026)), SOLE)


def side_head(p: Painter, facing_left: bool) -> None:
    d = -1 if facing_left else 1
    p.fill(p.ellipse(0.5 - 0.04 * d, 0.52, 0.42, 0.47), HAIR_DARK)
    p.fill(p.ellipse(0.5 + 0.02 * d, 0.56, 0.335, 0.385), SKIN)
    p.fill(p.ellipse(0.5 + 0.335 * d, 0.645, 0.075, 0.055), SKIN)
    p.fill(p.union(p.ellipse(0.5 + 0.10 * d, 0.35, 0.21, 0.13),
                   p.ellipse(0.5 - 0.07 * d, 0.40, 0.165, 0.115)), HAIR)
    p.fill(p.ellipse(0.5 + 0.07 * d, 0.285, 0.10, 0.045), HAIR_LIGHT)
    p.fill(p.capsule(0.5 - 0.295 * d, 0.33, 0.5 - 0.335 * d, 0.94, 0.10), HAIR)
    bow(p, 0.5 - 0.30 * d, 0.22, 0.9)


def side_torso(p: Painter) -> None:
    p.fill(p.union(
        p.capsule(0.42, 0.14, 0.58, 0.14, 0.115),
        p.rect(0.365, 0.12, 0.635, 0.52),
        trapezoid(0.51, 0.92, 0.145, 0.305),
    ), DRESS)
    p.fill(trapezoid(0.855, 0.92, 0.272, 0.305), DRESS_DARK)
    p.fill(p.ellipse(0.5, 0.095, 0.092, 0.042), COLLAR)
    p.fill(p.rect(0.365, 0.515, 0.635, 0.545), DRESS_DARK)


def back_head(p: Painter) -> None:
    p.fill(p.ellipse(0.5, 0.50, 0.43, 0.47), HAIR)
    for fx in (0.40, 0.50, 0.60):
        p.fill(p.capsule(fx, 0.32, fx, 0.86, 0.026), HAIR_DARK)
    p.fill(p.union(p.ellipse(0.30, 0.90, 0.105, 0.075),
                   p.ellipse(0.50, 0.93, 0.115, 0.085),
                   p.ellipse(0.70, 0.90, 0.105, 0.075)), HAIR)
    p.fill(p.ellipse(0.37, 0.335, 0.09, 0.048), HAIR_LIGHT)
    bow(p, 0.5, 0.175, 1.0)


def back_torso(p: Painter) -> None:
    p.fill(p.union(
        p.capsule(0.34, 0.16, 0.66, 0.16, 0.125),
        p.rect(0.345, 0.14, 0.655, 0.55),
        trapezoid(0.54, 0.94, 0.165, 0.36),
    ), DRESS)
    p.fill(trapezoid(0.875, 0.94, 0.322, 0.36), DRESS_DARK)
    p.fill(p.rect(0.492, 0.16, 0.508, 0.54), DRESS_DARK)
    p.fill(p.ellipse(0.5, 0.105, 0.11, 0.046), COLLAR)
    p.fill(p.rect(0.345, 0.545, 0.655, 0.575), DRESS_DARK)


# --- face ----------------------------------------------------------------------------------
def _big_eye(p: Painter, cx: float, iris_dy: float = 0.04, brow=None, ry: float = 0.20) -> None:
    p.fill(p.ellipse(cx, 0.52, 0.135, ry), EYE_WHITE)
    p.fill(p.ellipse(cx + 0.008, 0.52 + iris_dy, 0.083, 0.128 * (ry / 0.20)), IRIS)
    p.fill(p.ellipse(cx + 0.008, 0.545 + iris_dy, 0.045, 0.072 * (ry / 0.20)), INK)
    p.fill(p.ellipse(cx - 0.028, 0.435 + iris_dy, 0.026, 0.038), EYE_WHITE)
    p.fill(p.capsule(cx - 0.115, 0.325, cx + 0.105, 0.30, 0.030), INK)
    if brow:
        x0, y0, x1, y1 = brow
        p.fill(p.capsule(cx + x0, y0, cx + x0 + (x1 - x0), y1, 0.028), HAIR_DARK)


EYES = {
    "NEUTRAL": lambda p: (_big_eye(p, 0.30), _big_eye(p, 0.70)),
    "CLOSED": lambda p: p.fill(p.union(
        p.ring(0.30, 0.48, 0.125, 0.115, 0.17, upper=False),
        p.ring(0.70, 0.48, 0.125, 0.115, 0.17, upper=False)), INK),
    "HAPPY": lambda p: p.fill(p.union(
        p.ring(0.30, 0.58, 0.135, 0.155, 0.16, upper=True),
        p.ring(0.70, 0.58, 0.135, 0.155, 0.16, upper=True)), INK),
    "SAD": lambda p: (
        _big_eye(p, 0.30, iris_dy=0.075, brow=(-0.105, 0.30, 0.075, 0.215)),
        _big_eye(p, 0.70, iris_dy=0.075, brow=(-0.075, 0.215, 0.105, 0.30)),
    ),
    "ANGRY": lambda p: (
        _big_eye(p, 0.30, ry=0.135, brow=(-0.105, 0.225, 0.085, 0.375)),
        _big_eye(p, 0.70, ry=0.135, brow=(-0.085, 0.375, 0.105, 0.225)),
    ),
}


def _teeth_band(p: Painter, x0: float, x1: float, y0: float, y1: float) -> None:
    p.fill(p.rect(x0, y0, x1, y1), TEETH)


MOUTHS = {
    "NORMAL": lambda p: p.fill(p.capsule(0.38, 0.52, 0.62, 0.52, 0.040), MOUTH_INK),
    "CLOSED": lambda p: p.fill(p.capsule(0.435, 0.52, 0.565, 0.52, 0.028), MOUTH_INK),
    "A": lambda p: (
        p.fill(p.ellipse(0.5, 0.55, 0.17, 0.26), MOUTH_INK),
        _teeth_band(p, 0.365, 0.635, 0.335, 0.45),
        p.fill(p.ellipse(0.5, 0.735, 0.095, 0.065), TONGUE),
    ),
    "E": lambda p: (
        p.fill(p.ellipse(0.5, 0.475, 0.235, 0.155), MOUTH_INK),
        _teeth_band(p, 0.315, 0.685, 0.335, 0.425),
    ),
    "I": lambda p: p.fill(p.capsule(0.31, 0.52, 0.69, 0.52, 0.046), MOUTH_INK),
    "O": lambda p: p.fill(p.ellipse(0.5, 0.53, 0.125, 0.185), MOUTH_INK),
    "U": lambda p: p.fill(p.ellipse(0.5, 0.55, 0.085, 0.115), MOUTH_INK),
    "SMILE": lambda p: (
        p.fill(p.difference(p.ellipse(0.5, 0.42, 0.26, 0.23), p.rect(0.0, 0.0, 1.0, 0.42)), MOUTH_INK),
        _teeth_band(p, 0.315, 0.685, 0.42, 0.505),
    ),
    "SAD": lambda p: p.fill(
        p.difference(p.ellipse(0.5, 0.68, 0.235, 0.20), p.rect(0.0, 0.68, 1.0, 1.0)), MOUTH_INK),
    "SURPRISED": lambda p: (
        p.fill(p.ellipse(0.5, 0.52, 0.105, 0.165), MOUTH_INK),
        p.fill(p.ellipse(0.5, 0.56, 0.048, 0.085), TONGUE),
    ),
    "ANGRY": lambda p: (
        p.fill(p.rect(0.30, 0.40, 0.70, 0.645), MOUTH_INK),
        _teeth_band(p, 0.335, 0.665, 0.435, 0.615),
        p.fill(p.union(p.rect(0.409, 0.435, 0.421, 0.615),
                       p.rect(0.494, 0.435, 0.506, 0.615),
                       p.rect(0.579, 0.435, 0.591, 0.615)), MOUTH_INK),
    ),
}


# --- dispatch ------------------------------------------------------------------------------
SLEEVES = {"upper_arm_l": None, "upper_arm_r": None, "forearm_l": None, "forearm_r": None}


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
            (upper_arm_sleeve if part.startswith("upper") else forearm_arm)(painter)
        elif part in ("hand_l", "hand_r"):
            hand(painter)
        elif part in ("thigh_l", "thigh_r"):
            thigh(painter)
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
            (upper_arm_sleeve if part.startswith("upper") else forearm_arm)(painter)
        elif part in ("hand_l", "hand_r"):
            hand(painter)
        elif part in ("thigh_l", "thigh_r"):
            thigh(painter)
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
            (upper_arm_sleeve if part == "upper_arm" else forearm_arm)(painter)
        elif part == "hand":
            hand(painter)
        elif part == "thigh":
            thigh(painter)
        elif part == "shin":
            shin_sock(painter)
        else:
            sneaker(painter, -1 if facing_left else 1)


# --- rough paper-doll preview (not part of the sheet) --------------------------------------
def blit(dst: png_rw.Image, src: png_rw.Image, sx: int, sy: int, sw: int, sh: int,
         dx: int, dy: int, scale: int) -> None:
    for row in range(sh // scale):
        for col in range(sw // scale):
            r, g, b, a = src.get(sx + col * scale, sy + row * scale)
            if a > 0:
                dst.set(dx + col, dy + row, (r, g, b, 255))


def render_preview(sheet: png_rw.Image, slots: dict, out: str) -> None:
    canvas = png_rw.Image.blank(560, 1020, (255, 255, 255, 255))
    rect = {s["id"]: s for s in slots["slots"]}

    def put(sid: str, dx: int, dy: int, scale: int = 2) -> None:
        s = rect[sid]
        blit(canvas, sheet, s["x"], s["y"], s["w"], s["h"], dx, dy, scale)

    put("front_head", 184, 40)
    put("front_torso", 184, 196)
    put("front_upper_arm_l", 84, 230)
    put("front_upper_arm_r", 380, 230)
    put("front_forearm_l", 84, 391)
    put("front_forearm_r", 380, 391)
    put("front_hand_l", 84, 540)
    put("front_hand_r", 380, 540)
    put("front_thigh_l", 196, 505)
    put("front_thigh_r", 292, 505)
    put("front_shin_l", 196, 705)
    put("front_shin_r", 292, 705)
    put("front_foot_l", 180, 908)
    put("front_foot_r", 276, 908)
    png_rw.write(canvas, out)
    print(f"preview -> {out}")


def main(argv: list[str] | None = None) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description='Paint the "Mimi" character sheet.')
    parser.add_argument("--slots", default=os.path.join(here, "slots.json"))
    parser.add_argument("--out", default=os.path.join(here, "..", "docs", "assets", "arena-character-sheet.png"))
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
    print(f"arena character: {len(template['slots'])} slots painted -> {out} ({size} bytes)")

    if args.preview:
        render_preview(image, template, os.path.abspath(args.preview))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
