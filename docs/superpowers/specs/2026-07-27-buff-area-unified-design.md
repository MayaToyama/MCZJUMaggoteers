# BUFF_AREA Unified Design

> **Status:** Ready for implementation plan  
> **Date:** 2026-07-27  
> **Depends on:** `2026-07-27-config-magic-abilities-design.md` (shared `executeMagicEffect`, FX presets, targets vocabulary)

**Summary:** Add `Effect.BUFF_AREA` — a shared magic effect that applies timed vanilla potions to self / allies / enemies. Same execution core for weapon right-click (`use_ability`) and reward STAT combat triggers (`ON_KILL` / `ON_DAMAGE_DEALT` / `ON_DAMAGE_TAKEN`). FX (caster ring + optional mark on hit target / attacker) is config-driven per entry.

---

## 1. Goals

### 1.1 Problems

- Weapon `use_ability` only supports `DAMAGE_AREA` / `DAMAGE_BEAM` / `HEAL_AREA` — cannot apply potion buffs/debuffs.
- Reward STAT can only potion-buff **self** via `ADD_POTION` (permanent or fireTrigger → self only).
- No config path for: right-click team Resistance, kill → team buff, hit → mark + debuff enemy, take damage → self Absorption.

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| Zero-Java weapons | New buff staff = `items/*.yml` `use_ability` only |
| Shared core | Weapon + STAT use one `BUFF_AREA` implementation |
| Trigger separation | Right-click via `use_ability`; combat via `rewards.yml` `trigger:` |
| Configurable FX | Caster `fx` + optional `mark_fx` / `mark_on` independently for weapon and STAT |
| Potion merge | Amp/duration rules (see §5) |

### 1.3 Out of scope (v1)

- `targets: both` (buff allies + debuff enemies in one cast) — defer to v1.1
- `targets: hit_target` as a target selector for potion apply (v1 uses `targets` for who gets potions; `mark_on` only for FX mark)
- Writing timed buffs into `PlayerState` (Bukkit potions only)
- STAT `fireTrigger: ON_INTERACT` (weapon `use_ability` covers interact; keep validator ban)
- Permanent / aura-style carrier (use existing `AURA` reward)
- Per-potion FX (one caster fx + one mark fx per ability fire)

---

## 2. Architecture

```
Effect.BUFF_AREA  (execution body: potions[] + targets + radius + FX)
        ▲
        │
   ┌────┴────────────────────────────┐
   │                                 │
use_ability (weapon)          PlayerEffect fireTrigger (STAT)
ConfigMagicHandler            EffectService.executeEffect
→ executeAbility              → executeMagicEffect
FX: use_ability.fx            FX: params.fx
    + optional mark_fx            + optional mark_fx / mark_on
```

| Data | Source | Notes |
|------|--------|-------|
| Weapon ability | `ItemAbilityRegistry` by item id | Not in PlayerState |
| STAT effect | `PlayerState.effects()` | Existing `PlayerEffect` model |
| Spell power | N/A for BUFF_AREA | Does **not** apply `MAGIC_DAMAGE` multiplier |

---

## 3. Trigger boundaries

### 3.1 Weapons (main-hand right-click)

- Semantics = interact; **no** `trigger:` field on items.
- Requires: main hand, `use_ability`, alive run participant.
- CD: `use_ability.cooldown_sec` via `CooldownService` (unchanged).

### 3.2 Reward STAT — allowed `fireTrigger` for `BUFF_AREA`

| Value | When | `mark_on` useful |
|-------|------|------------------|
| `ON_KILL` | Killer kills entity | `hit_target` = dead entity |
| `ON_DAMAGE_DEALT` | Player damages entity | `hit_target` = damaged entity |
| `ON_DAMAGE_TAKEN` | Player takes damage | `attacker` if LivingEntity damager |

**Forbidden as fireTrigger (unchanged whitelist + BUFF_AREA-specific):**

- `ON_INTERACT` — use `use_ability` instead
- Lifecycle / tick: `ON_WAVE_CLEAR`, `ON_ACT_ENTER`, `ON_GAME_END`, `ON_REVIVE`, `ON_DEATH`, `ON_TICK_1S`

**Load rules for `effect: BUFF_AREA`:**

- Must have `fireTrigger` when loaded from `rewards.yml` (no permanent BUFF_AREA STAT).
- Weapon path has no fireTrigger (instant cast).

`RewardLoadValidator` / `ItemAbilityRegistry`: reject invalid targets / empty potions / unknown potion / duration_ticks ≤ 0 with warn+skip.

### 3.3 Combat listener change (required)

Today `EffectListener` calls game-wide `fireTrigger(game, ON_KILL)` without entity context. For `mark_on` and correct ownership:

- Switch combat fires to **`fireTriggerPlayer(game, player, trigger, TriggerContext)`** for the involved player only:
  - Kill → killer
  - Damage dealt → damager player
  - Damage taken → damaged player
- Pass `TriggerContext(fired, hitTarget, attacker)`.

This also fixes pre-existing over-firing (all teammates' `ON_DAMAGE_DEALT` effects when one player hits).

---

## 4. Schema

### 4.1 Shared `params` (weapon `use_ability.params` and STAT `params`)

```yaml
params:
  radius: 6.0                 # ignored when targets: self
  targets: allies             # self | allies | enemies
  include_self: true          # allies only; default true
  enemy_scope: tracked        # enemies only; tracked | all_living (same as DAMAGE_AREA)
  potions:
    - { potion: RESISTANCE, amp: 0, duration_ticks: 200 }
    - { potion: REGENERATION, amp: 0, duration_ticks: 100 }
  fx:                         # optional caster-centered FX
    preset: RIPPLE_RINGS
    radius: 6.0
    particle: HAPPY_VILLAGER
    sound: ENTITY_PLAYER_LEVELUP
  mark_fx:                    # optional entity mark FX
    preset: DOT_ABOVE
    particle: SOUL_FIRE_FLAME
  mark_on: hit_target         # none | hit_target | attacker (default none)
```

| Field | Notes |
|-------|-------|
| `potions[]` | ≥1 required; potion name via `GameRegistries.potionEffect` |
| `duration_ticks` | Must be > 0 |
| `amp` | Potion amplifier (0 = level I) |
| `targets: self` | Only caster; ignore radius |
| `targets: allies` | `AllyTargeting.alliesInRadius` |
| `targets: enemies` | Same resolution as `DAMAGE_AREA` (`TargetResolver` / enemy_scope) |
| `fx` | Same schema as `use_ability.fx` / AURA fx (`MagicFxBuilder`) |
| `mark_fx` | Same schema; played on marked entity location |
| `mark_on` | Who gets `mark_fx`; ignored if no context or entity null |

### 4.2 Weapon YAML

```yaml
maggoteers:buff_staff:
  material: STICK
  pdc:
    - key: maggoteers:id
      type: STRING
      value: maggoteers:buff_staff
  use_ability:
    cooldown_sec: 20
    effect: BUFF_AREA
    params:
      radius: 6.0
      targets: allies
      include_self: true
      potions:
        - { potion: RESISTANCE, amp: 0, duration_ticks: 200 }
    fx:
      preset: RIPPLE_RINGS
      radius: 6.0
      particle: HAPPY_VILLAGER
```

Weapon FX: prefer top-level `use_ability.fx` (existing merge). If also `params.fx`, **`use_ability.fx` wins** for caster FX (same override idea as ability-level fx today). `params.mark_fx` / `mark_on` still read from params.

### 4.3 STAT reward YAML

```yaml
- id: a1s_kill_team_resist
  category: STAT
  unique: true
  trigger: ON_KILL
  effect: BUFF_AREA
  params:
    radius: 8.0
    targets: allies
    include_self: true
    potions:
      - { potion: RESISTANCE, amp: 0, duration_ticks: 160 }
    fx:
      preset: SPIRAL_RADIUS
      radius: 8.0
      particle: TOTEM_OF_UNDYING
    mark_fx:
      preset: DOT_ABOVE
      particle: SOUL_FIRE_FLAME
    mark_on: hit_target
  display: "<aqua>击杀：团队抗性"
```

STAT caster FX lives in `params.fx` (rewards already parse nested fx via `MagicFxBuilder`).

---

## 5. Potion merge rules

When applying potion type `T` with amp `A` and duration `D` to an entity that already has `T`:

1. Existing amp **>** A → **ignore** new buff  
2. Existing amp **==** A → keep the one with **longer remaining duration** (compare remaining ticks vs D)  
3. Existing amp **<** A → **replace** with new buff  

Implement as pure `PotionMerge.shouldApply(existingAmp, existingRemainingTicks, newAmp, newDurationTicks) -> boolean` (+ apply helper). Unit-test all three cases and “no existing → apply”.

Use `LivingEntity.addPotionEffect(..., true)` only when merge says apply (force replace when winning).

---

## 6. Runtime

### 6.1 `TriggerContext`

```java
public record TriggerContext(
    Trigger fired,
    LivingEntity hitTarget,   // ON_DAMAGE_DEALT / ON_KILL victim
    LivingEntity attacker     // ON_DAMAGE_TAKEN damager if LivingEntity
) {
    public static TriggerContext empty(Trigger fired) {
        return new TriggerContext(fired, null, null);
    }
}
```

### 6.2 Execution

```
Weapon:
  CooldownService.tryUse → play use_ability.fx → executeAbility
    → executeMagicEffect(BUFF_AREA, params, weaponFx, TriggerContext.empty(null))

STAT:
  fireTriggerPlayer(..., ctx)
    → executeEffect → executeMagicEffect(BUFF_AREA, params, null, ctx)
        → resolve caster FX from params.fx (if present) → MagicFxPresets.play(caster, fx)
        → resolve targets by params.targets
        → for each target × each potion: PotionMerge + addPotionEffect
        → if mark_on + mark_fx + entity present: MagicFxPresets on entity
```

`executeEffect` switch adds:

```java
case BUFF_AREA -> executeMagicEffect(..., e.params(), null, triggerCtx);
```

Extend `executeMagicEffect` / `executeEffect` signatures to accept optional `TriggerContext` (default empty for effects that ignore it).

### 6.3 Mark resolution

| `mark_on` | Entity |
|-----------|--------|
| `none` / omit | No mark |
| `hit_target` | `ctx.hitTarget()` |
| `attacker` | `ctx.attacker()` |

Missing entity → skip mark silently (no warn spam).

### 6.4 Not PlayerState

Timed potions are Bukkit-only. Expiry is vanilla. No charm / collectible interaction beyond existing STAT grant if the option is mapped.

---

## 7. Validation

### 7.1 Load-time (`ItemAbilityRegistry` + `RewardLoadValidator` / reward parse)

| Check | Action |
|-------|--------|
| `effect: BUFF_AREA` + empty / missing `potions` | warn + skip ability / option |
| Unknown potion name | warn + skip that potion entry; if none left → skip ability |
| `duration_ticks ≤ 0` | warn + skip that potion entry |
| `targets` not in `{self,allies,enemies}` | warn + skip |
| STAT without fireTrigger | warn + skip |
| STAT fireTrigger not in combat whitelist | warn + skip |
| `mark_on` invalid | warn; treat as none |

### 7.2 Runtime

- No allies in radius → still play caster fx; potions applied to empty set  
- Dead / invalid mark entity → skip mark  

---

## 8. Files to touch

| File | Change |
|------|--------|
| `Effect.java` | Add `BUFF_AREA` |
| `EffectKeys.java` | Keys for potions list / mark_on if needed (or reuse nested EffectContext) |
| `PotionMerge.java` | **Create** — pure merge logic |
| `TriggerContext.java` | **Create** |
| `EffectService.java` | `BUFF_AREA` branch; thread TriggerContext |
| `EffectListener.java` | Per-player fire + context |
| `MagicEffectParams.java` | Defaults + validateBuffArea |
| `ItemAbilityRegistry.java` | Parse `potions[]`, validate BUFF_AREA |
| `RewardOption.java` | Parse `potions[]` in params (list of maps) |
| `RewardLoadValidator.java` | BUFF_AREA rules |
| `items/maggoteers.yml` | Sample buff staff (optional smoke) |
| `rewards.yml` | Optional 1 sample STAT (optional) |
| Tests | `PotionMergeTest`, validator tests |

---

## 9. Testing

- `PotionMergeTest`: three rules + no-existing  
- Validator: reject empty potions, bad targets, STAT without trigger, ON_INTERACT still forbidden  
- Manual smoke: buff staff right-click → allies get Resistance + fx; kill STAT → team buff + mark on corpse  

---

## 10. Doc sync (with implementation)

- `CLAUDE.md` §10 Effect catalog + weapon guide  
- `.cursor/skills/maggoteers-plugin-config/reference.md` — BUFF_AREA params  
- `docs/dev-log.md` — dated entry  
- Cross-link from magic-abilities design as extension  

---

## 11. Decisions locked

| Topic | Decision |
|-------|----------|
| Effect type | New `BUFF_AREA` (not overload HEAL_AREA) |
| Status kind | Vanilla timed potions only |
| Multi-potion | `potions[]` list |
| Targets v1 | `self` \| `allies` \| `enemies` |
| Merge | Higher amp wins; same amp → longer duration; lower amp → replace |
| FX | Independent caster `fx` + optional `mark_fx`/`mark_on` for weapon and STAT |
| Interact | Weapon `use_ability` only; STAT bans ON_INTERACT |
| Combat fire | Per-player + `TriggerContext` |
| PlayerState | Do not store timed buffs |

---

## Appendix: Review checklist

| Issue | Resolution |
|-------|------------|
| Weapon vs STAT duplication | Shared `executeMagicEffect(BUFF_AREA)` |
| ON_INTERACT on rewards | Still forbidden; weapon path covers |
| Mark without context | Silent skip |
| Spell power | Not applied to potions |
| Game-wide combat fire | Fix to per-player as part of this work |
