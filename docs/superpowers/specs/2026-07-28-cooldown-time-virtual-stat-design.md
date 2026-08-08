# COOLDOWN_TIME Virtual Stat Design

> **Status:** Approved (v1.1 post-review) — ready for implementation plan  
> **Date:** 2026-07-28  
> **Revision:** v1.1 — visual CD fix, API signature, validation tests, YAML encoding, TestBlade removal  
> **Depends on:** `2026-07-27-config-magic-abilities-design.md` (virtual stats, `PlayerCombatStats`, config magic CD)

**Summary:** Add virtual stat `COOLDOWN_TIME` (initial multiplier **1.0**). Config magic weapons gate on `PlayerCombatStats.effectiveCooldownSec(game, uuid, baseSec)` which returns `int` seconds. Layer stacking is **multiplicative** (`Pi(1 + value_i)`), not additive like `MAGIC_DAMAGE`. Supports negative `value` (e.g. `-0.10` -> x0.9). **No multiplier floor**; resulting seconds clamped to **at least 1** after rounding.

---

## 1. Goals

### 1.1 Problem

- Magic weapon CD uses raw `use_ability.cooldown_sec`; no stat modifies wait time.
- `MAGIC_DAMAGE` uses `1 + sum(value)`; cooldown reduction needs **multiplicative** stacking (two -10% -> x0.81, not -20% additive).
- Visual CD can diverge from logic CD when items carry ItemCreator `useCooldown` component (see section 6).

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| Virtual stat | `COOLDOWN_TIME` in `VirtualStats`; YAML `attr: COOLDOWN_TIME` via existing `putAttr` / `GameRegistries.isVirtualAttribute` (no parser change beyond enum) |
| Baseline | No matching effects -> multiplier **1.0** |
| Negative values | `value: -0.10` -> layer factor **0.90** |
| Multiplicative stack | Two -10% layers -> **0.9 x 0.9 = 0.81** |
| Deep stacking | No multiplier cap; `0.9^22 ~= 0.099` |
| Logic + visual CD | Same **effectiveSec** for PDC gate and hotbar cooldown bar |
| Validator | Reject `COOLDOWN_TIME` unless `op: PERCENT` (+ load-validation tests) |
| Public API | Handlers call **`effectiveCooldownSec` only**; multiplier helper is package-private |

### 1.3 Out of scope (v1)

- `PlayerEffect.cooldownSec` on reward WEAPON (not wired to interact)
- Non-magic items (currency, revive coin, menus)
- Sub-second CD (integer seconds; `max(1, round(...))` is post-multiply guard only)
- Bukkit attribute mirror
- `config.yml` multiplier floor
- **`stack: UPGRADE_LEVEL` for COOLDOWN_TIME** (not supported v1; use `stack: ADD` — see section 3.3)
- Legacy Java handlers (`TestBladeHandler` etc. were deleted; magic weapons use `ConfigMagicHandler` only)

---

## 2. Architecture

```
rewards.yml STAT (ADD_ATTRIBUTE, attr: COOLDOWN_TIME, op: PERCENT)
        |
        v
PlayerState.effects()
        |
        v
PlayerCombatStats.effectiveCooldownSec(game, uuid, baseSec)  --> int
        |   (internal: cooldownTimeMultiplierFromEffects -> double, unclamped)
        |
        v
ConfigMagicHandler (and any future registerHandler escape hatch)
        |
        v
CooldownService.tryUse(player, stack, pdcId, effectiveSec)
        --> applyVisualUseCooldown(player, stack, effectiveSec)   // same seconds
```

| Data | Source | Notes |
|------|--------|-------|
| Base CD | `items/*.yml` `use_ability.cooldown_sec` | Never mutated at runtime |
| Multiplier | `PlayerState.effects()` | Recomputed each use; **not clamped** |
| Effective sec | `PlayerCombatStats.effectiveCooldownSec` | **`int`**, `max(1, (int) Math.round(base * mult))` |
| Logic CD | `CooldownService` timestamp table | Input is always **effective** sec |
| Visual CD | `applyVisualUseCooldown` | **Must use effectiveSec**, not component base seconds |

**Contrast with `MAGIC_DAMAGE`:**

| | `MAGIC_DAMAGE` | `COOLDOWN_TIME` |
|--|----------------|-----------------|
| Aggregation | `1 + sum(value)` | `Pi (1 + value)` |
| Apply point | `EffectService` damage | Pre-`CooldownService.tryUse` |
| Default | 1.0 | 1.0 |

---

## 3. Formula

### 3.1 Per-layer factor

For each `PlayerEffect` where `effect == ADD_ATTRIBUTE`, `VirtualStats.isCooldownTime(attrName)`, `op == PERCENT`:

```
layerFactor = 1.0 + value
```

### 3.2 Multiplier vs effective seconds

```
multiplier = product(layerFactor_i)     // no effects -> 1.0; NO floor on multiplier
effectiveSec = max(1, (int) Math.round(baseSec * multiplier))
```

- **Multiplier:** unclamped (pathological `multiplier <= 0` should not pass load; runtime defensive min 0.01 only if needed).
- **Seconds:** clamped to **>= 1** so `CooldownService` int API never receives 0.

**Examples** (base = 20):

| Effects | Multiplier | effectiveSec |
|---------|------------|--------------|
| none | 1.0 | 20 |
| one -10% | 0.9 | 18 |
| two -10% (ADD) | 0.81 | 16 |
| 22x -10% | ~= 0.099 | 2 |

### 3.3 Stack strategies (v1)

| stack | COOLDOWN_TIME v1 |
|-------|------------------|
| **ADD** | **Recommended.** Each acquired layer is a separate `PlayerEffect`; factors multiply. |
| **UPGRADE_LEVEL** | **Not supported v1.** Do not author UPGRADE_LEVEL COOLDOWN_TIME rewards until a follow-up spec defines per-level value (unlike amp-style potions, multiplicative semantics are ambiguous). Validator may warn if detected. |
| **IGNORE** | Default for duplicate triggered passives. |

`MAGIC_DAMAGE` in production uses **ADD** for stacking (`a2s_magic_dmg`); same pattern for CD haste.

---

## 4. Config schema

### 4.1 YAML parsing (no new parser work)

`RewardOption.putAttr` stores raw name in `ATTR_NAME`. `GameRegistries.isVirtualAttribute(name)` delegates to `VirtualStats.parse`. Adding `COOLDOWN_TIME` to the enum is sufficient for `attr: COOLDOWN_TIME` in rewards.

### 4.2 Reward STAT example

```yaml
- id: a1s_cd_haste
  category: STAT
  effect: ADD_ATTRIBUTE
  params: { attr: COOLDOWN_TIME, op: PERCENT, value: -0.10 }
  unique: true
  stack: ADD
  display: "<aqua>\u51b7\u5374\u65f6\u95f4 -10%"
  description: "\u9b54\u6cd5\u7269\u54c1\u51b7\u5374 \u00d70.9\uff08\u4e58\u7b97\u53e0\u5c42\uff09"
```

(Display/description may use literal Chinese in repo UTF-8 files; above uses escapes for tooling-safe copy.)

### 4.3 Load validation

| Case | Result |
|------|--------|
| `COOLDOWN_TIME` + `op: PERCENT` | Allow |
| `COOLDOWN_TIME` + `op: FLAT` | Warn + skip |
| `COOLDOWN_TIME` + omitted `op` (defaults FLAT) | Warn + skip |
| `COOLDOWN_TIME` + `stack: UPGRADE_LEVEL` | Warn (unsupported v1); still load if PERCENT ok |

Mirror existing `validateMagicDamagePercent` pattern in `RewardLoadValidator`.

---

## 5. Code touchpoints

| File | Change |
|------|--------|
| `VirtualStats` | `COOLDOWN_TIME`, `isCooldownTime(String)` |
| `PlayerCombatStats` | Package-private `cooldownTimeMultiplierFromEffects`; **`public static int effectiveCooldownSec(MaggoteersGame, UUID, int baseSec)`** |
| `RewardLoadValidator` | PERCENT-only for `COOLDOWN_TIME`; optional UPGRADE_LEVEL warn |
| `CooldownService.applyVisualUseCooldown` | **Behavior change:** use **`effectiveSec`** for tick duration; read `useCooldown` only for **`cooldownGroup` key**, ignore component `seconds()` |
| `ConfigMagicHandler` | `effectiveSec = PlayerCombatStats.effectiveCooldownSec(mg, uuid, ability.cooldownSec())` before `tryUse` |
| `ItemInteractRouter` javadoc | Note: custom `registerHandler` handlers must call `effectiveCooldownSec` themselves if they use CD |

**Removed from scope:** `TestBladeHandler` — deleted in config-magic migration (`dev-log` 2026-07-27); `maggoteers:test_blade` uses `use_ability` + `ConfigMagicHandler`.

---

## 6. Visual CD alignment (critical)

### 6.1 Problem

Current `applyVisualUseCooldown(player, stack, fallbackSec)` prefers `UseCooldown.seconds()` from the item component when present. ItemCreator weapons set `useCooldown` with **base** seconds. Logic CD would use **effectiveSec** while the bar shows **base** -> mismatch.

### 6.2 Required behavior (v1.1)

```java
/** Apply hotbar cooldown for effectiveSec seconds.
 *  If stack has useCooldown.cooldownGroup(), apply to that group key.
 *  Do NOT use UseCooldown.seconds() when effectiveSec is provided. */
public static void applyVisualUseCooldown(Player player, ItemStack stack, int effectiveSec)
```

Algorithm:

1. If `effectiveSec <= 0` -> return (unchanged).
2. `ticks = max(1, effectiveSec * 20)`.
3. If `UseCooldown` component exists and `cooldownGroup() != null` -> `player.setCooldown(group, ticks)`.
4. Else -> `player.setCooldown(stack, ticks)`.

Component **base seconds** are authoring reference only; runtime bar always reflects **effective** wait.

### 6.3 Item authoring

Keep `useCooldown` on items for **group identity** (per-weapon CD groups). Base seconds in ItemCreator should match `use_ability.cooldown_sec` for editor preview consistency; runtime overrides duration.

---

## 7. Runtime behavior

- Multiplier evaluated **at each use attempt**; active CD not retroactively adjusted.
- No `PlayerState` / not in game -> `effectiveCooldownSec` returns `max(1, baseSec)` (multiplier 1.0).
- `ConfigMagicHandler` is the sole magic-weapon CD path in v1.

---

## 8. Testing

### 8.1 `PlayerCombatStatsTest`

- Empty -> mult 1.0, effective 20 from base 20
- One -10% -> 18
- Two ADD -10% -> 16 (0.81)
- Ignore FLAT / wrong attr
- Optional: 22 layers ~= 0.099 mult

### 8.2 `RewardLoadValidationTest`

- `COOLDOWN_TIME` + PERCENT -> passes validation
- `COOLDOWN_TIME` + FLAT -> skip reason present
- `COOLDOWN_TIME` + null op -> skip (FLAT default)

### 8.3 `CooldownServiceTest` (optional extend)

- Mock/visual path: when component has group, ticks derived from **passed effectiveSec**, not component seconds

### 8.4 Manual

- `field_shield` base 20s, one -10% reward -> ~18s bar and re-use gate

---

## 9. Review checklist

- [x] Visual CD uses effectiveSec (section 6)
- [x] TestBladeHandler removed; ConfigMagicHandler only
- [x] UPGRADE_LEVEL explicitly unsupported v1
- [x] `effectiveCooldownSec` returns `int` with rounding documented
- [x] Load-validation tests listed
- [x] YAML example encoding fixed
- [x] Multiplier unclamped vs seconds `max(1, ...)` explicit
- [x] Public API = `effectiveCooldownSec` only

---

## Appendix

- Stat id: `COOLDOWN_TIME`
- Positive `value` lengthens CD; negative shortens
- Future: UPGRADE_LEVEL semantics need dedicated spec if ever required