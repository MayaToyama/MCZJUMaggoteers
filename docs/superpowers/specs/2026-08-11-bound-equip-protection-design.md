# BOUND_EQUIP Effect - Upgradeable Bound Chestplate (Protection)

> **Status:** Draft (review revisions incorporated; awaiting implementation plan)
> **Date:** 2026-08-11
> **Revision:** v1.1 — armor_toughness zeroing; stricter lock intercepts; explicit EffectService wiring; orphan strip on remove; GUI preview branch; load-time single CHEST BOUND_EQUIP; display wording
> **Depends on:** `UPGRADE_LEVEL` stack (`EffectStacker` / `RewardService`); ItemCreator items; `RunItemGuardListener`
> **Related:** `2026-07-27-reward-collectibles-design.md` (levelled charms; this design does **not** use charms)

**Summary:** Add `Effect.BOUND_EQUIP`: upgradeable STAT reward with `PlayerState` as source of truth; derived view is a **bound chestplate**. First grant: Protection II **leather** chest (0 armor). Each upgrade: Protection +2 up to XVI. **L1 stays leather;** material upgrades at Prot IV / VIII / XII / XVI to gold / iron / diamond / netherite. Always **0 armor** (and **0 toughness** where the material has any), unbreakable, Unbreaking X (10). Conflicting chestpiece destroyed; cannot unequip in-run.

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
| Bound chest | Occupies `CHEST`; cannot unequip/swap/drop/move-within-inventory/container in-run |
| Conflict | Non-matching chest piece **destroyed**, then bound piece equipped |
| Progression | See §3; protection = `2 * level`; material breakpoints correct |
| Zero defense stats | Armor (and toughness when present) fully cancelled via `attributeModifiers` — see §3 / §4.2 |
| Appearance | `unbreakable: true` + `minecraft:unbreaking: 10` + matching `protection` |
| Draw pool | Repeatable until max via existing `RewardDrawVisibility` (`unique: true` does **not** block upgrades — see §4.1) |
| GUI | No `collectibles.yml`; dedicated `BOUND_EQUIP` preview from `params.items_by_level` |
| Zero-Java content | Same pattern = new STAT + new items (once slot is implemented) |
| Orphan cleanup | When effect absent from PlayerState, strip matching `kind=bound_equip` items (chest + inventory) |

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
EffectService.apply (merge)
        |
        +-- explicit BoundEquipService.sync(player, pe)   // NOT via applyDerived
                                      |
                                      +-- resolve items_by_level[level]
                                      +-- destroy conflicting CHEST item
                                      +-- equip + PDC (kind=bound_equip, reward_id, level)

resync / revive --> BoundEquipService.resync (+ orphan strip for absent reward_ids)

effect removed / debug clear --> BoundEquipService.remove(reward_id)  // chest + inventory orphans

RunItemGuard (+ bound rules) --> block unequip / swap / move / drop / container
```

| Data | Source | Notes |
|------|--------|-------|
| Upgrade level | `PlayerEffect.level` | Cap = `upgrade_max` (8 for this reward) |
| Item per level | `params.items_by_level` | ItemCreator ids; validated at load |
| Equipped view | Player chest slot | PDC-tagged; derived only |
| Lock | Extend `RunItemGuardListener` (or sibling) | Must cover in-inventory moves — see §5.3 |

**Why a new Effect (not collectibles):** Equip target, destroy-on-conflict, and slot lock differ from backpack charms; `BOUND_EQUIP` keeps `CollectibleService` focused.

**Critical wiring:** `EffectService.applyDerived` only handles `ADD_ATTRIBUTE` / `ADD_POTION`; unknown effects are no-ops. **Do not** expect `applyDerived` to equip bound gear. Call `BoundEquipService.sync` explicitly after merge (same pattern as `CollectibleService.grant` after `RewardService.applyStat`, and/or a dedicated branch in `EffectService.apply` / `resync`). Documented again in §6 / §8.

---

## 3. Progression table

| Reward level | Protection | Material | Armor cancel | Toughness cancel |
|--------------|------------|----------|--------------|------------------|
| 1 | II (2) | Leather | armor `-3` | — |
| 2 | IV (4) | Gold | armor `-5` | — |
| 3 | VI (6) | Gold | armor `-5` | — |
| 4 | VIII (8) | Iron | armor `-6` | — |
| 5 | X (10) | Iron | armor `-6` | — |
| 6 | XII (12) | Diamond | armor `-8` | toughness `-2` |
| 7 | XIV (14) | Diamond | armor `-8` | toughness `-2` |
| 8 | XVI (16) | Netherite | armor `-8` | toughness `-1` |

Modifier amounts match existing mob chest templates in `items/maggoteers.yml` (`mob_gold_chest`, `mob_iron_chest_ub10`, `mob_diamond_chest`, `mob_netherite_chest`). Leather has no mob chest sample; use vanilla leather chestplate armor **3** → `armor: -3`.

All levels: `unbreakable: true`, `minecraft:unbreaking: 10`, plus the protection enchant above. Operation: `ADD_NUMBER`, slot: `chest`.

---

## 4. Config schema

### 4.1 rewards.yml

```yaml
- id: prot_chest
  display: "<aqua>保护胸甲"
  description: "<gray>绑定胸甲：保护 II→XVI（每升 1 阶 +2），全程 0 护甲/韧性"
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

**Display wording:** Avoid `"Protection +1"` / `"保护+1"` alone — that reads like enchant level +1, but L1 is Protection **II**. Prefer **"保护胸甲"** / **"保护胸甲 Lv.n"** (GUI can show next level via preview). Description may say "每升 1 阶保护 +2".

**`unique: true` vs `UPGRADE_LEVEL`:** Both may coexist. Draw visibility is decided by `RewardDrawVisibility` (STAT owned + `UPGRADE_LEVEL` and `level < cap` → still visible). `unique: true` is a **content convention** for authors / collectible-style docs; it does **not** by itself block re-draws for upgrades.

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
- `attributeModifiers` (must fully cancel default defense for that material):
  - **L1–L5:** `minecraft:armor` only (leather `-3`, gold `-5`, iron `-6`)
  - **L6–L8:** `minecraft:armor` **and** `minecraft:armor_toughness` (diamond `-8` / `-2`; netherite `-8` / `-1` — same as mob templates)
- Stable ItemCreator / PDC id

### 4.3 Load-time validation (`RewardLoadValidator` / params parser)

- `slot` must be implemented (v1: only `CHEST`)
- Every `items_by_level` value exists in `ItemService`
- Key set equals `1..upgrade_max`
- Reject `trigger` / `expiry` on `BOUND_EQUIP`
- **Single CHEST binding:** across all loaded reward options (all pools), at most **one** `BOUND_EQUIP` with `slot: CHEST`. Fail load (or hard warn-fail) on duplicates so misconfig cannot silently destroy another binding at runtime. (§5.5 is no longer the primary safeguard.)

---

## 5. Lifecycle

### 5.1 First grant

1. Merge `PlayerEffect` at `level=1`.
2. Explicit `BoundEquipService.sync`: if chest occupied by non-matching piece -> **destroy**; equip level-1 item; set PDC (`kind=bound_equip`, `reward_id`, `reward_level`).
3. No collectible charm.

### 5.2 Upgrade

1. `EffectStacker` increments `level` (cap 8); no amp sync required (not a potion).
2. `sync` replaces chest with `items_by_level[newLevel]` for same `reward_id`.
3. At max level, option hidden by existing visibility rules.

### 5.3 Lock (bound) — intercept points

Existing `RunItemGuardListener` only blocks drop, shift-to-other-inventory, and placing run items into foreign inventories. **Bound chest rules are stricter** and must be implemented explicitly (extend the listener or add a sibling). Bound items may already be `isRunItem` via ItemCreator id, but **in-player-inventory moves are still allowed today** — that must change for `kind=bound_equip`.

While in Maggoteers run, for `kind=bound_equip` (and/or the chest equipment slot holding it), **cancel**:

| Surface | Examples to cover |
|---------|-------------------|
| `InventoryClickEvent` | `PICKUP_*` from chest slot; `PLACE_*` / `SWAP_WITH_CURSOR` involving chest or bound stack; number-key / `HOTBAR_*` swaps; shift-clicks that would move the bound piece |
| `InventoryDragEvent` | Any drag whose raw slots include the chest armor slot, or that moves a bound stack |
| Existing drop / container guards | Keep; bound pieces remain covered |
| Optional (future non-CHEST) | `PlayerSwapHandItemsEvent` if offhand/mainhand bindings appear |

Allow inspecting (open inventory, hover) while the item remains in the chest slot.

Implementation / test checklist must list these actions; "cannot unequip" is otherwise easy to miss.

### 5.4 Death / revive / resync

Call `BoundEquipService.resync` alongside existing collectible resync points (`EffectService.resync`, death strategy).

If chest is missing the correct `(reward_id, level)` piece -> destroy conflicting chest content and re-equip.

Also strip **orphan** bound pieces (any inventory slot) whose `reward_id` is not present as a `BOUND_EQUIP` in `PlayerState` — mirror `CollectibleService.resync` orphan cleanup.

### 5.5 Multiple BOUND_EQUIP on same slot

**Primary control:** load validator (§4.3) — at most one `slot: CHEST` `BOUND_EQUIP` in all rewards. Runtime "later sync wins" is only an emergency fallback if validation is bypassed in tests; do not rely on it for content safety.

### 5.6 Effect removed / orphan strip

`BOUND_EQUIP` has no `expiry`, but debug commands, future revoke paths, or state edits may remove the `PlayerEffect`. When `PlayerState` no longer contains a `BOUND_EQUIP` for `reward_id`:

- Remove all items with `kind=bound_equip` and matching `reward_id` from **chest and full inventory** (including orphans accidentally left in backpack if a lock bug slipped).
- Hook points: any path that removes effects (e.g. alongside `CollectibleService.remove` after `sweepExpiry` / explicit clear), plus `resync` orphan pass.

### 5.7 Cleanup

World/inventory teardown at game end is sufficient for normal end-of-run; §5.6 covers mid-run effect removal.

---

## 6. Code change surface

| Action | Piece |
|--------|-------|
| **Add** | `Effect.BOUND_EQUIP`; params keys; `BoundEquipService` (sync / resync / remove / orphan strip); PDC kind; lock rules |
| **Wire (explicit)** | After `EffectService.apply` merge for `BOUND_EQUIP`, call `BoundEquipService.sync` — **not** `applyDerived`. In `EffectService.resync` (and death revive), call `BoundEquipService.resync`. On effect removal, call `BoundEquipService.remove`. |
| **GUI** | `RewardOptionIcons`: STAT branch must detect `BOUND_EQUIP` and preview via `items_by_level` (do **not** only call `CollectibleService.preview`) |
| **Touch** | `ItemKind` (or equivalent); **extend** `RunItemGuardListener` (or sibling) with §5.3 intercepts |
| **Validate** | `RewardLoadValidator`: schema + single global `CHEST` `BOUND_EQUIP` |
| **Config** | One STAT option; eight item definitions with armor/(toughness) cancels per §3 |
| **Docs** | plugin-config `reference.md` Effect table row; optional `CLAUDE.md` Effect catalog |
| **Do not rewrite** | `UPGRADE_LEVEL` core; draw mutual exclusion for other chestplates; collectibles; `GRANT_ITEM` |

**Difficulty:** medium - additive Effect + equipment view + stricter inventory guards; not a rewards engine rewrite.

---

## 7. Testing

- Load: missing level key / unknown item / bad slot / trigger present -> reject; **two CHEST `BOUND_EQUIP` options -> reject**
- Sync: empty chest equips; vanilla chest destroyed then bound; upgrade 1->2 material + protection correct
- Stats: L6+ items expose cancelled toughness (no leftover toughness from diamond/netherite defaults)
- Lock: unequip, cursor swap, hotbar number swap, drag from chest, drop, container — all cancelled
- Resync after simulated loss: correct level restored; orphan backpack bound piece removed when effect absent
- Effect remove: bound chest (and orphans) stripped
- Visibility: level < max visible; at max hidden; upgrades still drawable despite `unique: true`
- GUI: `RewardOptionIcons` returns correct next-level chest preview without collectibles mapping
- Unit: params parse + level-to-item map; guard helpers for click/drag classification where possible

---

## 8. Implementation notes

- `EffectStacker` UPGRADE_LEVEL amp++ is potion-specific; `BOUND_EQUIP` relies on `level` only for view sync - do not require amp in params.
- **Never** route equip through `applyDerived`; wire `BoundEquipService` explicitly (§2 / §6).
- GUI preview level mirrors collectibles: unowned -> 1; owned upgrade path -> `min(level + 1, upgradeMax)`; resolve item id from `opt.params()` / stored effect params `items_by_level`.
- Protection levels above IV are intentional and supported by modern Paper/Minecraft damage formulas.
- Netherite toughness cancel follows **mob template** (`-1`), not necessarily vanilla full toughness 3 — stay consistent with `mob_netherite_chest` unless a separate balance decision changes both.
- Pool placement (which act/weak/strong/boss) is a content choice at implementation time; not fixed by this design.
- `PlayerSwapHandItemsEvent` is optional for v1 CHEST-only; include when non-chest slots are implemented.
