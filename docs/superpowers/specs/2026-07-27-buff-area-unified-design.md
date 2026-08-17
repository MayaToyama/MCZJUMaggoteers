# BUFF_AREA Unified Design

> **Status:** Ready for implementation plan (post-review revision)  
> **Date:** 2026-07-27  
> **Revision:** v1.2 — compat validation, dead-entity potions, global ON_DAMAGE_TAKEN, radius=5.0, mark vs merge B  
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
| Load-time compat | Impossible `targets`/`mark_on`×`fireTrigger` combos warn+skip or coerce (§7.2) |

### 1.3 Out of scope (v1)

- `targets: both` (buff allies + debuff enemies in one cast)
- `also_hit_target` (AoE ∪ hitTarget)
- Writing timed buffs into `PlayerState` (Bukkit potions only)
- STAT `fireTrigger: ON_INTERACT` (weapon `use_ability` covers interact)
- Permanent / aura-style carrier (use existing `AURA`)
- Per-potion FX
- **ON_DAMAGE_DEALT via Projectile** (bow as damager) — deferred; melee `damager instanceof Player` only this phase
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
mark: each target in list     mark: mark_on + TriggerContext
      (even if merge skips)         (compat-validated at load)
```

| Data | Source | Notes |
|------|--------|-------|
| Weapon ability | `ItemAbilityRegistry` | Not in PlayerState |
| STAT effect | `PlayerState.effects()` | Existing `PlayerEffect` |
| Spell power | N/A | Does **not** apply `MAGIC_DAMAGE` |
| Potion entry type | Prefer reuse `wave.PotionSpec` **or** parallel `effect` record with same fields (`potion`/`effect`, `amp`, `duration_ticks`/`dur`) | Avoid dual ad-hoc maps; plan picks one |

---

## 3. Trigger boundaries

### 3.1 Weapons (main-hand right-click)

- No `trigger:` on items; CD via `use_ability.cooldown_sec`.
- Left-click melee does **not** fire weapon abilities (unchanged).

### 3.2 Reward STAT — allowed `fireTrigger` for `BUFF_AREA`

| Value | When | Context |
|-------|------|---------|
| `ON_KILL` | Killer kills entity | `hitTarget` = victim (**may already be dead**) |
| `ON_DAMAGE_DEALT` | Player **melee** damages entity (`damager instanceof Player`) | `hitTarget` = victim |
| `ON_DAMAGE_TAKEN` | Player damaged by **enemy attack** only (see §3.4) | `attacker` = resolved enemy LivingEntity |

**Forbidden fireTrigger:** `ON_INTERACT`, lifecycle/tick triggers (unchanged whitelist).

**Load rules:**

- STAT `BUFF_AREA` **must** have `fireTrigger` (no permanent BUFF_AREA STAT).
- Weapon path has no fireTrigger.
- Compat matrix §7.2 applies.

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

### 3.4 ON_DAMAGE_TAKEN — enemy attacks only (**catalog-level semantics**)

> **Not BUFF_AREA-private.** Changing `EffectListener` for `ON_DAMAGE_TAKEN` changes the **Trigger catalog** meaning for every effect/expiry that uses it (CLAUDE.md §10.1 must say the same).

**Fire when:**

- `EntityDamageByEntityEvent` and the damaging party resolves to a **non-ally LivingEntity** (“enemy”):
  - Direct: damager is LivingEntity and not an ally run participant
  - Projectile: damager is Projectile, `getShooter()` is LivingEntity enemy (mob arrow etc.)
- Ally / self / environmental (fall, fire tick, drowning, etc.) → **do not** fire `ON_DAMAGE_TAKEN`

**Attacker field:** resolved enemy LivingEntity (shooter if projectile). Always non-null when the trigger fires.

**Current content:** `rewards.yml` has **no** `ON_DAMAGE_TAKEN` entries today — risk low; still document as global change.

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
  radius: 5.0                 # default 5.0 (align HEAL_AREA); ignored for self|hit_target
  targets: allies             # self | allies | enemies | hit_target; omit → allies
  include_self: true          # allies only; default true
  enemy_scope: tracked        # enemies only; tracked | all_living
  potions:
    - { potion: RESISTANCE, amp: 0, duration_ticks: 200 }
    - { potion: REGENERATION, amp: 0, duration_ticks: 100 }
  fx:
    preset: RIPPLE_RINGS
    radius: 5.0
    particle: HAPPY_VILLAGER
  mark_fx:
    preset: DOT_ABOVE
    particle: SOUL_FIRE_FLAME
  mark_on: hit_target         # STAT: none | hit_target | attacker
```

| Field | Notes |
|-------|-------|
| `potions[]` | ≥1 required; `amp` default **0**; `amp < 0` → **clamp to 0** at load; `duration_ticks` must be **>0** |
| `targets` omit | Default **`allies`** |
| `targets: self` | Caster only; ignore radius |
| `targets: allies` | `AllyTargeting.alliesInRadius` |
| `targets: enemies` | `TargetResolver` + `enemy_scope` (DAMAGE_AREA path) |
| `targets: hit_target` | **Independent** resolve: singleton `{ctx.hitTarget}` — **must not** call `TargetResolver.collect` / `AuraParams.targetsAllies` (those do not know `hit_target`) |
| `radius` | **Locked default `5.0`** in `withDefaults` + validation table (same number everywhere). For `allies`/`enemies`, if present and `≤0` → warn+skip |
| `fx` / `mark_fx` | `MagicFxBuilder` schema |

### 4.2 Weapon YAML

```yaml
use_ability:
  cooldown_sec: 20
  effect: BUFF_AREA
  params:
    radius: 5.0
    targets: allies
    include_self: true
    potions:
      - { potion: RESISTANCE, amp: 0, duration_ticks: 200 }
    mark_fx:
      preset: DOT_ABOVE
      particle: HAPPY_VILLAGER
  fx:
    preset: RIPPLE_RINGS
    radius: 5.0
    particle: HAPPY_VILLAGER
```

- Caster FX: **`use_ability.fx` wins** over `params.fx`.
- If weapon YAML sets `mark_on` → load **warn: ignored** (STAT-only field).
- If weapon `targets: hit_target` → load **warn** (always empty without context).

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
    fx: { preset: THICK_RING, radius: 1.5, particle: ITEM_SLIME }
    mark_fx: { preset: DOT_ABOVE, particle: ANGRY_VILLAGER }
    mark_on: hit_target
  display: "<green>淬毒：命中上毒"
```

**Parsing (must implement):** `RewardOption.parseParams` today only handles `carrier_fx` / `mark_fx`. Add `fx` → `EffectKeys.FX`, `mark_on` → `EffectKeys.MARK_ON`, `potions` → `EffectKeys.POTIONS` (deep-copied in `EffectContext.copyDeep`).

---

## 5. Potion merge rules

1. Existing amp **>** new A → **ignore** new buff  
2. Existing amp **==** new A → keep **longer remaining duration**  
3. Existing amp **<** new A → **replace** with new buff  

**Locked:** higher new amp replaces; equal → longer duration; **lower new amp ignored.**

`PotionMerge.shouldApply(...)` + unit tests.  
`addPotionEffect(..., true)` when applying. Flags: `ambient=false`, `particles=true`.

### 5.1 Conflict with permanent ADD_POTION / AURA

**Author convention (v1):** avoid same potion type. No runtime priority.

### 5.2 Dead / invalid entities

When applying potions to a candidate entity:

- If `!entity.isValid()` **or** `entity.isDead()` → **skip potion apply** for that entity  
- **Mark may still play** at that entity’s location (corpse / death tick) when mark rules say so  

**Author guidance:** `ON_KILL` + `targets: hit_target` is usually pointless for potions (victim already dead). Prefer `allies` for kill team-buff. Load-time: **warn** (non-fatal; option still loads) when STAT has `fireTrigger: ON_KILL` + `targets: hit_target`.

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
  → executeMagicEffect(BUFF_AREA, params, null, empty)
      → build target list (§6.4)
      → for each target: skip dead for potions; PotionMerge
      → weapon mark: every entity in target list with mark_fx (§6.3 B)

STAT:
  fireTriggerPlayer(..., ctx)
  → if params.fx → MagicFxPresets.play(caster, fx)
  → build target list
  → potions (skip dead/invalid)
  → mark_on + mark_fx (may mark dead hit_target on kill)
```

**Weapon:** do **not** re-play caster FX inside `executeMagicEffect`.

### 6.3 Mark resolution

| Path | Mark behavior |
|------|----------------|
| **Weapon** | If `mark_fx` present → play on **every entity in the resolved target list**, **even if PotionMerge applied zero potions** (**policy B** — cast feedback). Ignore `mark_on` (warn if set). |
| **STAT** | `mark_on: none` → no mark. `hit_target` / `attacker` from context; missing → silent skip. Dead hit_target: mark **allowed**. |

### 6.4 Target resolution (do not misuse TargetResolver for hit_target)

| `targets` | Potion/mark list |
|-----------|------------------|
| `self` | `{caster}` |
| `allies` | `AllyTargeting.alliesInRadius` |
| `enemies` | `TargetResolver` + enemy_scope |
| `hit_target` | `{ctx.hitTarget}` if non-null, else `{}` — **dedicated branch only** |

---

## 7. Validation

### 7.1 General load checks

| Check | Action |
|-------|--------|
| Empty / missing `potions` | warn + skip |
| Unknown potion / `duration_ticks ≤ 0` | warn + skip entry; if none left → skip ability |
| `amp < 0` | clamp to **0** |
| `targets` not in `{self,allies,enemies,hit_target}` | warn + skip |
| Omit `targets` | default **`allies`** |
| `allies`/`enemies` with explicit `radius ≤ 0` | warn + skip |
| Omit `radius` for allies/enemies | default **`5.0`** |
| STAT without fireTrigger | warn + skip |
| Forbidden fireTrigger | warn + skip |
| Weapon `targets: hit_target` | warn |
| Weapon `mark_on` set | warn: ignored |
| Invalid `mark_on` | warn; treat as none |
| STAT `ON_KILL` + `targets: hit_target` | **warn** (potions noop on corpse; mark still ok) |

### 7.2 Compat: `targets` / `mark_on` × `fireTrigger` (STAT)

| Combination | Runtime without check | Load action |
|-------------|----------------------|-------------|
| `targets: hit_target` + `ON_DAMAGE_TAKEN` | hitTarget always null → empty | **warn + skip** option (require trigger ∈ `{ON_KILL, ON_DAMAGE_DEALT}`) |
| `targets: hit_target` + `ON_KILL` / `ON_DAMAGE_DEALT` | OK (kill: potions skipped if dead, mark ok) | allow; kill combo soft-warn §7.1 |
| `mark_on: attacker` + `ON_KILL` / `ON_DAMAGE_DEALT` | attacker unset → no mark | **warn**; coerce `mark_on` to **none** (or require `ON_DAMAGE_TAKEN`) |
| `mark_on: hit_target` + `ON_DAMAGE_TAKEN` | hitTarget unset → no mark | **warn**; coerce to **none** (or require kill/dealt) |
| `mark_on: attacker` + `ON_DAMAGE_TAKEN` | OK | allow |
| `mark_on: hit_target` + `ON_KILL` / `ON_DAMAGE_DEALT` | OK | allow |

Implement in `RewardLoadValidator` (BUFF_AREA branch).

`MagicEffectParams.withDefaults(BUFF_AREA)`: `targets=allies`, `include_self=true`, `enemy_scope=tracked`, `radius=5.0`.

---

## 8. Files to touch

| File | Change |
|------|--------|
| `Effect.java` | Add `BUFF_AREA` |
| `EffectKeys.java` | `FX`, `MARK_ON`, `POTIONS` |
| `EffectContext.java` | Deep-copy potions / FX |
| `PotionMerge.java` | **Create** |
| `TriggerContext.java` | **Create** |
| `EffectService.java` | BUFF_AREA; TriggerContext; dead skip; mark policy B |
| `EffectListener.java` | Per-player; melee dealt; enemy-only taken (+ shooter for taken); **global** ON_DAMAGE_TAKEN |
| `MagicEffectParams.java` | Defaults radius **5.0** + validateBuffArea |
| `ItemAbilityRegistry.java` | Parse potions; weapon mark_on/hit_target warns |
| `RewardOption.java` | Parse `fx`, `mark_on`, `potions[]` |
| `RewardLoadValidator.java` | BUFF_AREA + compat matrix §7.2 |
| `CLAUDE.md` | §10.1 ON_DAMAGE_TAKEN catalog note; Effect catalog |
| Sample YAML / tests | As §9 |

---

## 9. Testing

- `PotionMergeTest`: higher/equal/lower + no-existing  
- Validator: empty potions; compat skip (`hit_target`+`ON_DAMAGE_TAKEN`); `mark_on:attacker`+`ON_KILL` → coerced none  
- Listener: only killer gets ON_KILL; fall does **not** fire ON_DAMAGE_TAKEN; enemy melee/arrow does  
- `targets: enemies` ≠ allies  
- `targets: hit_target` only victim  
- Dead hit_target: no potion, mark still attempted  
- Weapon: mark on list members even when merge ignores all  
- No double caster FX on weapon  

---

## 10. Doc sync (with implementation)

- `CLAUDE.md` §10.1 Trigger catalog — **ON_DAMAGE_TAKEN = enemy attack only** (global)  
- `CLAUDE.md` §10.2 / weapon guide — BUFF_AREA  
- plugin-config `reference.md`  
- `docs/dev-log.md`  
- Cross-link magic-abilities design  

---

## 11. Decisions locked

| Topic | Decision |
|-------|----------|
| Effect type | New `BUFF_AREA` |
| Status kind | Vanilla timed potions only |
| Multi-potion | `potions[]` (prefer shared PotionSpec-shaped type) |
| Targets v1 | `self` \| `allies` \| `enemies` \| `hit_target` |
| Targets default | **`allies`** |
| Radius default | **`5.0`** (locked, align HEAL_AREA) |
| Hit debuff | `hit_target` only on `ctx.hitTarget` |
| hit_target resolve | Dedicated branch; not TargetResolver/AuraParams |
| Merge | Higher new amp replaces; equal → longer; lower new ignored |
| Permanent conflict | Author avoid same type |
| Weapon mark | **Policy B:** every entity in target list (merge irrelevant) |
| STAT mark | `mark_on` + context; compat-validated |
| Weapon `mark_on` | Warn ignored |
| Dead entity | Skip potions; mark allowed |
| ON_KILL + hit_target | Soft warn; prefer allies for kill buffs |
| Compat matrix | §7.2 warn+skip / coerce |
| Weapon caster FX | Handler only |
| Interact | Weapon use_ability; STAT bans ON_INTERACT |
| Combat fire | Per-player + context; intentional team HEAL fix |
| ON_DAMAGE_DEALT | Melee Player only |
| ON_DAMAGE_TAKEN | **Global:** enemy melee/arrow only; no environment |
| amp < 0 | Clamp to 0 |
| PotionEffect flags | ambient=false, particles=true |

---

## Appendix: Review checklist (v1.2)

| Issue | Resolution |
|-------|------------|
| hit_target vs ON_DAMAGE_TAKEN | Load skip |
| mark_on attacker vs kill/dealt | Warn + coerce none |
| Dead victim potions | Skip potions; mark ok |
| ON_DAMAGE_TAKEN global | Catalog + CLAUDE §10.1 |
| radius e.g. | Locked **5.0** |
| Mark vs merge | Policy **B** |
| Weapon mark_on | Warn ignored |
| Double schema potions | Prefer PotionSpec-shaped type |
| hit_target via TargetResolver | Forbidden; dedicated branch |
| amp < 0 | Clamp 0 |
