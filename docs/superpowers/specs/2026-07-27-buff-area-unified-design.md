# BUFF_AREA Unified Design

> **Status:** Ready for implementation plan (post-review revision)  
> **Date:** 2026-07-27  
> **Revision:** v1.1 — review fixes (`hit_target`, weapon mark recipients, listener semantics, RewardOption parsing)  
> **Depends on:** `2026-07-27-config-magic-abilities-design.md` (shared `executeMagicEffect`, FX presets, targets vocabulary)

**Summary:** Add `Effect.BUFF_AREA` — shared magic effect applying timed vanilla potions. Same core for weapon right-click (`use_ability`) and reward STAT combat triggers (`ON_KILL` / `ON_DAMAGE_DEALT` / `ON_DAMAGE_TAKEN`). Targets: `self` | `allies` | `enemies` | `hit_target`. FX: caster ring + optional marks.

---

## 1. Goals

### 1.1 Problems

- Weapon `use_ability` only supports `DAMAGE_AREA` / `DAMAGE_BEAM` / `HEAL_AREA` — cannot apply potion buffs/debuffs.
- Reward STAT can only potion-buff **self** via `ADD_POTION` (permanent or fireTrigger → self only).
- No config path for:
  - right-click → team Resistance (AoE allies)
  - kill → team buff
  - hit → **debuff the hit entity** (`targets: hit_target`)
  - take damage from enemy → self Absorption

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| Zero-Java weapons | New buff staff = `items/*.yml` `use_ability` only |
| Shared core | Weapon + STAT use one `BUFF_AREA` implementation |
| Trigger separation | Right-click via `use_ability`; combat via `rewards.yml` `trigger:` |
| Hit-target debuff | `targets: hit_target` applies potions **only** to `ctx.hitTarget` |
| Configurable FX | Caster `fx` + mark rules (weapon vs STAT differ — see §6.3) |
| Potion merge | Higher new amp replaces; equal → longer duration; lower new amp ignored (§5) |

### 1.3 Out of scope (v1)

- `targets: both` (buff allies + debuff enemies in one cast)
- `also_hit_target` (AoE ∪ hitTarget)
- Writing timed buffs into `PlayerState` (Bukkit potions only)
- STAT `fireTrigger: ON_INTERACT` (weapon `use_ability` covers interact)
- Permanent / aura-style carrier (use existing `AURA`)
- Per-potion FX
- **ON_DAMAGE_DEALT via Projectile** (bow/arrow as damager) — deferred; melee `damager instanceof Player` only this phase
- Special merge vs permanent `ADD_POTION` / AURA potions — **author convention only** (avoid same potion type)

---

## 2. Architecture

```
Effect.BUFF_AREA  (potions[] + targets + radius + FX)
        ▲
   ┌────┴────────────────────────────┐
use_ability (weapon)          PlayerEffect fireTrigger (STAT)
ConfigMagicHandler            EffectService.executeEffect
→ executeAbility              → executeMagicEffect
FX: use_ability.fx            FX: params.fx
mark: each potion recipient   mark: mark_on + TriggerContext
```

| Data | Source | Notes |
|------|--------|-------|
| Weapon ability | `ItemAbilityRegistry` | Not in PlayerState |
| STAT effect | `PlayerState.effects()` | Existing `PlayerEffect` |
| Spell power | N/A | Does **not** apply `MAGIC_DAMAGE` |

---

## 3. Trigger boundaries

### 3.1 Weapons (main-hand right-click)

- No `trigger:` on items; CD via `use_ability.cooldown_sec`.
- Left-click melee does **not** fire weapon abilities (unchanged).

### 3.2 Reward STAT — allowed `fireTrigger` for `BUFF_AREA`

| Value | When | Context |
|-------|------|---------|
| `ON_KILL` | Killer kills entity | `hitTarget` = victim |
| `ON_DAMAGE_DEALT` | Player **melee** damages entity (`damager instanceof Player`) | `hitTarget` = victim |
| `ON_DAMAGE_TAKEN` | Player damaged by **enemy attack** only (see §3.4) | `attacker` = resolved enemy LivingEntity |

**Forbidden fireTrigger:** `ON_INTERACT`, lifecycle/tick triggers (unchanged whitelist).

**Load rules:**

- STAT `BUFF_AREA` **must** have `fireTrigger` (no permanent BUFF_AREA STAT).
- Weapon path has no fireTrigger.

### 3.3 Combat listener: per-player + context (**behavior change**)

Replace game-wide `fireTrigger(game, …)` for combat with:

```text
fireTriggerPlayer(game, involvedPlayer, trigger, TriggerContext)
```

| Event | Involved player | Context |
|-------|-----------------|---------|
| Kill | killer | hitTarget = dead entity |
| Damage dealt | damager player (melee only) | hitTarget = damaged entity |
| Damage taken | damaged player | attacker = enemy LivingEntity (see §3.4) |

**Migration note (explicit):** Existing rewards `a1w_lifesteal` (`ON_KILL`+`HEAL`), `a2w_vamp` (`ON_DAMAGE_DEALT`+`HEAL`) currently fire for **all** teammates under game-wide `fireTrigger`. After this change only the **acting player** triggers — correct semantics; document as intentional fix, not a regression.

### 3.4 ON_DAMAGE_TAKEN — enemy attacks only

**Fire when:**

- `EntityDamageByEntityEvent` and the damaging party resolves to a **non-ally LivingEntity** (“enemy”):
  - Direct: damager is LivingEntity and not an ally run participant
  - Projectile: damager is Projectile, `getShooter()` is LivingEntity enemy (mob arrow etc.)
- Ally / self / environmental (fall, fire tick, drowning, etc.) → **do not** fire `ON_DAMAGE_TAKEN`

**Attacker field:** resolved enemy LivingEntity (shooter if projectile). Always non-null when the trigger fires.

**Note:** Projectile resolution here is **only** for damage-taken enemy detection. `ON_DAMAGE_DEALT` still requires `damager instanceof Player` (no bow on-hit this phase — §1.3).

### 3.5 ON_DAMAGE_DEALT — melee only (v1)

```text
if (!(event.getDamager() instanceof Player)) return; // no Projectile→shooter yet
```

Bow/trident on-hit passives deferred.

---

## 4. Schema

### 4.1 Shared `params`

```yaml
params:
  radius: 6.0                 # required >0 for allies|enemies; ignored for self|hit_target
  targets: allies             # self | allies | enemies | hit_target
  include_self: true          # allies only; default true
  enemy_scope: tracked        # enemies only; tracked | all_living
  potions:
    - { potion: RESISTANCE, amp: 0, duration_ticks: 200 }
    - { potion: REGENERATION, amp: 0, duration_ticks: 100 }
  fx:                         # optional caster FX (STAT); weapon prefers use_ability.fx
    preset: RIPPLE_RINGS
    radius: 6.0
    particle: HAPPY_VILLAGER
  mark_fx:                    # optional
    preset: DOT_ABOVE
    particle: SOUL_FIRE_FLAME
  mark_on: hit_target         # STAT only meaningful: none | hit_target | attacker
```

| Field | Notes |
|-------|-------|
| `potions[]` | ≥1 required; `amp` default **0**; `duration_ticks` must be **>0** |
| `targets` omit | Default **`allies`** (align HEAL_AREA) via `MagicEffectParams.withDefaults` |
| `targets: self` | Caster only; ignore radius |
| `targets: allies` | `AllyTargeting.alliesInRadius`; `include_self` default true |
| `targets: enemies` | Same as DAMAGE_AREA (`TargetResolver` + `enemy_scope`) |
| `targets: hit_target` | **Only** `ctx.hitTarget`; if null → no potions (weapon path always null → empty) |
| `radius` | Default e.g. `5.0` for allies/enemies; validate `>0` when those targets used |
| `fx` / `mark_fx` | Same schema as AURA / weapon FX (`MagicFxBuilder`) |

### 4.2 Weapon YAML

```yaml
use_ability:
  cooldown_sec: 20
  effect: BUFF_AREA
  params:
    radius: 6.0
    targets: allies          # hit_target is useless on weapons (warn if set — see §6.3)
    include_self: true
    potions:
      - { potion: RESISTANCE, amp: 0, duration_ticks: 200 }
    mark_fx:                 # played on each potion recipient
      preset: DOT_ABOVE
      particle: HAPPY_VILLAGER
  fx:
    preset: RIPPLE_RINGS
    radius: 6.0
    particle: HAPPY_VILLAGER
```

- Caster FX: **`use_ability.fx` wins** over `params.fx`.
- Weapon **does not** use `mark_on` / TriggerContext for marks (see §6.3).

### 4.3 STAT reward YAML

```yaml
- id: a1s_hit_poison
  category: STAT
  unique: true
  trigger: ON_DAMAGE_DEALT
  effect: BUFF_AREA
  params:
    targets: hit_target
    potions:
      - { potion: POISON, amp: 0, duration_ticks: 60 }
    fx:
      preset: THICK_RING
      radius: 1.5
      particle: ITEM_SLIME
    mark_fx:
      preset: DOT_ABOVE
      particle: ANGRY_VILLAGER
    mark_on: hit_target
  display: "<green>淬毒：命中上毒"
```

**Parsing (must implement — not present today):** `RewardOption.parseParams` currently only handles `carrier_fx` / `mark_fx`. Add:

| Key | Storage |
|-----|---------|
| `fx` | `EffectKeys.FX` (new) nested EffectContext |
| `mark_on` | `EffectKeys.MARK_ON` (new) String |
| `potions` | `EffectKeys.POTIONS` (new) list structure (immutable copy in `EffectContext.copyDeep`) |
| `targets` / `include_self` / `enemy_scope` / `radius` | existing keys where present |

Unknown keys remain ignored; these three must **not** be dropped.

---

## 5. Potion merge rules

When applying potion type `T` with amp `A` and duration `D` to an entity that already has `T`:

1. Existing amp **>** new A → **ignore** new buff  
2. Existing amp **==** new A → keep **longer remaining duration**  
3. Existing amp **<** new A → **replace** with new buff  

Locked wording (§11): **higher new amp replaces; equal → longer duration; lower new amp ignored.**

`PotionMerge.shouldApply(...)` pure helper + unit tests.

Apply with `addPotionEffect(effect, true)` when merge says apply. Flags align trigger `ADD_POTION`: `ambient=false`, `particles=true`.

### 5.1 Conflict with permanent ADD_POTION / AURA

**Author convention (v1):** do not use the same potion type on permanent/`AURA` grants and `BUFF_AREA`. No runtime priority between PlayerState resync and BUFF_AREA. Document in plugin-config reference.

---

## 6. Runtime

### 6.1 `TriggerContext`

```java
public record TriggerContext(
    Trigger fired,
    LivingEntity hitTarget,
    LivingEntity attacker
) {
    public static TriggerContext empty() {
        return new TriggerContext(null, null, null);
    }
}
```

### 6.2 Execution (avoid double caster FX)

```
Weapon:
  CooldownService.tryUse
  → MagicFxService.play(use_ability.fx)          # caster FX once (handler only)
  → executeAbility → executeMagicEffect(BUFF_AREA, params, null, empty)
      → resolve potion targets
      → PotionMerge apply
      → weapon mark: for each recipient with mark_fx → play mark (no mark_on)

STAT:
  fireTriggerPlayer(..., ctx)
  → executeEffect → executeMagicEffect(BUFF_AREA, params, null, ctx)
      → if params.fx present → MagicFxPresets.play(caster, fx)   # once
      → resolve targets (incl. hit_target)
      → PotionMerge apply
      → if mark_on + mark_fx + entity → mark once
```

**Weapon:** do **not** pass/play `weaponFx` again inside `executeMagicEffect` (HEAL_AREA already ignores it; BUFF_AREA must not double-play caster FX).

`executeEffect` adds `case BUFF_AREA -> executeMagicEffect(...)`.

### 6.3 Mark resolution

| Path | Mark behavior |
|------|----------------|
| **Weapon** | If `mark_fx` present → play on **each entity that received at least one potion**. Ignore `mark_on`. If `targets: hit_target` on weapon → load **warn** (always empty recipients). |
| **STAT** | `mark_on: none` → no mark. `hit_target` → `ctx.hitTarget()`. `attacker` → `ctx.attacker()`. Missing entity → silent skip. |

### 6.4 Target resolution summary

| `targets` | Potion recipients |
|-----------|-------------------|
| `self` | Caster |
| `allies` | Allies in radius (+ include_self) |
| `enemies` | Enemies in radius per enemy_scope |
| `hit_target` | Singleton `{ctx.hitTarget}` or empty |

### 6.5 Not PlayerState

Timed potions are Bukkit-only.

---

## 7. Validation

| Check | Action |
|-------|--------|
| Empty / missing `potions` | warn + skip |
| Unknown potion / `duration_ticks ≤ 0` | warn + skip entry; if none left → skip ability |
| `targets` not in `{self,allies,enemies,hit_target}` | warn + skip |
| Omit `targets` | default `allies` (not skip) |
| `allies`/`enemies` with `radius ≤ 0` | warn + skip |
| STAT without fireTrigger | warn + skip |
| Forbidden fireTrigger | warn + skip |
| Weapon `targets: hit_target` | warn (ability may load but does nothing useful) |
| Invalid `mark_on` | warn; treat as none |

`MagicEffectParams.withDefaults(BUFF_AREA)`: default `targets=allies`, `include_self=true`, `enemy_scope=tracked`, default radius when needed.

---

## 8. Files to touch

| File | Change |
|------|--------|
| `Effect.java` | Add `BUFF_AREA` |
| `EffectKeys.java` | `FX`, `MARK_ON`, `POTIONS` (+ list type) |
| `EffectContext.java` | Deep-copy `POTIONS` / nested `FX` |
| `PotionMerge.java` | **Create** |
| `TriggerContext.java` | **Create** |
| `EffectService.java` | BUFF_AREA branch; TriggerContext through executeEffect |
| `EffectListener.java` | Per-player fire; melee dealt; enemy-only taken (+ projectile shooter for **taken** only) |
| `MagicEffectParams.java` | Defaults + validateBuffArea |
| `ItemAbilityRegistry.java` | Parse `potions[]`, validate |
| `RewardOption.java` | Parse `fx`, `mark_on`, `potions[]` (critical) |
| `RewardLoadValidator.java` | BUFF_AREA rules |
| Sample YAML | Optional buff staff + optional STAT |
| Tests | PotionMerge; validator; fireTriggerPlayer isolation; hit_target; enemies≠allies |

---

## 9. Testing

- `PotionMergeTest`: higher/equal/lower amp + no-existing  
- Validator: empty potions, bad targets, STAT w/o trigger, ON_INTERACT forbidden  
- Listener: only killer gets ON_KILL effects (not teammates)  
- `targets: enemies` does not buff allies  
- `targets: hit_target` applies only to victim when context present  
- Weapon: mark on each recipient; no caster FX double-play  
- Manual: buff staff; hit-poison STAT; enemy-hit Absorption; fall damage does **not** fire ON_DAMAGE_TAKEN  

---

## 10. Doc sync (with implementation)

- `CLAUDE.md` §10 + weapon guide  
- plugin-config `reference.md`  
- `docs/dev-log.md`  
- Cross-link magic-abilities design  

---

## 11. Decisions locked

| Topic | Decision |
|-------|----------|
| Effect type | New `BUFF_AREA` |
| Status kind | Vanilla timed potions only |
| Multi-potion | `potions[]` |
| Targets v1 | `self` \| `allies` \| `enemies` \| **`hit_target`** |
| Targets default | **`allies`** if omitted |
| Hit debuff | `hit_target` = potions only on `ctx.hitTarget` (not AoE+mark mismatch) |
| Merge | Higher new amp replaces; equal → longer duration; **lower new amp ignored** |
| Permanent conflict | Author avoid same type; no runtime special-case |
| Weapon mark | **Each potion recipient** gets `mark_fx`; ignore `mark_on` |
| STAT mark | `mark_on` + TriggerContext |
| Weapon caster FX | Handler only; core does not re-play |
| Interact | Weapon `use_ability`; STAT bans ON_INTERACT |
| Combat fire | Per-player + context; **intentional** fix for team-wide HEAL triggers |
| ON_DAMAGE_DEALT | Melee Player damager only (no Projectile→shooter this phase) |
| ON_DAMAGE_TAKEN | Enemy melee/arrow only; **no** environmental; attacker always set when fired |
| PlayerState | Do not store timed buffs |
| PotionEffect flags | ambient=false, particles=true |
| amp default | 0 |

---

## Appendix: Review checklist (v1.1)

| Issue | Resolution |
|-------|------------|
| §1.1 hit debuff vs AoE | Added `targets: hit_target` |
| §11 merge wording | Aligned with §5 (lower new amp ignored) |
| rewards parse `fx`/`potions` | Explicit RewardOption + EffectKeys work |
| Weapon mark empty context | Mark each recipient instead |
| Permanent potion clash | Author convention |
| targets omit | Default allies |
| ON_DAMAGE_TAKEN env | Do not fire |
| Projectile on dealt | Out of scope v1 |
| Projectile on taken | Resolve shooter to detect enemy + fill attacker |
| Per-player fire | Documented migration for lifesteal/vamp |
| Double caster FX | Weapon: handler only |
