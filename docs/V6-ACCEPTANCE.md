# RigStudio V6 — Acceptance Test (Spec §64)

32-step checklist. Every step must be performed on the real app (no mockups); a step only
counts when the stated result is observed. Engine-level guarantees are backed by the 212 core
tests (`bash tools/verify_all.sh`).

| # | Step | Expected result | Verified by |
|---|------|-----------------|-------------|
| 1 | Open the app with no projects | Home screen, "Create character" entry points | Manual |
| 2 | Import a 2048×2048 RGBA sheet (10 required front parts) | 9-step progress, then the character opens in the editor | Manual / ExtractionTests |
| 3 | Import a sheet with one part missing | Validation names the exact missing slot | Manual / ExtractionTests |
| 4 | Tap a slot preview in the import grid | Enlarged preview with ✓ / ⚠ / ✕ status | Manual |
| 5 | Re-detect / replace / clear one slot | Only that slot changes | Manual |
| 6 | Extraction padding 8% → 12% | Parts keep their look; no clipped pixels | Manual / ExtractionTests |
| 7 | Open the editor | Character framed full-body, idle playing | Manual |
| 8 | Play / pause / stop / restart / loop / scrub / step | Transport controls all respond (V4 §29) | Manual |
| 9 | Set speed 0.25× and 3× | Animation slows / speeds up exactly | Manual / PlaybackTests |
| 10 | Switch front → side-left → back view | Same scale, stage position, playhead, playback state | Manual (V6 view-switch preservation) |
| 11 | Play SIDE WALK with no profile artwork | "Side View Assets Not Found" | Manual / AnimationTests |
| 12 | Play SIDE WALK with profile artwork | True profile art, never rotated front | Manual |
| 13 | Mirror Side View on import | Real mirrored side assets created | Manual / SheetImporter |
| 14 | Crossfade check: switch Walk → Run | ~220 ms blend, no snap | Manual / AnimationEngineTests |
| 15 | Walk body + Talk face together | Layered blend, face never overwrites body | Manual / AnimationEngineTests |
| 16 | Turn on Pose Mode | Playback pauses; wrist/ankle handles visible | Manual |
| 17 | Drag a hand handle to a new spot | Limb follows via 2-bone IK; elbow keeps its bend side | Manual / PoseEditingTests |
| 18 | Drag a target beyond reach | Limb stretches to max reach and stops — never dislocates | Manual / TwoBoneIkTests |
| 19 | Drag the ankle backwards | Knee refuses to hyperextend (joint limits) | Manual / PoseEditingTests |
| 20 | Pose tools: Reset / Mirror / Copy / Paste | Each performs its real operation | Manual |
| 21 | Save pose → apply → delete → duplicate | Pose library CRUD round-trips (poses.json, schemaVersion 6) | Manual / UserContentTests |
| 22 | Capture 2+ keyframes → Save & play | Custom animation plays like a library clip | Manual / UserContentTests |
| 23 | Mirror Animation on a custom clip | L↔R tracks remapped (not timeline-reversed) | Manual / UserContentTests |
| 24 | Tap a part in Pose Mode → Bring to front | Part draws above the rest; survives undo | Manual / RenderTests (z-order) |
| 25 | Undo / redo across pose, keyframe, layer edits | One history, bounded, symmetric | Manual / EditorInfraTests |
| 26 | Backgrounds: transparent / black / white / checker / custom | Stage + export honour each | Manual / ExportTests |
| 27 | Export MP4 1080p30 | Offline render, same framing as preview | Manual / ExportTests |
| 28 | Export MP4 4K 60 fps | Same pipeline, only resolution/framerate differ | Manual |
| 29 | Export transparent → MP4 | Clear error; PNG sequence offered for alpha | Manual / ExportTests |
| 30 | Chroma key green/blue/custom export | Solid background exported exactly | Manual / ExportTests |
| 31 | Reopen a project | Rig rebuilt from manifest; camera, z-order, poses, animations, accessories restored | Manual / ProjectTests (schema 6) |
| 32 | Enable the debug overlay | Bones, pivots, bounds, z-order badges, IK chains, FPS | Manual |
| 33 | Import a hat PNG → attach Head → rotate head | Hat rides the head through every animation | Manual / AccessoryTests |
| 34 | Attach a sword to a hand, size/rotate/layer it | Follows the arm chain; exported MP4 shows it | Manual / AccessoryTests |

## Status legend

- **Manual** — must be performed on a device/emulator with the CI-built APK.
- **/<Suite>Tests** — already machine-verified in `tools/run_core_tests.sh` (212 tests).

## Current state (2026-09-07)

Engine foundations shipped and green: IK solver, pose editing, user keyframes/clips, pose
library, z-order overrides, props/accessories (attach + follow + export), schemaVersion 6
persistence with forward migration, 4K/60 export settings, 23-clip library, rig inspector.
Remaining UI-first steps: on-device pass of the Manual rows.
