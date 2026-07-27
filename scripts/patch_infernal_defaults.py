#!/usr/bin/env python3
from __future__ import annotations
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PATH = ROOT / "src/main/resources/waves.yml"

INLINE_TYPES = {
    "HUSK": ("quicksand", 1),
    "SLIME": ("quicksand", 1),
    "ELDER_GUARDIAN": ("teleport", 3),
}

def insert_inline_infernal(line, skill, level):
    block = f"infernal: {{ level: {level}, affixes: [{skill}] }}"
    if block in line:
        return line
    if ", delay:" in line:
        return line.replace(", delay:", f", {block}, delay:", 1)
    if line.rstrip().endswith("}"):
        core = line.rstrip()
        return core[:-1] + f", {block} }}" + line[len(core):]
    return line

def patch_inline_line(line):
    if "infernal:" in line:
        return line
    for entity, (skill, level) in INLINE_TYPES.items():
        if re.search(rf"\btype:\s*{entity}\b", line):
            return insert_inline_infernal(line, skill, level)
    return line

def block_has_infernal(lines, type_idx):
    base = len(lines[type_idx]) - len(lines[type_idx].lstrip())
    for j in range(type_idx + 1, len(lines)):
        stripped = lines[j].strip()
        if not stripped:
            continue
        indent = len(lines[j]) - len(lines[j].lstrip())
        if indent <= base and stripped.startswith("- "):
            break
        if stripped.startswith("infernal:"):
            return True
    return False

def insert_block_infernal(lines, type_idx, skill, level):
    if block_has_infernal(lines, type_idx):
        return lines
    base = lines[type_idx][: len(lines[type_idx]) - len(lines[type_idx].lstrip())]
    insert_at = type_idx + 1
    while insert_at < len(lines):
        stripped = lines[insert_at].strip()
        if not stripped:
            insert_at += 1
            continue
        indent = len(lines[insert_at]) - len(lines[insert_at].lstrip())
        if indent <= len(base) and stripped.startswith("- "):
            break
        if stripped.split(":")[0] in {"delay", "on_death", "equipment", "passengers", "infernal"}:
            break
        insert_at += 1
    block = [
        f"{base}infernal:\n",
        f"{base}  level: {level}\n",
        f"{base}  affixes: [{skill}]\n",
    ]
    return lines[:insert_at] + block + lines[insert_at:]

def patch_content(text):
    lines = text.splitlines(keepends=True)
    for i, line in enumerate(lines):
        if line.strip().startswith("- {") and "type:" in line:
            lines[i] = patch_inline_line(line)
    for i in range(len(lines) - 1, -1, -1):
        m = re.match(r"^(\s+)type:\s*(HUSK|SLIME|ELDER_GUARDIAN)\s*$", lines[i])
        if m:
            skill, level = INLINE_TYPES[m.group(2)]
            lines = insert_block_infernal(lines, i, skill, level)
    return "".join(lines)

def patch_file(path=DEFAULT_PATH):
    original = path.read_text(encoding="utf-8")
    updated = patch_content(original)
    if updated != original:
        path.write_text(updated, encoding="utf-8", newline="\n")
        print(f"Patched infernal defaults in {path}")
    else:
        print(f"No infernal changes needed in {path}")

if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_PATH
    patch_file(target)