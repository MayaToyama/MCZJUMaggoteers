# DISABLE_AI Effect Design

> **Status:** Phase 1 implemented
> **Date:** 2026-08-08  
> **Revision:** v1.1 — unload restore, skip summons, executeEffect routing, held phase 2, ON_KILL+hit_target skip  
> **Depends on:** `2026-07-27-buff-area-unified-design.md` (targets / mark_fx / TriggerContext)  
> **Phase 2 depends on:** `2026-08-08-weapon-held-effects-design.md` (held combat triggers) — wire `held_effects` only after Path B lands

**Summary:** Add `Effect.DISABLE_AI` — temporarily `setAI(false)` on hostile mobs, restore after `duration_ticks`. **Phase 1** shared core for weapon `use_ability` + reward STAT combat triggers. **Phase 2** adds `held_effects` combat triggers. Targets: `enemies` | `hit_target` | `attacker`. Re-apply keeps the longer remaining duration.

---

## 0. Delivery phases

| Phase | Scope | Gate |
|-------|-------|------|
| **1 (this plan)** | `use_ability` + STAT `fireTrigger` + `MobAiLockRegistry` + docs/tests | Can ship without held Path B |
| **2** | `held_effects` allow-list + `WeaponEffectValidator` + held sample | After weapon-held-effects Path B merged |

Phase 1 must not block on held. Architecture below notes held as phase-2 surface; validators for held stay out of phase-1 acceptance.

---

## 1. Goals

### 1.1 Problems

- No config path to freeze hostile mobs in place (no pathfinding / melee AI) for a short window.
- Authors want the same selector vocabulary used elsewhere: AoE enemies, attack victim, or damage source.

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| Zero-Java weapons | New stun staff / on-hit stun = YAML only |
| Shared core | Weapon + STAT (+ held in phase 2) use one `DISABLE_AI` implementation |
| Hostile-only | Players, allies, and player summons never enter the target list |
| Timed restore | After `duration_ticks`, AI restored to pre-lock value |
| Unload-safe | Chunk unload / entity remove restores AI before drop from registry when entity still valid |
| Longer-wins merge | Re-apply extends expire only if new end is later |
| Load-time compat | Impossible `targets`×`fireTrigger` combos warn+skip |

### 1.3 Out of scope (v1 / phase 1)

- Cancelling damage events from stunned mobs
- Per-tick velocity clear / position pin (knockback may still move the body)
- `self` / `allies` / `both` targets
- Stacking independent stun layers
- Boss-specific immunity flags
- Writing stun state into `PlayerState`
- Projectile `ON_DAMAGE_DEALT` (still melee-only per existing catalog)
- `held_effects` wiring (**phase 2**)

---

## 2. Architecture

```
Effect.DISABLE_AI  (duration_ticks + targets + radius + FX)
        ▲
   ┌────┴──────────────────┬────────────────────────────┐
use_ability (weapon)   PlayerEffect fireTrigger    held_effects (phase 2)
ConfigMagicHandler     EffectService.executeEffect → executeMagicEffect
→ executeAbility       (same case group as DAMAGE_AREA / BUFF_AREA)
FX: use_ability.fx     FX: params.fx
mark: each locked tgt  mark: mark_on + context
```

**Critical routing:** `executeEffect` today routes `DAMAGE_AREA`, `DAMAGE_BEAM`, `HEAL_AREA`, `BUFF_AREA` into `executeMagicEffect`. **`DISABLE_AI` must be added to that same `case` group.** Documenting only `executeMagicEffect` is insufficient — STAT/held fireTrigger enters via `executeEffect`.

| Data | Source | Notes |
|------|--------|-------|
| Weapon ability | `ItemAbilityRegistry` | Not in PlayerState |
| STAT effect | `PlayerState.effects()` | Existing `PlayerEffect` + fireTrigger |
| held effect (phase 2) | `PlayerState` via `held:<itemId>:<n>` | Same fireTrigger path |
| Mob lock state | `MobAiLockRegistry` | Entity UUID → expire + restore flag |
| Spell power | N/A | Does **not** apply `MAGIC_DAMAGE` |

---

## 3. Trigger boundaries

### 3.1 Weapons (`use_ability`) — phase 1

- Main-hand right-click; CD via `cooldown_sec`.
- No `trigger:` on the ability block.
- `targets: hit_target` / `attacker` load **warn** (ineffective without combat context).

### 3.2 Reward STAT — allowed `fireTrigger` — phase 1

| Value | Context fields used |
|-------|---------------------|
| `ON_KILL` | Prefer `targets: enemies` (AoE at death site / origin). `targets: hit_target` is **warn+skip** (corpse always filtered — noop) |
| `ON_DAMAGE_DEALT` | `hitTarget` = victim (melee only) |
| `ON_DAMAGE_TAKEN` | `attacker` = enemy LivingEntity |

**STAT must have fireTrigger** (no permanent `DISABLE_AI` STAT).

Forbidden: `ON_INTERACT`, lifecycle/tick triggers (existing whitelist).

### 3.3 `held_effects` — phase 2

- Must include combat `trigger` ∈ `{ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_KILL}`.
- Forbidden as permanent held (no trigger).
- Forbidden: `expiry`, `UPGRADE_LEVEL`, `GRANT_ITEM`, `SUMMON` (existing held rules).
- Same `targets`×trigger compat matrix as STAT (§6.2).
- Implement only after weapon-held-effects Path B is merged.

---

## 4. Schema

### 4.1 Shared `params`

```yaml
params:
  duration_ticks: 60          # required, >0
  targets: enemies            # enemies | hit_target | attacker; omit → enemies
  radius: 5.0                 # enemies only; default 5.0
  enemy_scope: tracked        # enemies only; tracked | all_living; default tracked
  fx:
    preset: RIPPLE_RINGS
    radius: 5.0
    particle: SOUL
  mark_fx:
    preset: DOT_ABOVE
    particle: ENCHANT
  mark_on: hit_target         # STAT/held only: none | hit_target | attacker
```

| Field | Notes |
|-------|-------|
| `duration_ticks` | Required; `≤0` or missing → warn+skip |
| `targets` omit | Default **`enemies`** |
| `targets: enemies` | `TargetResolver` + `enemy_scope`; needs `radius > 0` |
| `targets: hit_target` | Singleton `{ctx.hitTarget}` — dedicated branch (reuse `BuffAreaTargets` pattern) |
| `targets: attacker` | Singleton `{ctx.attacker}` — dedicated branch |
| `self` / `allies` / `both` | Unsupported → warn+skip |
| `radius` | Default **5.0** via `withDefaults`; ignored for `hit_target` / `attacker` |
| `fx` / `mark_fx` | Same `MagicFxBuilder` schema as `BUFF_AREA` |

### 4.2 Weapon YAML (phase 1)

```yaml
use_ability:
  cooldown_sec: 8
  effect: DISABLE_AI
  params:
    duration_ticks: 60
    targets: enemies
    radius: 6.0
    enemy_scope: tracked
  fx:
    preset: RIPPLE_RINGS
    radius: 6.0
```

### 4.3 STAT YAML (phase 1)

```yaml
# On-hit stun
- id: a2s_stun_strike
  category: STAT
  trigger: ON_DAMAGE_DEALT
  effect: DISABLE_AI
  params:
    duration_ticks: 40
    targets: hit_target
    mark_fx: { preset: DOT_ABOVE, particle: ENCHANT }
    mark_on: hit_target
  unique: true
  display: "<aqua>震击：命中短暂瘫痪"

# Retaliation stun
- id: a2s_stun_thorns
  category: STAT
  trigger: ON_DAMAGE_TAKEN
  effect: DISABLE_AI
  params:
    duration_ticks: 40
    targets: attacker
  unique: true
  display: "<aqua>反震：受伤瘫痪来源"
```

Spec and `rewards.yml` must be UTF-8. Do not copy examples from a UTF-16 corrupted buffer.

### 4.4 held YAML (phase 2 only)

```yaml
held_effects:
  - effect: DISABLE_AI
    trigger: ON_DAMAGE_DEALT
    params:
      duration_ticks: 20
      targets: hit_target
```

---

## 5. Runtime

### 5.1 Apply

1. Resolve origin via `TriggerContext.resolveOrigin` (death_site / caster).
2. Build target list (§5.3); then **hostile filter** — skip if any of:
   - not a valid living entity / already dead
   - `instanceof Player`
   - ally run participant (`AllyTargeting` / same rules as damage-taken ally skip)
   - `SummonRegistry.isTrackedSummon(entity)` (player-owned summons — wolves etc.; required especially for `enemy_scope: all_living`)
3. For each remaining LivingEntity, call `MobAiLockRegistry.lock(entity, durationTicks)`.
4. Play FX / marks (§5.4).

### 5.2 `MobAiLockRegistry`

| Field | Meaning |
|-------|---------|
| key | Entity `UUID` |
| `expireAtTick` | Server tick when lock ends |
| `restoreAi` | `entity.hasAI()` captured on **first** lock |

**lock(entity, durationTicks):**

```text
newExpire = nowTick + durationTicks
if absent:
  restoreAi = entity.hasAI()
  entity.setAI(false)
  put(uuid, newExpire, restoreAi)
else:
  expireAtTick = max(oldExpire, newExpire)   # longer-wins; do not change restoreAi
```

**Tick / scan task** (plugin-owned, period **1 tick** so `duration_ticks` is exact):

- If entity gone / dead / invalid → remove entry (no restore needed — body is gone).
- If `nowTick >= expireAtTick` and entity valid → `entity.setAI(restoreAi)` then remove.

**Unload / remove path (required — prevents permanent NoAI in chunk NBT):**

- Listen `EntityRemoveEvent` (and/or world unload cleanup that still has valid entities).
- If UUID is tracked **and** entity is still valid → `setAI(restoreAi)` **then** remove from registry.
- Do **not** drop a valid unloaded entity from the registry without restore — chunk reload would keep `NoAI` forever.

**Game / plugin cleanup:**

- On game cleanup and plugin disable: for each tracked valid entity, `setAI(restoreAi)`, then clear map.

### 5.3 Target resolution

| `targets` | List |
|-----------|------|
| `enemies` | `TargetResolver.collect` with `Effect.DISABLE_AI` + `enemy_scope`, then §5.1 hostile filter |
| `hit_target` | `{ctx.hitTarget}` if non-null else `{}`, then §5.1 filter |
| `attacker` | `{ctx.attacker}` if non-null else `{}`, then §5.1 filter |

Dedicated branches for `hit_target` / `attacker` must **not** go through ally/enemy radius collect.

Prefer extending `BuffAreaTargets.resolveTargetKey` (or a thin shared helper) with `attacker` rather than duplicating switch logic.

### 5.4 FX / mark

| Path | Behavior |
|------|----------|
| Weapon | Caster FX from `use_ability.fx` (do not double-play inside `executeMagicEffect`). If `mark_fx` present → mark **every** entity in the resolved list (even if lock was a no-op merge). Ignore `mark_on` (warn if set). |
| STAT / held | `params.fx` at caster; `mark_on` + `mark_fx` from context (`none` → no mark; missing entity → silent skip). Dead `hitTarget` mark allowed (STAT mark only; AI lock still skipped by §5.1). |

### 5.5 `executeEffect` routing (must implement)

```java
case DAMAGE_AREA, DAMAGE_BEAM, HEAL_AREA, BUFF_AREA, DISABLE_AI ->
    executeMagicEffect(...);
```

Inside `executeMagicEffect`, add `case DISABLE_AI -> { ... }` that resolves targets, applies §5.1 filter, locks, FX/marks.

---

## 6. Validation

### 6.1 General load checks

| Check | Action |
|-------|--------|
| Missing / `duration_ticks ≤ 0` | warn + skip |
| `targets` not in `{enemies, hit_target, attacker}` | warn + skip |
| Omit `targets` | default **`enemies`** |
| `enemies` with explicit `radius ≤ 0` | warn + skip |
| Omit `radius` for enemies | default **5.0** |
| Invalid `enemy_scope` | warn + skip |
| STAT without fireTrigger | warn + skip |
| Forbidden fireTrigger | warn + skip |
| held without combat trigger (phase 2) | warn + skip |
| Weapon `targets: hit_target` or `attacker` | warn (ineffective) |
| Weapon `mark_on` set | warn: ignored |
| Invalid `mark_on` | warn; treat as none |
| STAT/held `ON_KILL` + `targets: hit_target` | **warn + skip** (corpse always filtered — force authors to use `enemies` AoE) |

### 6.2 Compat: `targets` / `mark_on` × `fireTrigger` (STAT / held phase 2)

| Combination | Load action |
|-------------|-------------|
| `targets: hit_target` + `ON_DAMAGE_TAKEN` | **warn + skip** |
| `targets: hit_target` + `ON_KILL` | **warn + skip** |
| `targets: attacker` + `ON_KILL` / `ON_DAMAGE_DEALT` | **warn + skip** |
| `targets: attacker` + `ON_DAMAGE_TAKEN` | allow |
| `targets: hit_target` + `ON_DAMAGE_DEALT` | allow |
| `targets: enemies` + allowed trigger | allow |
| `mark_on: attacker` + not `ON_DAMAGE_TAKEN` | warn; coerce `mark_on` → `none` |
| `mark_on: hit_target` + `ON_DAMAGE_TAKEN` | warn; coerce → `none` |

Implement in `MagicEffectParams.validateDisableAi` + `RewardLoadValidator` / `ItemAbilityRegistry` (phase 1); `WeaponEffectValidator` held branch in phase 2.

`MagicEffectParams.withDefaults(DISABLE_AI)`: `targets=enemies`, `enemy_scope=tracked`, `radius=5.0`.

---

## 7. Files to touch

### Phase 1

| File | Change |
|------|--------|
| `Effect.java` | Add `DISABLE_AI` |
| `EffectKeys.java` | Reuse `DURATION_TICKS`, `TARGETS`, `RADIUS`, `ENEMY_SCOPE`, `FX`, `MARK_FX`, `MARK_ON` |
| `BuffAreaTargets.java` (or shared helper) | Add `attacker` branch; allow `DISABLE_AI` resolve |
| `MobAiLockRegistry.java` | **Create** — lock / longer-wins / expire / unload-restore / cleanup |
| `EffectService.java` | Add `DISABLE_AI` to `executeEffect` → `executeMagicEffect` case group; implement case body |
| `MagicEffectParams.java` | defaults + `validateDisableAi` |
| `ItemAbilityRegistry.java` | allow `DISABLE_AI` on `use_ability`; weapon warns |
| `RewardLoadValidator.java` | STAT `DISABLE_AI` + compat matrix |
| `MaggoteersPlugin.java` | start 1-tick scan; register remove/unload listener; cleanup on disable |
| Game cleanup hook | Call `MobAiLockRegistry.restoreAll` / clear for game world |
| `CLAUDE.md` | §10.2 Effect catalog; §15 weapon examples |
| Sample YAML (optional) | one `use_ability` example in `items/maggoteers.yml` |
| Tests | §8 phase 1 |

### Phase 2 (after held Path B)

| File | Change |
|------|--------|
| `WeaponEffectValidator.java` | held combat `DISABLE_AI` validation |
| `WeaponHeldRegistry` / samples | allow + example held row |
| Tests | held permanent skip; held on-hit lock |

---

## 8. Testing

### Phase 1

- `MobAiLockRegistryTest`: first lock sets AI false; re-apply longer-wins; shorter re-apply ignored; expire restores original `hasAI`; dead entity drops entry
- **Unload:** lock → simulate remove/unload while entity still valid → AI restored and registry empty
- Validator: bad duration; `hit_target`+`ON_DAMAGE_TAKEN` skip; `hit_target`+`ON_KILL` skip; `attacker`+`ON_DAMAGE_DEALT` skip
- Target resolve: `enemies` excludes allies/players; `all_living` excludes `SummonRegistry` tracked summons; `hit_target` / `attacker` singletons
- Routing: STAT fireTrigger reaches `DISABLE_AI` via `executeEffect` case (not only direct `executeMagicEffect` call)
- Cleanup: game end restores AI on living locked mobs

### Phase 2

- held permanent `DISABLE_AI` skip
- held `ON_DAMAGE_DEALT` + `hit_target` locks victim

---

## 9. Decision log

| Topic | Decision |
|-------|----------|
| Surfaces | Phase 1: `use_ability` + STAT; Phase 2: `held_effects` |
| Effect name | `DISABLE_AI` (new enum) |
| Duration unit | `duration_ticks` |
| Re-apply | Longer remaining wins |
| Mechanism | `LivingEntity.setAI(false)` only |
| Targets | `enemies` \| `hit_target` \| `attacker` |
| Default targets | `enemies` |
| Hostile filter | Skip players, allies, `SummonRegistry` summons |
| Unload | Restore AI while entity still valid, then drop registry |
| `ON_KILL` + `hit_target` | warn+skip (noop on corpse) |
| executeEffect | Must join DAMAGE_AREA/BUFF_AREA case → executeMagicEffect |
| PlayerState | Not used for mob lock |
| Damage cancel / pin | Deferred |

---

## 10. Open questions

None for v1 — resolved in brainstorming + review.
