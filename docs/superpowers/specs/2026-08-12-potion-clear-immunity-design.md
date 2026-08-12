# Potion Clear & Immunity Design (Silence / Purify)

> **Status:** Implemented (v1.1)
> **Date:** 2026-08-12
> **Revision:** v1.1 — blocking review: deployed YAML hard-fail, clear-only BUFF_AREA, DAMAGE_AREA order, immunity type/lifecycle, listener/tests
> **Replaces:** D1 `ADD_POTION` amp>=255 cancel trick (CLAUDE.md §10.2 / Plan 6 Task 6)
> **Depends on:** `EffectService` / `BUFF_AREA` / `DAMAGE_AREA` / `use_ability` `self_potions` / `PurifyListener` / `PluginFiles.saveResourceIfMissing`

**Summary:** Stop using 1-tick / 0s amp-255 as fake clear or immunity. One-shot clear uses `clear_potions` / `self_clear_potions` (`removePotionEffect`). Run-long immunity uses `ADD_POTION` + `immunity: true` (strip + block re-apply + poison/wither damage fallback). Weapon silence = one-shot; reward purify = run immunity; holy water / phantasm self-cleanse = one-shot. Removing the Java 255 special-case is **unsafe** unless loaders hard-fail leftover fake-255 configs and operators sync deployed YAML (`saveResource(..., false)` never overwrites).

---

## 1. Goals

### 1.1 Problem

Silence / purify configs use `amp: 255` with tiny `duration_ticks`. Via `PotionMerge` / `applyPotionPermanent` this **applies** that potion (permanent path refreshes 30s x 255 every second). Continuous application is indistinguishable from a real amp-255 effect. `PurifyListener` expects effect ids `immunity_*`, which do not match reward ids (`a3w_imm_*`).

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| Real clear | After silence / holy water / phantasm, cleared types are gone; no amp-255 flash |
| Real immunity | While a valid immunity effect is active, that non-instant `PotionEffectType` cannot re-apply; POISON/WITHER damage cancelled |
| Coexist | `clear_potions` and real `potions[]` in one cast (EMP: strip buffs + apply slowness) |
| Clear-only BUFF_AREA | `clear_potions` alone is valid; resolves targets and clears without requiring `potions[]` |
| Safe deploy | Leftover fake-255 in rewards/items **hard-fails** load; jar ships migrated resources; deploy steps sync server copies |
| No new Effect enum | Extend `BUFF_AREA` / `DAMAGE_AREA` / `ADD_POTION` / `use_ability` fields |
| Kill D1 255 branch | Remove `amp >= 255` special-case in `EffectService`; 255 means real high amp only |

### 1.3 Non-goals

- Do not add `CLEAR_POTION` / `POTION_IMMUNITY` Effect types.
- Do not change `affixes.yml` mob spawn `amp: 255` (affix semantics; separate task if mob immunity is needed).
- No timed silence aura that keeps blocking; weapon silence is one-shot on hit.
- No `clear_preset` macros (lists are explicit in YAML).
- No automatic rewrite of files already under `plugins/Maggoteers/` (operators sync; loader hard-fails if stale).

---

## 2. Semantics (approved)

| Use case | Behavior | Config |
|----------|----------|--------|
| Weapon silence (cataclysm, EMP) | One-shot `removePotionEffect` | `params.clear_potions` |
| Holy water self-cleanse | One-shot strip debuffs | `params.clear_potions` (`BUFF_AREA` targets self) |
| Phantasm etc. | One-shot strip + other real potions/temp effects | `self_clear_potions` + existing `self_potions` / `params` |
| Reward immunity (`a3w_imm_*`) | Run-long immunity to that potion | `ADD_POTION` + `immunity: true` (strict lifecycle; see §3.3) |

---

## 3. Config contract

### 3.1 `clear_potions` (area effects)

On `DAMAGE_AREA` / `BUFF_AREA` `params`:

```yaml
params:
  clear_potions: [STRENGTH, SPEED, INVISIBILITY, FIRE_RESISTANCE, RESISTANCE, REGENERATION]
  potions:
    - { potion: SLOWNESS, amp: 9, duration_ticks: 200 }  # optional real buffs
```

- Type: string list -> `PotionEffectType` (case-insensitive, same resolver as `potion:`).
- **Unknown type names hard-fail** load with resource path, item/reward id, and field path (e.g. `params.clear_potions[2]`). Do not silently drop.
- Does not write PlayerState; does not block later re-application.

**BUFF_AREA emptiness rule (replaces potions-required):**

- Valid iff `clear_potions` non-empty **OR** `potions` non-empty (at least one).
- Clear-only (`clear_potions` set, `potions` absent/empty) is **legal**.
- Validators (`MagicEffectParams` / `WeaponEffectValidator` / reward BUFF_AREA checks) must accept clear-only; reject only when both empty.

**DAMAGE_AREA:** may omit both (damage-only, as today). If `clear_potions` present, apply per §4.1 order.

### 3.2 `self_clear_potions` (weapon side path)

Top-level `use_ability` field (parallel to existing `self_potions`):

```yaml
use_ability:
  effect: ADD_POTION
  self_clear_potions: [WITHER, SLOWNESS, WEAKNESS, BLINDNESS, POISON, DARKNESS]
  self_potions:
    - { potion: INSTANT_DAMAGE, amp: 0, duration_ticks: 1 }
  params: { potion: RESISTANCE, amp: 4, duration_ticks: 0 }
  expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }
```

- `removePotionEffect` on caster **before** `applySelfPotions`.
- Do not encode fake-255 clears inside `self_potions`.
- Unknown names: same hard-fail as §3.1 (item id + `self_clear_potions[i]`).

**Success / cooldown (§4.6):** `self_clear` is part of the ability body. Cooldown is charged only when the ability as a whole succeeds under existing handler rules; clarify implementation so clear does not run after CD if the main effect path will return failure without side effects — preferred: run clear + main effect as one unit, return success if clear ran **or** main effect succeeded when either was configured (see §4.6).

### 3.3 `immunity: true` (run-long ADD_POTION)

```yaml
effect: ADD_POTION
params:
  potion: WITHER
  immunity: true
```

**Legal only when all of:**

| Constraint | Rule |
|------------|------|
| Effect | `effect == ADD_POTION` |
| Flag | `params.immunity == true` |
| Trigger | `trigger` / `fireTrigger` omitted (`null`) |
| Expiry | `expiry` omitted (`null`) |
| Recurring | `recurring` omitted (`null`) |
| Potion type | Non-instant, duration-capable type (see below) |

`PlayerEffect.isPermanent()` only means `fireTrigger == null` and **does not** imply no expiry. Immunity must not rely on `isPermanent()` alone; validators and runtime helpers check **expiry == null && recurring == null && fireTrigger == null**.

**Allowed potion types:** any non-instant effect that can persist on an entity (e.g. `WITHER`, `POISON`, `SLOWNESS`, …).

**Rejected (hard-fail):**

- `INSTANT_DAMAGE`
- `INSTANT_HEALTH`
- Any other instant / non-status type the Paper API treats as instant (validator uses `PotionEffectType#isInstant()` or equivalent allow/deny list documented in code)

Rationale: `EntityPotionEffectEvent` + damage-cause fallback do not fully cover instant effects; do not promise immunity for them.

**Params:**

- Requires a valid resolvable `potion`.
- `amp` / `duration_ticks` ignored when `immunity: true`; if present, load **warn** (not fail) and still treat as immunity.
- Weapon `use_ability` must not use `immunity` for one-shot cleanse (use `self_clear_potions` / `clear_potions`).

### 3.4 Deprecated patterns — **hard-fail** (blocking)

| Old | New |
|-----|-----|
| `potions: [{ potion: X, amp: 255, duration_ticks: 1 }]` meant as clear | `clear_potions: [X]` |
| `self_potions: [{ potion: X, amp: 255, duration_ticks: 1 }]` meant as clear | `self_clear_potions: [X]` |
| `ADD_POTION` `amp: 255` + `duration_ticks: 0` meant as immunity | `immunity: true` |

**Load hard-fail** on `rewards.yml` and `items/*.yml` (not affixes) when any entry matches:

- `amp == 255` **and** `duration_ticks` ∈ `{0, 1}` **and** not `immunity: true`

Error message must include resource path, reward/item id, and field location. Plugin must refuse to finish loading that config surface (skip option / fail enable path consistent with existing validator severity for fatal reward/item errors).

**Why hard-fail (not warn):** `PluginFiles.saveResourceIfMissing` / `saveResource(..., false)` never overwrites existing `plugins/Maggoteers/` files. After Java removes the 255 special-case, stale deployed YAML would apply **real** WITHER/POISON/SLOWNESS 255 (or 1-tick 255 buffs). Warning is insufficient.

**No in-plugin one-shot file migrator** in this design (YAGNI). Operators sync files; CI/tests use jar resources; hard-fail catches forgotten server copies.

### 3.5 Deploy / ops (blocking)

Implementation plan and `maggoteers-build-deploy` notes **must** require:

1. Ship migrated `src/main/resources/rewards.yml` and `items/maggoteers.yml` in the jar.
2. On test/prod: **overwrite or manually merge** `plugins/Maggoteers/rewards.yml` and `plugins/Maggoteers/items/maggoteers.yml` (and any other items files with fake-255). Copying only the jar is **not** enough.
3. Restart / reload config path; confirm enable log has **no** fake-255 hard-fail.
4. Optional checklist item in build-deploy skill / CLAUDE deploy section.

---

## 4. Runtime

### 4.1 One-shot clear and area order

**BUFF_AREA** (after target resolve):

```
if (clear empty && potions empty) return; // validator should already reject
for each target:
  for type in clear_potions: removePotionEffect(type)
  for spec in potions: PotionMerge.applyIfNeeded(...)
```

Clear-only: still resolve targets (and play mark/fx as today when configured), then clear only.

**DAMAGE_AREA** — per target, **fixed order** (compatible with current damage-first behavior; explicit for Resistance strip):

```
for each target:
  1. le.damage(dmg, p)           // current damage
  2. if dead/invalid: continue
  3. clear_potions                 // strip (e.g. RESISTANCE) AFTER this hit
  4. potions via PotionMerge       // optional apply
```

Rationale: cataclysm strips `RESISTANCE`. **Damage → clear → potions** keeps this cast’s 20 damage subject to Resistance that was already on the target (legacy-compatible). Clear-before-damage would make the same hit ignore Resistance and change balance; **rejected**.

Weapons: at start of ability body, `self_clear_potions` then `self_potions` (see §4.6 for success).

### 4.2 Immunity apply / resync

When an effect matches §3.3 immunity legality:

1. `removePotionEffect(type)`.
2. Do **not** `addPotionEffect`.
3. `refreshPermanentPotions` skips immunity entries (never applies).

On effect remove: stop blocking that type; do not restore previously cleared potions. (Immunity options have no expiry by contract; remove only via admin/debug/clearEffects/game end.)

### 4.3 Block re-application

Shared helper (required):

```java
// e.g. PotionImmunity.isImmune(Player player, PotionEffectType type)
```

Single place that inspects in-game `PlayerState` for §3.3-legal immunity matching `type`. **Do not** duplicate PlayerState queries in `PotionMerge`, `AuraService`, and `EffectService`.

1. **Listener** (new or folded into purify listener):
   - Event: `EntityPotionEffectEvent`
   - Priority: `EventPriority.HIGHEST`, `ignoreCancelled = true`
   - Cancel when action is `ADDED` or `CHANGED` and `PotionImmunity.isImmune(player, type)`
   - Do **not** cancel `REMOVED` or `CLEARED` (Paper action names — not `ADD` / `REMOVE`)
2. **Internal gate:** every plugin path that would `addPotionEffect` on a player calls `PotionImmunity.isImmune` first and skips on true. Mobs have no player immunity table.

### 4.4 Damage fallback

Rewrite `PurifyListener`:

- Drop effect-id convention `immunity_<cause>`.
- Use `PotionImmunity` (or shared predicate): cancel `DamageCause.POISON` / `WITHER` when immune to `POISON` / `WITHER`.
- Types without a damage cause (e.g. `SLOWNESS`) rely only on §4.3.

### 4.5 Remove D1 hack

Delete `amp >= 255` branches in `EffectService.applyPotionPermanent` / `refreshPermanentPotions`. Afterward `amp: 255` means a real amp-255 potion (same as affixes). **Only ship after §3.4 hard-fail + §3.5 deploy sync are in place.**

### 4.6 `executeAbility` success and self-clear

Today cooldown is typically charged before / around execution; partial success (self-clear ran, main effect failed, CD consumed) is confusing.

**Contract:**

- Treat `self_clear_potions` / `self_potions` / main `effect` as one ability invocation.
- Prefer: perform clear + self potions + main effect; return `true` if **any configured** side produced work **or** main effect returns true; return `false` only if nothing applicable ran (misconfig already load-failed) or main effect hard-failed **and** no clear/self_potions were configured.
- If clear-only side path is configured (`self_clear_potions` non-empty) and clear executes, that counts as success for cooldown purposes even when main effect is a no-op edge case — document in handler.
- Implementation plan should align `ConfigMagicHandler` CD charge with this boolean (no CD on total failure).

---

## 5. Migration inventory

| Asset | Change |
|-------|--------|
| `maggoteers:cataclysm` | fake 255 x6 -> `clear_potions` (silence list) |
| `maggoteers:emp` | fake 255 x6 -> `clear_potions`; keep `SLOWNESS` in `potions[]` |
| `maggoteers:holy_water` | fake 255 debuff strip -> `clear_potions` (purify list); clear-only or clear+heal as configured |
| `maggoteers:phantasm` | `self_potions` fake 255 -> `self_clear_potions`; keep real self-damage entries |
| `rewards.yml` `a3w_imm_wither` / `_poison` / `_slow` | -> `immunity: true`; drop fake amp/duration; ensure no trigger/expiry/recurring |
| `plugins/Maggoteers/*` on servers | **Manual sync** of `rewards.yml` + `items/maggoteers.yml` (see §3.5) |
| `affixes.yml` | **no change** (fake-255 hard-fail does **not** apply to affixes) |

Silence list: `STRENGTH, SPEED, INVISIBILITY, FIRE_RESISTANCE, RESISTANCE, REGENERATION`

Purify list: `WITHER, SLOWNESS, WEAKNESS, BLINDNESS, POISON, DARKNESS`

---

## 6. Java touchpoints

| Area | Work |
|------|------|
| `EffectKeys` | `IMMUNITY` (Boolean), `CLEAR_POTIONS` (`List<PotionEffectType>`); `ItemAbility` adds `selfClearPotions` |
| `PotionImmunity` | Shared `isImmune(Player, PotionEffectType)` (+ optional cause helpers) |
| Parsers | Parse `clear_potions` / `self_clear_potions`; parse `immunity`; unknown name hard-fail with path |
| Validators | BUFF_AREA: potions **or** clear_potions; fake-255 hard-fail; immunity lifecycle + non-instant; instant types rejected |
| `MagicEffectParams` / `EffectService.executeBuffArea` | Allow clear-only; resolve targets when potions empty |
| `EffectService` DAMAGE_AREA | Order: damage → clear → potions |
| `EffectService` / handler | Immunity apply; remove 255 hack; executeAbility success (§4.6) |
| `PotionMerge` / AURA | Call `PotionImmunity.isImmune` only (no duplicated state walks) |
| Listener | `EntityPotionEffectEvent` HIGHEST; ADDED/CHANGED cancel; REMOVED/CLEARED pass; rewrite `PurifyListener` |
| Docs / deploy skill | CLAUDE §10.2/§18; config reference; dev-log; **deploy sync checklist** |

---

## 7. Testing

### 7.1 Automated

- Unit: `clear_potions` parse; unknown name fails with id/path; fake-255 hard-fail conditions; immunity lifecycle reject (trigger/expiry/recurring/instant).
- Unit: BUFF_AREA validator accepts clear-only; rejects both-empty.
- Unit: DAMAGE_AREA ordering documented/tested via extracted helper if feasible (damage then clear then apply).
- Unit: `PotionImmunity.isImmune` true/false; skip apply when immune.
- **Event-level (MockBukkit or listener unit with mocked event):**
  - `ADDED` and `CHANGED` cancelled when immune
  - `REMOVED` / `CLEARED` not cancelled
  - After leaving game / effects cleared, apply allowed again
- Existing `PotionMerge` tests stay free of clear-semantics (clear does not use merge).

### 7.2 Manual

1. Mob with Strength + Resistance -> cataclysm: damage lands under existing Resistance, then Resistance/Strength cleared; no amp-255 icon.
2. EMP -> buffs stripped, Slowness remains ~10s.
3. Holy water clear-only -> debuffs stripped; ability not no-op.
4. Phantasm -> debuffs stripped; Resistance expiry still works.
5. Pick `a3w_imm_wither` -> no Wither apply, no wither damage; poison / slowness analogues; cannot configure `INSTANT_*` immunity.
6. Deploy stale YAML with fake-255 -> plugin load **hard-fails** with clear error; sync fixed files -> loads clean.
7. `mvn test` + deploy checklist §3.5.

---

## 8. Documentation follow-ups (implementation)

- `CLAUDE.md`: purify = `immunity: true` + event block; remove "0s 255 pending verification"; note deploy sync for rewards/items.
- `.cursor/skills/maggoteers-plugin-config/reference.md`: `clear_potions` / `self_clear_potions` / `immunity` + clear-only BUFF_AREA + hard-fail fake-255.
- `.cursor/skills/maggoteers-build-deploy/SKILL.md`: must copy/sync `rewards.yml` and `items/maggoteers.yml` to `E:/MCpaper/plugins/Maggoteers/`.
- `docs/dev-log.md`: D1 retirement and v1.1 review decisions.

---

## 9. Decisions log

| Decision | Choice | Why |
|----------|--------|-----|
| Silence vs immunity | Mixed: weapon one-shot; reward run-long | Different gameplay intent |
| API shape | Extend existing fields; no new Effect | Lower migration cost; coexist with DAMAGE/BUFF |
| Immunity impl | Event cancel + internal gate + damage fallback | Avoid tick-strip flicker and 1-tick damage |
| Stale deployed YAML | Hard-fail fake-255 + ops sync (no auto migrator) | `saveResource(false)` never overwrites; warn unsafe after removing 255 hack |
| Clear-only BUFF_AREA | potions **or** clear_potions required | Else schema lies; holy_water would no-op |
| DAMAGE_AREA order | damage → clear → potions | Preserve Resistance affecting this hit; explicit balance |
| Instant immunity | Forbidden (`isInstant` / deny list) | Event + damage fallback incomplete |
| Immunity lifecycle | no trigger, no expiry, no recurring | `isPermanent()` alone is insufficient |
| Event actions | `ADDED` / `CHANGED` cancel; `REMOVED` / `CLEARED` allow | Match Paper enum names |
| Listener priority | `HIGHEST`, `ignoreCancelled = true` | Win over apply races |
| Immunity queries | Single `PotionImmunity.isImmune` | No duplicated PlayerState walks |
| Unknown clear names | Hard-fail with path/id | No silent drop |
| Preset macros | No | Explicit lists; per-item tuning |
| affixes 255 | Out of scope; hard-fail excludes affixes | Not player silence/purify path |
