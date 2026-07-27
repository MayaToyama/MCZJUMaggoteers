# Configurable Magic Weapon System Design

**Status**: Pending implementation  
**Version**: v1.2 (2026-07-27) - v1.1 + section 4.1, HEAL_AREA targets validation, magic damage re-entry guard — review pass: virtual stats, targets, multiplier formula, FX/CD/execution contracts  
**Summary**: Data-driven magic weapon system. Main-hand right-click runs item `use_ability`. Reward STAT **forbids** `ON_INTERACT` as `fireTrigger`. Virtual stat `MAGIC_DAMAGE` (not Bukkit). Weapon progression via WEAPON rewards + STAT multiplier stacking.

---

## 1. Goals

### 1.1 Problems

- Magic weapons use Java handlers with hardcoded damage/range/CD, separate from `EffectService` reward passives.
- Classes need stronger magic gear from Act pools without per-weapon Handler code.
- Build needs spell power: `final = base × multiplier`, stacked via distinct reward ids with `stack: ADD`.

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| Zero **per-weapon** Handler | New weapon = `items/*.yml` + `rewards.yml` WEAPON; **no Java per item**. New **Effect shapes** (BEAM, etc.) still require one-time Java. |
| Unified damage core | Weapon and reward magic damage share one implementation (see §7.3). |
| Separation | Right-click = weapon config; 3-choice = combat `fireTrigger` + permanent stat/aura/potion. |
| Gear progression | Starter weapon → stronger WEAPON rewards per act. |

### 1.3 Out of scope (this phase)

- Dynamic Lore / scoreboard for spell power (P5)
- Active AURA (`fireTrigger` + expiry) in reward pools — **AURA rewards permanent only** (no `trigger`, no `expiry`)
- Off-hand weapon abilities
- `HEAL_POWER` / heal multiplier virtual stat (**HEAL_AREA uses base `amount` only**; see §5.3)
- Replacing optional `registerHandler` escape hatch (see §9.2) — kept for pre-Effect complex weapons

---

## 2. Architecture

**Weapon path**: `items/*.yml` `use_ability` → main-hand right-click → `ItemInteractRouter` → `ConfigMagicHandler` → `EffectService.executeAbility` (CD/FX only) → shared magic damage/heal core.

**Reward path**: `rewards.yml` STAT → `PlayerState.effects` → `fireTrigger` / permanent → `executeEffect` → **same shared core**.

| Data | Source | Notes |
|------|--------|-------|
| Weapon ability | `ItemAbilityRegistry` | By `maggoteers:id`; NOT in PlayerState |
| Reward effects | `PlayerState.effects()` | Existing `PlayerEffect` model |
| Spell multiplier | Virtual stat aggregation (§6) | NOT Bukkit `Attribute` |

---

## 3. Trigger boundaries

### 3.1 Weapons (main hand only)

- ON_INTERACT semantics; NOT a reward `fireTrigger`.
- Requires: main hand, `use_ability` for itemId, `PlayerState.isAlive()`.
- **Remove** tier-3: scan PlayerState for ON_INTERACT + `fireTriggerPlayer`.

### 3.2 Reward STAT — `fireTrigger` whitelist

**Allowed `fireTrigger`** (when applying STAT):

| Value | Example |
|-------|---------|
| *(omit)* | Permanent `ADD_ATTRIBUTE`, `ADD_POTION`, `AURA` |
| `ON_KILL` | Kill shockwave `DAMAGE_AREA`, lifesteal `HEAL` |
| `ON_DAMAGE_DEALT` | Next-hit buff (often with `expiry`) |
| `ON_DAMAGE_TAKEN` | On-hit retaliation |

**Forbidden as `fireTrigger`**: `ON_INTERACT`, `ON_WAVE_CLEAR`, `ON_ACT_ENTER`, `ON_GAME_END`, `ON_REVIVE`, `ON_DEATH`, `ON_TICK_1S`.

`RewardService.load` **must reject or warn+skip** forbidden `fireTrigger` at load time (**ship with P3**, same phase as deleting `a2s_damage_area_interact`).

### 3.3 Reward STAT — `expiry` (separate from fireTrigger)

**Allowed `expiry.trigger`** (when effect ends):

- `ON_WAVE_CLEAR`, `ON_ACT_ENTER`, `ON_DAMAGE_DEALT`, `ON_DAMAGE_TAKEN`, `ON_KILL`, etc.
- Example: `fireTrigger: ON_DAMAGE_DEALT` + `expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }` = next attack only.

**Forbidden for reward AURA in v1**: any `trigger` or `expiry` on `effect: AURA` (permanent carrier only; aligns with team-aura spec for 3-choice pools).

**Forbidden**: `expiry.trigger: ON_INTERACT` (same rationale as fireTrigger).

### 3.4 `Trigger.ON_INTERACT` enum

Keep enum (weapon-only). Weapon YAML uses `use_ability`; no `trigger` field on items.

---

## 4. `use_ability` schema

### 4.1 Item YAML shape

```yaml
maggoteers:example_blade:
  material: IRON_SWORD
  pdc:
    - key: maggoteers:id
      type: STRING
      value: maggoteers:example_blade
  use_ability:
    cooldown_sec: 3
    effect: DAMAGE_AREA
    params:
      damage: 6.0
      radius: 6.0
      targets: enemies
      enemy_scope: tracked
    fx:
      preset: SPIRAL_RADIUS
      particle: CRIT
      radius: 6.0
```

| Field | Notes |
|-------|-------|
| `cooldown_sec` | **Authoritative** gameplay CD (D2 `CooldownService`); see §4.2 |
| `effect` | Effect enum |
| `params` | Base values; per-effect schema §5; load validation §5.4 (HEAL_AREA) |
| `fx` | Optional; merge order §4.3 |

`ItemAbilityRegistry.load` parses this block per `maggoteers:id`. Missing `cooldown_sec` / unknown `effect` / invalid effect params -> warning + skip that ability (item still granted; right-click falls through router).

### 4.2 Cooldown: `cooldown_sec` vs ItemCreator `useCooldown`

| Layer | Role |
|-------|------|
| `use_ability.cooldown_sec` | **Single source of truth** for ability gate (`CooldownService.tryUse`). |
| ItemCreator `useCooldown` | Optional **vanilla hotbar overlay only**; if present, SHOULD equal `cooldown_sec` (content author responsibility). Plugin does NOT read `useCooldown` for logic. |

On ability use: only `CooldownService` blocks re-fire. Mismatch between visual bar and logic is a config bug, not dual enforcement.

### 4.3 FX merge order (three layers)

Later layers override earlier for each field:

```text
1. config.yml  magic_fx.defaults
2. config.yml  magic_fx.weapons.<itemId>   (optional per-weapon defaults)
3. items YAML  use_ability.fx              (authoritative for weapons)
```

**Transition**: legacy top-level `use_fx` on item = **alias** of `use_ability.fx`. If both exist, `use_ability.fx` wins; if only `use_fx`, treat as layer 3 with deprecation warning. Goal: delete standalone `use_fx` after migration.

Weapon execution plays FX from merged `MagicUseFx` before damage/heal core runs.

---

## 5. Effect extensions

### 5.1 `targets` / `enemy_scope` (DAMAGE_AREA, DAMAGE_BEAM)

**Same vocabulary as AURA** (`2026-07-24-team-aura-effect-design.md`):

| `targets` | Who can be hit/healed |
|-----------|------------------------|
| `enemies` | Non-player living entities per `enemy_scope` |
| `allies` | Same-game alive adventure-mode players |
| `both` | Union of both rules |

| `enemy_scope` | When `targets` includes enemies |
|---------------|----------------------------------|
| `tracked` (**default**) | `WaveEngine.isTracked(uuid)` mobs only |
| `all_living` | Any non-player `LivingEntity` in range |

**Weapon defaults** (if omitted in params): `targets: enemies`, `enemy_scope: tracked`.

**Current bug being fixed**: legacy `EffectService.DAMAGE_AREA` hits all living except self (includes allies). New core **must** respect `targets` — allies never damaged when `enemies`.

**Reward kill shockwave** (`a1s_damage_area`): recommend explicit `targets: enemies` + `enemy_scope: tracked` in YAML.

### 5.2 `DAMAGE_AREA` (reuse)

- Params: `damage`, `radius`, optional `targets`, `enemy_scope`.
- Magic damage × multiplier (§6).
- FX from caller context (weapon merged fx; rewards may use default ring particle).

### 5.3 `DAMAGE_BEAM` (new)

```yaml
params:
  damage: 14.0
  ray_length: 32.0      # default 32.0
  beam_radius: 1.25     # default 1.25
  targets: enemies      # default enemies
  enemy_scope: tracked  # default tracked
```

**Behavior** (parity `ExcaliburHandler`):

1. Origin: player eye location; direction: look vector.
2. If `getTargetEntity(ray_length)` hits a living entity, `length = min(ray_length, distance to target center)`; else full `ray_length`.
3. Sample `steps = max(16, length * 2)` points along ray; each point `nearbyEntities(beam_radius)`; each entity damaged once.
4. FX: `AIM_RAY` along same length/direction.
5. Apply magic multiplier to `damage`.

### 5.4 `HEAL_AREA` (new)

```yaml
params:
  amount: 6.0           # HP units (same as HEAL)
  radius: 5.0
  targets: allies       # load-validated; see below
  include_self: true    # default true
```

- Uses `AllyTargeting.alliesInRadius` (same as healing staff today).
- **Does NOT** apply `MAGIC_DAMAGE` or any heal multiplier in v1.
- **Does NOT** reserve `HEAL_POWER` — explicitly out of scope; future spec would add a separate virtual stat.

**Load-time `targets` validation** (`ItemAbilityRegistry` + `RewardService` for STAT entries):

| `params.targets` | `include_self` | Result |
|------------------|----------------|--------|
| `allies` | any (default true) | **Accept** |
| `both` | `true` | **Accept** (heal path applies to allies only) |
| `both` | `false` / omitted | **warn + skip** |
| `enemies` | any | **warn + skip** |
| omitted | — | **Accept** -> runtime default `allies` + `include_self: true` |

Skip = ability not registered / reward option not loaded. Do not silently heal wrong teams at runtime.

### 5.5 Magic damage formula

```text
finalDamage = baseDamage × magicDamageMultiplier(player, game)
```

**Applies to**: `DAMAGE_AREA`, `DAMAGE_BEAM`, and reward-triggered equivalents.  
**Does not apply to**: melee/`ATTACK_DAMAGE`, `HEAL`, `HEAL_AREA`, AURA grants.

---

## 6. Virtual stat `MAGIC_DAMAGE` (blocking decision)

### 6.1 Problem

Today: `RewardOption.putAttr` → `GameRegistries.attribute` → `EffectService.applyAttribute`. Unknown names log warning and `attr == null` → **silent discard**. `MAGIC_DAMAGE` is not a Bukkit attribute.

### 6.2 Chosen approach: `ADD_ATTRIBUTE` + virtual stat registry (not fake Bukkit)

Do **not** add a separate `VIRTUAL_STAT` effect for v1 (avoids duplicate reward schema). Instead:

1. **`VirtualStats`** enum/registry: `MAGIC_DAMAGE` (extensible later).
2. **`GameRegistries.isVirtualAttribute(String)`** / **`VirtualStats.parse(name)`** — if virtual, skip Bukkit resolution.
3. **`RewardOption.putAttr`**: always store `ATTR_NAME`; set `EffectKeys.ATTR` only when Bukkit attr resolves; virtual names store `ATTR_NAME` only.
4. **`EffectService.apply` / `applyDerived`**: if virtual → **only** merge into `PlayerState.effects()`; **do not** call `applyAttribute`.
5. **`PlayerCombatStats.magicDamageMultiplier(game, uuid)`**: scan permanent + non-expired `PlayerEffect` where `effect == ADD_ATTRIBUTE` and `ATTR_NAME == MAGIC_DAMAGE`.

Config shape unchanged for authors:

```yaml
effect: ADD_ATTRIBUTE
params: { attr: MAGIC_DAMAGE, op: PERCENT, value: 0.10 }
stack: ADD
```

### 6.3 Multiplier formula (fixed)

Only **`op: PERCENT`** supported for `MAGIC_DAMAGE` in v1. **`op: FLAT` rejected** at load (warning + skip option) or treated as 0 with warning.

For each qualifying `PlayerEffect` entry (after merge/stack):

```text
magicDamageMultiplier = 1.0 + Σ effectiveValue
```

- **`stack: ADD`**: each layer is a separate `PlayerEffect`; sum each layer's `params.value`.
- **`stack: UPGRADE_LEVEL`**: one entry; effectiveValue = `value + (level - 1) * step` if YAML uses amp-style stepping, OR store upgraded value in params on merge (same as resistance upgrade today). For MAGIC_DAMAGE rewards, **prefer ADD-only** (no UPGRADE_LEVEL entries).

**Example**: three picks +10%, +15%, +10% → multiplier = 1.0 + 0.10 + 0.15 + 0.10 = **1.35**.

Not multiplicative `(1.1 × 1.15 × 1.1)` unless we explicitly change later.

### 6.4 Bukkit vs virtual summary

| `attr` | PlayerState | Bukkit modifier |
|--------|-------------|-----------------|
| `ATTACK_DAMAGE`, etc. | yes | yes (`applyAttribute`) |
| `MAGIC_DAMAGE` | yes | **no** |

---

## 7. Runtime

### 7.1 `ItemInteractRouter`

1. ItemKind handlers (currency, revive, etc.) — unchanged.  
2. If `registerHandler` has entry for itemId → **handler wins** (escape hatch, §9.2).  
3. Else if `ItemAbilityRegistry` has ability → `ConfigMagicHandler`.  
4. Else — no cancel; vanilla interaction.

**Remove**: tier-3 ON_INTERACT PlayerEffect fallback.

### 7.2 `ConfigMagicHandler`

```text
CooldownService.tryUse(itemId, use_ability.cooldown_sec)
→ MagicFxService.play(merged fx)
→ EffectService.executeAbility(player, game, ability)
```

### 7.3 `executeAbility` vs `executeEffect` — no fork

```text
executeAbility(...)  // weapon entry: optional fx already played
  → executeMagicEffect(player, game, effect, params, fxContext)

executeEffect(...)   // reward entry: existing switch
  → same executeMagicEffect(...) for DAMAGE_AREA / BEAM / HEAL / HEAL_AREA / ...
```

**Single private core** (`executeMagicEffect` or equivalent):

- Resolve targets via shared helper (`TargetResolver` aligned with AURA ally/enemy rules).
- Apply `PlayerCombatStats.magicDamageMultiplier` for magic damage effects.
- `DAMAGE_AREA`, `DAMAGE_BEAM`, `HEAL_AREA` logic lives **only here**.
- **Same-tick re-entry guard** for magic damage (see §7.4).

Weapons must not duplicate damage loops in handlers.

### 7.4 Magic damage re-entry guard

**Problem**: `LivingEntity.damage(damager=player)` from `DAMAGE_AREA` / `DAMAGE_BEAM` raises `EntityDamageByEntityEvent` -> `EffectListener` fires `ON_DAMAGE_DEALT` -> a reward with `fireTrigger: ON_DAMAGE_DEALT` + `effect: DAMAGE_AREA` could recurse on the same tick. Current `rewards.yml` pools do **not** include this combo today; implement guard anyway.

**Pattern** (mirror existing `EffectService.FIRING` for `(game, trigger)`):

```text
Before dealing magic damage in executeMagicEffect:
  mark MagicDamageContext active for (gameId, sourcePlayerUuid, currentTick)

EffectListener.onDamageDealt / fireTrigger(ON_DAMAGE_DEALT):
  if MagicDamageContext active for (game, damager) on this tick -> skip fireTrigger for that damager

After executeMagicEffect completes (finally):
  clear mark
```

- Guard scope: **per source player, per tick** - suppresses `ON_DAMAGE_DEALT` only while that player's magic damage is applying; normal melee unchanged.
- Weapon `executeAbility` and reward `executeEffect` both use the same core.
- Unit test: `ON_DAMAGE_DEALT` -> `DAMAGE_AREA` -> no nested `ON_DAMAGE_DEALT` dispatch same tick.

---

## 8. Dual progression

| Line | Source |
|------|--------|
| Weapon panel | WEAPON rewards → higher base params / new effect type |
| Spell build | STAT `MAGIC_DAMAGE` ADD stacks |

```yaml
- { id: class_striker, category: WEAPON, item: maggoteers:test_blade }
- { id: a1s_flame_cleaver, category: WEAPON, item: maggoteers:flame_cleaver }
- { id: a2b_excalibur, category: WEAPON, item: maggoteers:excalibur }
```

---

## 9. Migration

### 9.1 Remove / replace

- Delete `TestBladeHandler`, `ExcaliburHandler`, `HealingStaffHandler` after config parity.
- Remove default `registerHandler` registrations for those three from `MaggoteersPlugin`.
- Delete `a2s_damage_area_interact` from `rewards.yml`.
- Add explicit `targets`/`enemy_scope` to kill-shockwave rewards.
- Merge `use_fx` → `use_ability.fx`.

### 9.2 Escape hatch: optional `registerHandler`

**Not** the default path for new content. Retained for weapons that cannot yet be expressed as an Effect (complex projectiles, multi-phase skills):

- `ItemInteractRouter.registerHandler(itemId, handler)` remains **public**.
- If registered, handler **overrides** config `use_ability` for that id.
- CLAUDE §15 updated to: prefer `use_ability`; handler only when Effect system insufficient.
- Zero **new** handlers for migrated whirlwind / Excalibur / staff.

---

## 10. Phases (revised)

| Phase | Work |
|-------|------|
| P1 | `VirtualStats` + `PlayerCombatStats` + multiplier formula + shared magic damage core on `DAMAGE_AREA` with targets |
| P2 | `ItemAbilityRegistry` + `ConfigMagicHandler` + `executeAbility` wrapper + FX merge |
| P3 | `DAMAGE_BEAM` + `HEAL_AREA` + migrate three weapons + **delete ON_INTERACT reward + load-time fireTrigger/expiry validation** |
| P4 | Pool content (`MAGIC_DAMAGE` entries, WEAPON tiers) |
| P5 | Lore/scoreboard (deferred) |

---

## 11. Tests

- `PlayerCombatStatsTest`: Σ PERCENT → multiplier; virtual attr not in Bukkit.
- `TargetResolverTest`: enemies/tracked excludes allies and untracked mobs.
- `DAMAGE_BEAM`: length shortens to target entity; defaults 32 / 1.25.
- `RewardServiceTest`: reject `fireTrigger: ON_INTERACT`; reject `AURA` with trigger/expiry; reject `MAGIC_DAMAGE` FLAT.
- Registry: FX merge order; `cooldown_sec` required.
- Manual: main/off-hand, weapon swap, ally not hit by enemy-targeted blade.

---

## 12. Doc sync

- `CLAUDE.md` §10 / §15: virtual stats, `use_ability`, handler escape hatch.
- `.cursor/skills/maggoteers-plugin-config/reference.md`: full schema.
- `docs/dev-log.md`: implementation note.

---

## Appendix: review checklist (v1.2)

| Issue | Resolution |
|-------|------------|
| MAGIC_DAMAGE vs Bukkit | Virtual stat registry; ADD_ATTRIBUTE special-case; no Bukkit write |
| targets semantics | AURA-aligned; default enemies + tracked |
| Multiplier formula | `1.0 + Σ(PERCENT value)`; FLAT unsupported v1 |
| FX layers | defaults → config weapons → use_ability.fx; use_fx alias |
| CD dual track | cooldown_sec authoritative; useCooldown cosmetic only |
| executeAbility fork | Single `executeMagicEffect` core |
| registerHandler | Optional override escape hatch |
| expiry vs fireTrigger | Both documented; AURA permanent only in pools |
| Validation timing | P3 with ON_INTERACT reward removal |