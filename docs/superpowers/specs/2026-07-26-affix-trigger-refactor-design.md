**Status:** Superseded by `2026-07-26-infernal-mobs-wave-integration-design.md`

# Affix Trigger Refactor

**Date:** 2026-07-26  
**Status:** Superseded — native `on:` combat affixes retired; IM integration via `infernal:` blocks  
**Related:** `affixes.yml`, `AffixService`, `MobFactory`, `RunPlanner`, `InfernalMobsBridge`

---

## 1. Problem

`on: hit` is currently implemented as "player hits mob -> buff the **player**", which contradicts config comments and design intent for affixes like `hidesuwa` (mob invisibility on damage).

The `on` field is duplicated as raw strings in three places without load-time validation.

---

## 2. Goals

1. Define and implement three combat triggers plus default spawn trigger.
2. Introduce `AffixTrigger` enum and fail-fast parsing in `AffixService`.
3. Centralize combat dispatch in `AffixCombatService`.
4. Migrate all existing `on: hit` entries in `affixes.yml`.
5. Add unit tests for enum parsing and trigger filtering.

**Out of scope:** new Effect types; waves.yml changes; affix display rendering.

---

## 3. Trigger semantics

| `on` | Event | Condition | Buff target |
|------|-------|-----------|-------------|
| *(omit)* | spawn | entity created | **mob** |
| `hit-player` | `EntityDamageByEntityEvent` | tracked mob damages player | **player** |
| `hit-by-player` | `EntityDamageByEntityEvent` | player (incl. projectile) damages tracked mob | **player** |
| `hit` | `EntityDamageEvent` | tracked mob takes **any** damage | **mob** |

### 3.1 Stacking

One player melee on a mob with `poisonous` + `hidesuwa`:

- `hit-by-player` -> poison on player
- `hit` -> invisibility on mob

Both apply in the same hit.

### 3.2 Any damage source for `hit`

Listen on `EntityDamageEvent` (MONITOR, ignoreCancelled=true): melee, projectiles, fall, fire, lava, suffocation, etc.

Only applies to tracked in-game mobs (`WaveEngine.isTracked`).

### 3.3 Affix resolution

Reuse `WaveEngine.resolveCombatAffixes(entity)`: direct entity -> projectile shooter -> mount -> mounted squad merge.

---

## 4. Config migration

```yaml
poisonous:  on: hit-by-player   # thorns poison on attacker
sticky:     on: hit-by-player   # slow attacker
hidesuwa:   on: hit             # mob hides when damaged
teleport:   on: hit             # mob invis + resistance when damaged
cataclysm:  on: hit             # mob brief resistance when damaged
```

Add a comment block at top of `affixes.yml` documenting all four triggers.

---

## 5. Architecture

```
config/AffixTrigger.java
config/Affix.java              # on: AffixTrigger
config/AffixService.java       # AffixTrigger.parse; unknown on -> fail-fast

effect/AffixCombatService.java # applyToPlayer / applyToMob
listener/CombatAffixListener.java

mob/MobFactory.java            # skip combat-trigger potions at spawn
plan/RunPlanner.java           # skip combat-trigger potions in step.potions
```

### AffixTrigger enum

```java
SPAWN(null),
HIT_PLAYER("hit-player"),
HIT_BY_PLAYER("hit-by-player"),
HIT("hit");
```

- `parse(null/blank)` -> SPAWN
- unknown string -> IllegalStateException with affix id
- `isCombatTriggered()` -> != SPAWN

### CombatAffixListener

| Handler | Event | Action |
|---------|-------|--------|
| entity damage by entity | player victim | hit-player -> player |
| entity damage by entity | tracked mob victim + player attacker | hit-by-player -> player |
| entity damage | tracked LivingEntity victim | hit -> mob |

Player checks: in Maggoteers game via `PlayerExt`.

---

## 6. Spawn pipeline

Unchanged rule: combat-trigger affix potions are **not** applied at spawn and **not** merged into RunPlanner `step.potions`.

Only affixes with `on` omitted (SPAWN) or non-combat triggers get spawn-time potions.

---

## 7. Error handling

| Case | Behavior |
|------|----------|
| unknown `on` in affixes.yml | fail on enable |
| untracked entity | ignore |
| player outside game | skip player buffs |
| unknown potion effect name | skip that potion |

---

## 8. Tests

| File | Cases |
|------|-------|
| `AffixTriggerTest` | parse all values; unknown throws; isCombatTriggered |
| `AffixCombatServiceTest` | filter by trigger |
| update existing tests | Affix constructor with AffixTrigger |

---

## 9. Acceptance

1. `hit-player` (quicksand) slows player when mob hits.
2. `poisonous` poisons player when player hits mob.
3. `hidesuwa` gives mob invisibility on any damage (incl. fall).
4. `sticky` slows player, not mob.
5. Unknown `on` fails server startup.
6. `mvn test` passes.

---

## 10. Self-review

- [x] No TBD placeholders
- [x] `hit` uses EntityDamageEvent for all damage sources
- [x] affixes.yml remains single source of truth
- [x] Scope limited to trigger refactor + yaml migration
- [x] hit + hit-by-player stacking documented
