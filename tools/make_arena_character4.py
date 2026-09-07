#!/usr/bin/env python3
"""Paint a fourth original RigStudio character sheet: "Sadia" (hijab girl, ivory kameez).

    python3 tools/make_arena_character4.py [--out docs/assets/arena-character-4-sheet.png] [--preview out.png]

Same contract as the other sheet painters: all 60 template slots filled with flat original
artwork in normalised slot coordinates — imports straight into RigStudio
(2048x2048 RGBA, zero stray ink, riggable, 4 views, 5 expressions, 11 mouths).

Style brief (the "modest warm" look, distinct from sample / Mimi / Rafi / Tuni):
  * deep rose-maroon hijab with fold seams and a gold brooch — a brand-new head silhouette
  * ivory kameez with a teal neckline placket, hem band and cuffs; teal-trimmed side seams
  * dark plum churidar leggings with a soft highlight stripe; golden flats with a strap + bow
  * hazel eyes with a lash line and soft arched brows; rose-lip mouths (11 shapes)
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
INK = (52, 38, 44)
HIJAB = (168, 62, 82)
HIJAB_DARK = (132, 44, 62)
HIJAB_LIGHT = (206, 100, 118)
SKIN = (248, 210, 178)
SKIN_SHADE = (224, 182, 148)
KAMEEZ = (246, 238, 224)
KAMEEZ_DARK = (214, 202, 182)
TEAL = (36, 128, 120)
TEAL_DARK = (26, 94, 90)
LEGGINGS = (58, 48, 74)
LEGGINGS_LIGHT = (78, 66, 96)
SHOE = (232, 178, 84)
SHOE_DARK = (192, 138, 52)
SOLE = (250, 244, 232)
EYE_WHITE = (250, 250, 248)
IRIS = (122, 72, 44)
IRIS_DEEP = (86, 48, 30)
LASH = (58, 36, 40)
LIPS = (192, 84, 96)
LIPS_DARK = (158, 62, 76)
TEETH = (246, 244, 240)
TONGUE = (210, 96, 104)
BLUSH = (255, 170, 158)
HAIR = (48, 32, 36)
GOLD = (240, 196, 96)

OUTLINE = 0.022  # medium-soft silhouette expansion


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


# --- body parts ----------------------------------------------------------------------------
def front_head(p: Painter) -> None:
    # the hijab: one big rounded drape with a darker rim and soft fold seams
    o_ellipse(p, 0.5, 0.50, 0.445, 0.455, HIJAB)
    p.fill(p.difference(p.ellipse(0.5, 0.50, 0.445, 0.455), p.ellipse(0.5, 0.50, 0.415, 0.425)), HIJAB_DARK)
    p.fill(p.union(
        p.capsule(0.42, 0.24, 0.36, 0.56, 0.014),
        p.capsule(0.58, 0.24, 0.64, 0.56, 0.014),
        p.capsule(0.5, 0.20, 0.5, 0.30, 0.014),
    ), HIJAB_DARK)
    p.fill(p.capsule(0.31, 0.42, 0.27, 0.72, 0.018), HIJAB_LIGHT)
    p.fill(p.capsule(0.69, 0.42, 0.73, 0.72, 0.018), HIJAB_LIGHT)
    # the face opening
    o_ellipse(p, 0.5, 0.565, 0.355, 0.375, SKIN)
    # hijab wrapping under the chin
    p.fill(p.difference(
        p.ellipse(0.5, 0.565, 0.355, 0.375),
        p.ellipse(0.5, 0.540, 0.355, 0.375),
    ), HIJAB_LIGHT)
    # hair peeking from under the hijab above the forehead
    p.fill(p.union(
        diamond(0.40, 0.315, 0.052, 0.040),
        diamond(0.50, 0.295, 0.058, 0.046),
        diamond(0.60, 0.315, 0.052, 0.040),
    ), HAIR)
    # gold brooch pinning the drape
    p.fill(p.ellipse(0.715, 0.40, 0.030, 0.030), GOLD)
    p.fill(p.ellipse(0.715, 0.40, 0.013, 0.013), TEAL)
    # blush
    p.fill(p.ellipse(0.325, 0.72, 0.055, 0.034), BLUSH)
    p.fill(p.ellipse(0.675, 0.72, 0.055, 0.034), BLUSH)


def front_torso(p: Painter) -> None:
    # ivory kameez: rounded shoulders, straight body, flared hem
    o_capsule(p, 0.36, 0.18, 0.64, 0.18, 0.12, KAMEEZ)
    o_rect(p, 0.35, 0.16, 0.65, 0.66, KAMEEZ)
    o_trapezoid(p, 0.60, 0.95, 0.165, 0.27, KAMEEZ)
    # teal hem band following the flare
    p.fill(trapezoid(0.875, 0.95, 0.245, 0.27), TEAL)
    # neckline placket + little gold buttons
    p.fill(p.capsule(0.5, 0.155, 0.5, 0.32, 0.016), TEAL)
    for cy in (0.22, 0.28, 0.34):
        p.fill(p.ellipse(0.5, cy, 0.016, 0.016), GOLD)
    # side seams
    p.fill(p.capsule(0.385, 0.30, 0.385, 0.86, 0.010), KAMEEZ_DARK)
    p.fill(p.capsule(0.615, 0.30, 0.615, 0.86, 0.010), KAMEEZ_DARK)


def sleeve_arm(p: Painter) -> None:
    # long kameez sleeve
    o_capsule(p, 0.5, 0.10, 0.5, 0.74, 0.155, KAMEEZ)
    p.fill(p.capsule(0.42, 0.24, 0.42, 0.62, 0.020), KAMEEZ_DARK)
    # teal cuff
    p.fill(p.rect(0.295, 0.665, 0.705, 0.755), TEAL)


def forearm_arm(p: Painter) -> None:
    # sleeve continues to the wrist with its cuff
    o_capsule(p, 0.5, 0.06, 0.5, 0.80, 0.125, KAMEEZ)
    p.fill(p.rect(0.315, 0.72, 0.685, 0.80), TEAL)


def hand(p: Painter) -> None:
    o_ellipse(p, 0.5, 0.55, 0.31, 0.37, SKIN)


def thigh_pants(p: Painter) -> None:
    # dark plum churidar with a soft highlight stripe
    o_capsule(p, 0.5, 0.05, 0.5, 0.97, 0.30, LEGGINGS)
    p.fill(p.capsule(0.42, 0.16, 0.42, 0.88, 0.038), LEGGINGS_LIGHT)


def shin_sock(p: Painter) -> None:
    o_capsule(p, 0.5, 0.05, 0.5, 0.97, 0.27, LEGGINGS)
    p.fill(p.capsule(0.43, 0.16, 0.43, 0.86, 0.032), LEGGINGS_LIGHT)


def flat_shoe(p: Painter, facing: int = 0) -> None:
    # a golden flat with a strap and a tiny bow
    o_rect(p, 0.26, 0.40, 0.74, 0.70, SHOE)
    o_ellipse(p, 0.5 + 0.05 * facing, 0.58, 0.235, 0.135, SHOE)
    p.fill(p.capsule(0.31, 0.50, 0.69, 0.50, 0.030), SHOE_DARK)
    p.fill(p.union(
        diamond(0.44, 0.42, 0.036, 0.028),
        diamond(0.56, 0.42, 0.036, 0.028),
    ), GOLD)
    o_rect(p, 0.20, 0.70, 0.80, 0.79, SOLE)


def side_head(p: Painter, facing_left: bool) -> None:
    d = -1 if facing_left else 1
    # hijab in profile: rounded back, face opening toward the facing side
    o_ellipse(p, 0.5 - 0.03 * d, 0.50, 0.43, 0.44, HIJAB)
    p.fill(p.difference(
        p.ellipse(0.5 - 0.03 * d, 0.50, 0.43, 0.44),
        p.ellipse(0.5 - 0.03 * d, 0.50, 0.40, 0.41),
    ), HIJAB_DARK)
    p.fill(p.capsule(0.5 - 0.22 * d, 0.26, 0.5 - 0.30 * d, 0.70, 0.016), HIJAB_DARK)
    p.fill(p.capsule(0.5 - 0.10 * d, 0.30, 0.5 - 0.02 * d, 0.24, 0.014), HIJAB_LIGHT)
    # face opening + gentle nose
    o_ellipse(p, 0.5 + 0.05 * d, 0.565, 0.30, 0.345, SKIN)
    p.fill(p.ellipse(0.5 + 0.345 * d, 0.685, 0.048, 0.036), SKIN)
    # hair peek near the forehead
    p.fill(diamond(0.5 + 0.13 * d, 0.315, 0.052, 0.040), HAIR)
    # brooch on the visible side
    p.fill(p.ellipse(0.5 - 0.14 * d, 0.33, 0.026, 0.026), GOLD)
    p.fill(p.ellipse(0.5 - 0.14 * d, 0.33, 0.012, 0.012), TEAL)


def side_torso(p: Painter) -> None:
    o_capsule(p, 0.44, 0.15, 0.56, 0.15, 0.105, KAMEEZ)
    o_rect(p, 0.385, 0.14, 0.615, 0.60, KAMEEZ)
    o_trapezoid(p, 0.58, 0.92, 0.135, 0.24, KAMEEZ)
    p.fill(trapezoid(0.855, 0.92, 0.215, 0.24), TEAL)
    p.fill(p.capsule(0.47, 0.26, 0.47, 0.84, 0.010), KAMEEZ_DARK)


def back_head(p: Painter) -> None:
    # the hijab from behind: full drape with fold seams and a pointed back hem
    o_ellipse(p, 0.5, 0.50, 0.44, 0.44, HIJAB)
    p.fill(diamond(0.5, 0.90, 0.17, 0.11), HIJAB)
    p.fill(p.union(
        p.capsule(0.40, 0.26, 0.40, 0.78, 0.020),
        p.capsule(0.50, 0.24, 0.50, 0.84, 0.020),
        p.capsule(0.60, 0.26, 0.60, 0.78, 0.020),
    ), HIJAB_DARK)
    p.fill(p.capsule(0.38, 0.20, 0.62, 0.20, 0.020), HIJAB_LIGHT)
    p.fill(p.capsule(0.5, 0.80, 0.5, 0.94, 0.026), HIJAB_DARK)
    # neck sliver under the drape
    p.fill(p.ellipse(0.5, 0.955, 0.09, 0.035), SKIN_SHADE)


def back_torso(p: Painter) -> None:
    o_capsule(p, 0.35, 0.16, 0.65, 0.16, 0.125, KAMEEZ)
    o_rect(p, 0.34, 0.15, 0.66, 0.62, KAMEEZ)
    o_trapezoid(p, 0.60, 0.93, 0.18, 0.28, KAMEEZ)
    p.fill(trapezoid(0.865, 0.93, 0.245, 0.28), TEAL)
    p.fill(p.capsule(0.5, 0.17, 0.5, 0.87, 0.011), KAMEEZ_DARK)


# --- face ----------------------------------------------------------------------------------
def _girl_eye(p: Painter, cx: float, iris_dy: float = 0.045, brow=None, ry: float = 0.20) -> None:
    """Hazel eye with a soft lash line and an arched brow."""
    p.fill(p.ellipse(cx, 0.52, 0.145, ry), EYE_WHITE)
    p.fill(p.ellipse(cx, 0.52 + iris_dy, 0.105, 0.135 * (ry / 0.20)), IRIS)
    p.fill(p.ellipse(cx, 0.545 + iris_dy, 0.052, 0.075 * (ry / 0.20)), IRIS_DEEP)
    p.fill(p.ellipse(cx + 0.008, 0.558 + iris_dy, 0.024, 0.033), INK)
    p.fill(p.ellipse(cx - 0.034, 0.455 + iris_dy, 0.028, 0.038), EYE_WHITE)
    # lash line along the upper lid + a small outer wing
    p.fill(p.capsule(cx - 0.125, 0.545 - ry, cx + 0.125, 0.545 - ry, 0.026), LASH)
    p.fill(diamond(cx + 0.145, 0.50 - ry * 0.5, 0.022, 0.030), LASH)
    # soft arched brow
    p.fill(p.capsule(cx - 0.10, 0.295, cx + 0.09, 0.283, 0.026), LASH)
    if brow:
        x0, y0, x1, y1 = brow
        p.fill(p.capsule(cx + x0, y0, cx + x1, y1, 0.026), LASH)


EYES = {
    "NEUTRAL": lambda p: (_girl_eye(p, 0.295), _girl_eye(p, 0.705)),
    "CLOSED": lambda p: p.fill(p.union(
        p.ring(0.295, 0.50, 0.145, 0.125, 0.16, upper=False),
        p.ring(0.705, 0.50, 0.145, 0.125, 0.16, upper=False)), LASH),
    "HAPPY": lambda p: p.fill(p.union(
        p.ring(0.295, 0.585, 0.15, 0.16, 0.15, upper=True),
        p.ring(0.705, 0.585, 0.15, 0.16, 0.15, upper=True)), LASH),
    "SAD": lambda p: (
        _girl_eye(p, 0.295, iris_dy=0.08, brow=(-0.105, 0.285, 0.075, 0.205)),
        _girl_eye(p, 0.705, iris_dy=0.08, brow=(-0.075, 0.205, 0.105, 0.285)),
    ),
    "ANGRY": lambda p: (
        _girl_eye(p, 0.295, ry=0.155, brow=(-0.105, 0.200, 0.085, 0.370)),
        _girl_eye(p, 0.705, ry=0.155, brow=(-0.085, 0.370, 0.105, 0.200)),
    ),
}


def _teeth(p: Painter, x0: float, x1: float, y0: float, y1: float) -> None:
    p.fill(p.rect(x0, y0, x1, y1), TEETH)


MOUTHS = {
    "NORMAL": lambda p: (
        p.fill(p.capsule(0.38, 0.52, 0.62, 0.52, 0.048), LIPS),
        p.fill(p.capsule(0.38, 0.555, 0.62, 0.555, 0.014), LIPS_DARK),
    ),
    "CLOSED": lambda p: p.fill(p.capsule(0.43, 0.52, 0.57, 0.52, 0.028), LIPS),
    "A": lambda p: (
        p.fill(p.ellipse(0.5, 0.55, 0.18, 0.26), LIPS),
        _teeth(p, 0.375, 0.625, 0.335, 0.45),
        p.fill(p.ellipse(0.5, 0.73, 0.10, 0.06), TONGUE),
    ),
    "E": lambda p: (
        p.fill(p.ellipse(0.5, 0.475, 0.245, 0.155), LIPS),
        _teeth(p, 0.315, 0.685, 0.335, 0.425),
    ),
    "I": lambda p: p.fill(p.capsule(0.33, 0.52, 0.67, 0.52, 0.048), LIPS),
    "O": lambda p: (
        p.fill(p.ellipse(0.5, 0.53, 0.13, 0.19), LIPS),
        p.fill(p.ellipse(0.5, 0.55, 0.07, 0.10), LIPS_DARK),
    ),
    "U": lambda p: (
        p.fill(p.ellipse(0.5, 0.55, 0.095, 0.12), LIPS),
        p.fill(p.capsule(0.46, 0.52, 0.54, 0.52, 0.020), LIPS_DARK),
    ),
    "SMILE": lambda p: (
        p.fill(p.difference(p.ellipse(0.5, 0.42, 0.28, 0.245), p.rect(0.0, 0.0, 1.0, 0.42)), LIPS),
        _teeth(p, 0.315, 0.685, 0.42, 0.505),
    ),
    "SAD": lambda p: p.fill(
        p.difference(p.ellipse(0.5, 0.68, 0.245, 0.21), p.rect(0.0, 0.68, 1.0, 1.0)), LIPS),
    "SURPRISED": lambda p: (
        p.fill(p.ellipse(0.5, 0.52, 0.115, 0.175), LIPS),
        p.fill(p.ellipse(0.5, 0.565, 0.052, 0.09), TONGUE),
    ),
    "ANGRY": lambda p: (
        p.fill(p.rect(0.31, 0.41, 0.69, 0.64), LIPS),
        _teeth(p, 0.345, 0.655, 0.44, 0.61),
        p.fill(p.union(p.rect(0.409, 0.44, 0.421, 0.61),
                       p.rect(0.494, 0.44, 0.506, 0.61),
                       p.rect(0.579, 0.44, 0.591, 0.61)), LIPS),
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

    if view in ("FRONT", "BACK"):
        if part == "head":
            (front_head if view == "FRONT" else back_head)(painter)
        elif part == "torso":
            (front_torso if view == "FRONT" else back_torso)(painter)
        elif part in ("upper_arm_l", "upper_arm_r", "forearm_l", "forearm_r"):
            (sleeve_arm if part.startswith("upper") else forearm_arm)(painter)
        elif part in ("hand_l", "hand_r"):
            hand(painter)
        elif part in ("thigh_l", "thigh_r"):
            thigh_pants(painter)
        elif part in ("shin_l", "shin_r"):
            shin_sock(painter)
        else:
            flat_shoe(painter)
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
            flat_shoe(painter, -1 if facing_left else 1)


def main(argv: list[str] | None = None) -> int:
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description='Paint the "Sadia" hijab-girl character sheet.')
    parser.add_argument("--slots", default=os.path.join(here, "slots.json"))
    parser.add_argument("--out", default=os.path.join(here, "..", "docs", "assets", "arena-character-4-sheet.png"))
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
    print(f"sadia character: {len(template['slots'])} slots painted -> {out} ({size} bytes)")

    if args.preview:
        render_preview(image, template, os.path.abspath(args.preview))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
