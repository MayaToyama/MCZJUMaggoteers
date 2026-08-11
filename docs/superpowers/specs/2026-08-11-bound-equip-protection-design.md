# BOUND_EQUIP Effect - Upgradeable Bound Chestplate (Protection)

> **Status:** Draft (approved in brainstorm; awaiting implementation plan)
> **Date:** 2026-08-11
> **Revision:** v1.0
> **Depends on:** `UPGRADE_LEVEL` stack (`EffectStacker` / `RewardService`); ItemCreator items; `RunItemGuardListener`
> **Related:** `2026-07-27-reward-collectibles-design.md` (levelled charms; this design does **not** use charms)

**Summary:** Add `Effect.BOUND_EQUIP`: upgradeable STAT reward with `PlayerState` as source of truth; derived view is a **bound chestplate**. First grant: Protection II leather chest (0 armor). Each upgrade: Protection +2 up to XVI. Material swaps at Protection 4/8/12/16 (gold/iron/diamond/netherite). Always 0 armor, unbreakable, Unbreaking X (10). Conflicting chestpiece destroyed; cannot unequip in-run.

---

## 1. Goals

### 1.1 Problems

- `UPGRADE_LEVEL` today upgrades potion amp / collectible skins; there is no path for upgradeable equipment (enchant + material) in an armor slot.
- `GRANT_ITEM` fits triggered consumables, not bound chestplate lifecycle.
- Authors need YAML-only content for a protection-scaling chest reward.

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| PlayerState truth | `level` 1-8 drives the view; resync restores if item lost |
| Bound chest | Occupies `CHEST`; cannot unequip/swap/drop/container in-run |
| Conflict | Non-matching chest piece **destroyed**, then bound piece equipped |
| Progression | See section 3; protection = `2 * level`; material breakpoints correct |
| Zero armor | All materials use `attributeModifiers` to zero chest armor |
| Appearance | `unbreakable: true` + `minecraft:unbreaking: 10` + matching `protection` |
| Draw pool | Repeatable until max via existing `RewardDrawVisibility` |
| GUI | No `collectibles.yml`; preview from `items_by_level` ItemCreator stacks |
| Zero-Java content | Same pattern = new STAT + new items (once slot is implemented) |

### 1.3 Out of scope (v1)

- Auto-hiding other chestplate rewards (**content convention** only; change draw logic later if needed)
- Runtime formula mutation of enchant/material (use 8 static ItemCreator items)
- Non-`CHEST` content packs (params keep `slot`; v1 implements `CHEST` only)
- `CollectibleService` rabbit-foot charms
- `held_effects` / `use_ability` paths (reward STAT permanent only)
- `trigger` / `expiry` (forbidden, like `AURA`)

---

## 2. Architecture

```
RewardOption (STAT, stack=UPGRADE_LEVEL, effect=BOUND_EQUIP)
        |
        v
RewardService.applyStat -> PlayerEffect (id, level, params)
        |
        v
EffectService.apply (merge) --> BoundEquipService.sync(player, pe)
                                      |
                                      +-- resolve items_by_level[level]
                                      +-- destroy conflicting CHEST item
                                      +-- equip + PDC (kind=bound_equip, reward_id, level)

resync / revive --> BoundEquipService.resync

RunItemGuard (+ bound rules) --> block unequip / swap / move / drop / container
```

| Data | Source | Notes |
|------|--------|-------|
| Upgrade level | `PlayerEffect.level` | Cap = `upgrade_max` (8 for this reward) |
| Item per level | `params.items_by_level` | ItemCreator ids; validated at load |
| Equipped view | Player chest slot | PDC-tagged; derived only |
| Lock | `RunItemGuardListener` (or sibling) | Recognize `kind=bound_equip` |

**Why a new Effect (not collectibles):** Equip target, destroy-on-conflict, and slot lock differ from backpack charms; `BOUND_EQUIP` keeps `CollectibleService` focused.

---

## 3. Progression table

| Reward level | Protection | Material | Armor |
|--------------|------------|----------|-------|
| 1 | II (2) | Leather | 0 |
| 2 | IV (4) | Gold | 0 |
| 3 | VI (6) | Gold | 0 |
| 4 | VIII (8) | Iron | 0 |
| 5 | X (10) | Iron | 0 |
| 6 | XII (12) | Diamond | 0 |
| 7 | XIV (14) | Diamond | 0 |
| 8 | XVI (16) | Netherite | 0 |

All levels: `unbreakable: true`, `minecraft:unbreaking: 10`, plus the protection enchant above.

---

## 4. Config schema

### 4.1 rewards.yml

```yaml
- id: prot_chest
  display: "<aqua>Protection +1"
  description: "<gray>Bound chest: Protection II to XVI, always 0 armor"
  category: STAT
  effect: BOUND_EQUIP
  params:
    slot: CHEST
    items_by_level:
      1: maggoteers:prot_chest_l1
      2: maggoteers:prot_chest_l2
      3: maggoteers:prot_chest_l3
      4: maggoteers:prot_chest_l4
      5: maggoteers:prot_chest_l5
      6: maggoteers:prot_chest_l6
      7: maggoteers:prot_chest_l7
      8: maggoteers:prot_chest_l8
  unique: true
  stack: UPGRADE_LEVEL
  upgrade_max: 8
```

Note: in-game MiniMessage display/description may use Chinese copy matching content style (`保护+1`, etc.) at implementation time.

**Rules:**

- Forbid `trigger` and `expiry`.
- Require `stack: UPGRADE_LEVEL` and `upgrade_max > 0`.
- `items_by_level` must contain every integer key in `1..upgrade_max`.
- Do **not** add a `collectibles.yml` entry for this id.
- Cap resolution unchanged: option `upgrade_max` -> pool -> config default.

### 4.2 items/*.yml (per level)

- `material`: leather / golden / iron / diamond / netherite chestplate
- `enchantments`: `minecraft:protection: N`, `minecraft:unbreaking: 10`
- `unbreakable: true`
- `attributeModifiers`: negate default chest armor to **0** for that material (same pattern as mob negative armor)
- Stable ItemCreator / PDC id

### 4.3 Load-time validation (`RewardLoadValidator` / params parser)

- `slot` must be implemented (v1: only `CHEST`)
- Every `items_by_level` value exists in `ItemService`
- Key set equals `1..upgrade_max`
- Reject `trigger` / `expiry` on `BOUND_EQUIP`

---

## 5. Lifecycle

### 5.1 First grant

1. Merge `PlayerEffect` at `level=1`.
2. `BoundEquipService.sync`: if chest occupied by non-matching bound piece -> **destroy**; equip level-1 item; set PDC (`kind=bound_equip`, `reward_id`, `reward_level`).
3. No collectible charm.

### 5.2 Upgrade

1. `EffectStacker` increments `level` (cap 8); no amp sync required (not a potion).
2. `sync` replaces chest with `items_by_level[newLevel]` for same `reward_id`.
3. At max level, option hidden by existing visibility rules.

### 5.3 Lock (bound)

While in Maggoteers run, block for `kind=bound_equip` items:

- Unequip from chest
- Swap with another chestplate
- Move to other inventory slots
- Drop / move into containers

Allow inspecting the item while it remains in the chest slot.

### 5.4 Death / revive / resync

Call `BoundEquipService.resync` alongside existing collectible resync points (`EffectService.resync`, death strategy).

If chest is missing the correct `(reward_id, level)` piece -> destroy conflicting chest content and re-equip.

### 5.5 Multiple BOUND_EQUIP on same slot

v1 content guarantees a single chest binding. If misconfigured, later sync wins (prior chest content destroyed per conflict rule).

### 5.6 Cleanup

World/inventory teardown at game end is sufficient; optional explicit unequip is not required for v1.

---

## 6. Code change surface

| Action | Piece |
|--------|-------|
| **Add** | `Effect.BOUND_EQUIP`; params keys; `BoundEquipService`; PDC kind; lock rules |
| **Wire** | `EffectService.apply` / `resync` after merge; GUI preview for `BOUND_EQUIP` without collectibles; load validator |
| **Touch** | `ItemKind` (or equivalent); `RunItemGuardListener` for in-inventory moves of bound gear |
| **Config** | One STAT option; eight item definitions |
| **Docs** | plugin-config `reference.md` Effect table row; optional `CLAUDE.md` Effect catalog |
| **Do not rewrite** | `UPGRADE_LEVEL` core; draw mutual exclusion; collectibles; `GRANT_ITEM` |

**Difficulty:** medium - additive Effect + equipment view; not a rewards engine rewrite.

---

## 7. Testing

- Load: missing level key / unknown item / bad slot / trigger present -> reject per project validator style
- Sync: empty chest equips; vanilla chest destroyed then bound; upgrade 1->2 material + protection correct
- Lock: unequip / swap / drop / container cancelled
- Resync after simulated loss: correct level restored
- Visibility: level < max visible; at max hidden
- Unit: params parse + level-to-item map; guard pure helpers where possible

---

## 8. Implementation notes

- `EffectStacker` UPGRADE_LEVEL amp++ is potion-specific; `BOUND_EQUIP` relies on `level` only for view sync - do not require amp in params.
- GUI preview level mirrors collectibles: unowned -> 1; owned upgrade path -> `min(level + 1, upgradeMax)`.
- Protection levels above IV are intentional and supported by modern Paper/Minecraft damage formulas.
- Pool placement (which act/weak/strong/boss) is a content choice at implementation time; not fixed by this design.
