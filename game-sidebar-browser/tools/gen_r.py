#!/usr/bin/env python3
"""Generates a compile-time `R` for the offline check, straight from the real resource files.

Because the constants come from res/ (not from a hand-written list), a reference such as
`R.string.history_empty` or `R.id.webContainer` fails to compile when the resource is missing -
which is exactly the class of error a normal build only reports after AAPT2 runs.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

res_dir, package, out_path = sys.argv[1], sys.argv[2], sys.argv[3]


def emit(group, names):
    lines = [f"    object {group} {{"]
    for name in sorted(set(names)):
        lines.append(f"        const val {name}: Int = 0")
    lines.append("    }")
    return lines


string_names, color_names, dimen_names, style_names, array_names = [], [], [], [], []
values_dir = os.path.join(res_dir, "values")
if os.path.isdir(values_dir):
    for file_name in sorted(os.listdir(values_dir)):
        if not file_name.endswith(".xml"):
            continue
        root = ET.parse(os.path.join(values_dir, file_name)).getroot()
        for node in root:
            name = node.attrib.get("name")
            if not name:
                continue
            tag = node.tag
            if tag == "string":
                string_names.append(name)
            elif tag == "color":
                color_names.append(name)
            elif tag == "dimen":
                dimen_names.append(name)
            elif tag == "style":
                style_names.append(name.replace(".", "_"))
            elif tag in ("string-array", "array", "integer-array"):
                array_names.append(name)

file_groups = {
    "drawable": "drawable",
    "layout": "layout",
    "mipmap-anydpi-v26": "mipmap",
    "mipmap-hdpi": "mipmap",
    "xml": "xml",
    "anim": "anim",
    "raw": "raw",
}
file_names = {}
for folder, group in file_groups.items():
    folder_path = os.path.join(res_dir, folder)
    if not os.path.isdir(folder_path):
        continue
    for file_name in sorted(os.listdir(folder_path)):
        if file_name.endswith(".xml") or file_name.endswith(".png") or file_name.endswith(".webp"):
            file_names.setdefault(group, []).append(os.path.splitext(file_name)[0])

# Every @+id in every layout, so R.id.* references are checked too.
id_names = []
layout_dir = os.path.join(res_dir, "layout")
if os.path.isdir(layout_dir):
    for file_name in sorted(os.listdir(layout_dir)):
        if not file_name.endswith(".xml"):
            continue
        text = open(os.path.join(layout_dir, file_name), encoding="utf-8").read()
        id_names.extend(re.findall(r'@\+id/([A-Za-z0-9_]+)', text))

body = ['@file:Suppress("unused", "ClassName", "MayBeConstant", "ObjectPropertyName")', "",
        f"package {package}", ""]
for group, names in [("string", string_names), ("color", color_names), ("dimen", dimen_names),
                     ("style", style_names), ("array", array_names), ("id", id_names)]:
    body.extend(emit(group, names))
    body.append("")
for group, names in sorted(file_names.items()):
    body.extend(emit(group, names))
    body.append("")
body.append("object R {")
for group in ["string", "color", "dimen", "style", "array", "id"] + sorted(file_names):
    body.append(f"    val {group} = __R.{group}")
body.append("}")

os.makedirs(os.path.dirname(out_path), exist_ok=True)
content = "\n".join(body) + "\n"
content = content.replace("object string {", "object string {", 1)
# Wrap the generated groups in a holder object so `R.string` can reference them.
content = content.replace(f"package {package}\n", f"package {package}\n\nobject __R {{", 1)
content = content.replace("\nobject R {", "}\n\nobject R {", 1)
open(out_path, "w", encoding="utf-8").write(content)

counts = {
    "string": len(set(string_names)), "drawable": len(set(file_names.get("drawable", []))),
    "id": len(set(id_names)), "layout": len(set(file_names.get("layout", []))),
    "color": len(set(color_names)), "style": len(set(style_names)), "array": len(set(array_names)),
}
print("generated " + out_path + " -> " + ", ".join(f"{k}={v}" for k, v in counts.items()))
