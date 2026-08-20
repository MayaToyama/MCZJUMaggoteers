#!/usr/bin/env python3
"""迁移 waves.yml：affixes/infernal → skills（怪物技能系统 1.1）。

规则（作用于 step / passengers / on_death 任意层级 Map）：
  - 键重命名：affixes: [...] → skills: [...]
  - infernal 块删除；其 affixes 并入同 Map 的 skills（无 affixes 则整个丢弃）
  - 同一 Map 既有 affixes 又有 infernal.affixes → 合并【去重】为一个 skills 列表
    （如 affixes: [blinding] + infernal: {affixes: [blinding, wardenwrath, confusing]}
      → skills: [blinding, wardenwrath, confusing]，不产生 [blinding, blinding, ...]）

副作用：PyYAML dump 会丢失注释与手写格式。waves.yml 头部注释含 IM 说明，
本就要移除（Task 8 Step 5 会用新注释重写头部），接受。

用法：python3 scripts/migrate_mob_skills.py
"""
from __future__ import annotations

from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
WAVES = ROOT / "src/main/resources/waves.yml"

# 迁移后写入的头部注释（去 IM，介绍技能系统；PyYAML dump 丢注释，故这里重写）
HEADER = """# waveplan implementation (2026-07-26). Single source for strategies and pools.
# delay = step pre-wait seconds; coeff may include hp/dmg/speed/scale/follow_range
# delay = 相对【同 point】上一刷怪时刻的等待秒数（该点尚无事件则相对本轮起点）；不同 point 时间轴独立、可并行
# strategy.repeat = 整份 steps 再跑一轮（每轮各 point 时钟归零，轮起点=上一轮最晚时刻）
# strategy.关卡名称 = Chinese display name (chat begin tip + scoreboard)
# steps[].repeat = 单条 step 在同 point 时间轴上按 delay 再刷几次（默认 1）
# skills: mob_skills.yml 的怪物技能 id 列表（trigger+effect 全可配置；1.1 起替代旧 affixes/infernal）
"""


def _dedup(ids: list) -> list:
    """保持顺序去重（合并 affixes + infernal.affixes 可能重复）。"""
    out = []
    seen = set()
    for i in ids:
        if i not in seen:
            seen.add(i)
            out.append(i)
    return out


def transform(node):
    """递归：Map 内 affixes→skills；infernal 展开并入 skills。返回新结构。"""
    if isinstance(node, dict):
        out = {}
        skills = None
        for k, v in node.items():
            if k == "affixes":
                if isinstance(v, list):
                    skills = list(v)
                continue
            if k == "infernal":
                if isinstance(v, dict) and isinstance(v.get("affixes"), list):
                    merged = list(v["affixes"])
                    if skills:
                        merged = skills + merged
                    skills = _dedup(merged)
                continue
            out[k] = transform(v)
        if skills is not None:
            existing = out.get("skills")
            if existing is not None and isinstance(existing, list):
                for s in skills:
                    if s not in existing:
                        existing.append(s)
            else:
                out["skills"] = skills
        return out
    if isinstance(node, list):
        return [transform(x) for x in node]
    return node


def main() -> None:
    if not WAVES.exists():
        raise SystemExit("missing " + str(WAVES))
    with WAVES.open(encoding="utf-8") as f:
        data = yaml.safe_load(f) or {}
    migrated = transform(data)
    with WAVES.open("w", encoding="utf-8") as f:
        f.write(HEADER)
        yaml.safe_dump(migrated, f, sort_keys=False, allow_unicode=True)
    print("migrated " + str(WAVES))


if __name__ == "__main__":
    main()
