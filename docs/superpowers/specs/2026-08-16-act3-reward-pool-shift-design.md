# Act3 奖励池前移 + 彩蛋 Boss 池

**日期：** 2026-08-16  
**状态：** 已定案（对话确认）

## 目标

- Act3 普通商店任意波次只抽 **`act3_strong`**（当前强池内容）。
- 现 `act3_weak` 彩蛋/情怀物品迁入 **`act3_boss`**，仅在清掉 Act3 唯一 weak（奖励关）之后，Boss 商店才切到该池。
- 击败 Act2 Boss 后的首个休整：Boss 商店仍为 **`act2_boss`**。

## YAML（`rewards.yml`）

操作顺序（不可颠倒）：

1. `act3_boss.options` ← 旧 `act3_weak.options`（丢弃旧 boss 选项；保留 `cost: 1` / `currency: boss`）
2. `act3_weak.options` ← 复制 `act3_strong.options`（含 `upgrade_level_cap` 一并对齐；`act3_strong` 本身不变）

`act3_weak` 池名保留但 Act3 普通商店不再引用（可接受重复配置）。

## 运行时

| 入口 | 规则 |
|------|------|
| 普通商店 | Act1/2 不变；**Act3（actIndex=2）一律 `act3_strong`** |
| Boss 粘滞 | 击杀 Boss 仍更新 `lastBossKillAct`；**额外：Act3 清 weak 波也更新为当前层** → 此后 Boss 商店为 `act3_boss` |

## 非目标

- 不删 `act3_weak` 池键。
- 不改 Act1/2 池选择。
- 不改解锁商店派生逻辑（仍扫全部 `requires_unlock`）。
