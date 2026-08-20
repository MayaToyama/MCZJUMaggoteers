# Multi use_ability + deferred next-hit FX Design

> **Status:** Implemented (framework) - author YAML checklist remains in section 7
> **Date:** 2026-08-13
> **Revision:** v1.1 — fire/expiry order, weaponPath deferred exec, shared TriggerContext, FX rules
> **Depends on:** `config-magic-weapon-system-design.md`, `2026-08-08-weapon-held-effects-design.md`, `2026-08-08-disable-ai-effect-design.md`
> **Delivery scope:** Framework + unit tests only. Author updates content YAML after merge (except intentional silent behavior change on `eighty_hammer`, see section 1.2 / 9).

**Summary:** Allow one weapon `use_ability` to declare multiple effect steps (`effects[]`), mixing **immediate** casts and **deferred** next-hit effects. Cast FX once on right-click; empower FX once on the hit that consumes the deferred pack (on victim). Legacy single `effect:` remains supported. `held_effects` unchanged.

---

## 1. Goals

### 1.1 Problems

- One item id maps to a single `ItemAbility` with one `effect`; authors cannot express EMP (silence + real stun) or Herafinger (next-hit damage buff + next-hit AoE slow) in config.
- `expiry` is only parsed for `ADD_ATTRIBUTE` / `ADD_POTION`. Magic effects with `expiry` (e.g. `DISABLE_AI` on eighty_hammer) are ignored so right-click applies immediately, contradicting lore.
- Temporary weapon buffs do not play FX when the empowered hit lands; authors want a clear strike flash on the victim.
- Today `fireTriggerPlayer` sweeps expiry **before** dispatching `fireTrigger == T`, so a deferred magic row stored as `fireTrigger = expiryTrigger = ON_DAMAGE_DEALT` would be removed before it can execute. Acceptance for next-hit magic cannot land without flipping that order.
- Old magic-weapon specs claimed `fireTrigger` + `expiry` both `ON_DAMAGE_DEALT` = next-attack-only, but that never worked under the current sweep-first order. This design closes that gap.

### 1.2 Success criteria

| Criterion | Detail |
|-----------|--------|
| Multi-step YAML | `use_ability.effects[]` with 2+ steps loads and runs |
| Legacy compat | Existing single `effect:` / `params:` / `expiry:` still loads as one-step pack |
| Immediate + deferred mix | Same right-click may run some steps now and store others until expiry trigger |
| Deferred beyond ADD_* | `BUFF_AREA`, `DISABLE_AI`, and other magic effects allowed with `expiry` (weapon path) |
| Fire-before-expiry | On trigger T: snapshot -> execute all fireTrigger==T -> then sweepExpiry for expiryTrigger==T + resyncDerived (same order for fireTriggerPlayer and game-wide fireTrigger) |
| Deferred magic = weapon path | Deferred magic executes with weaponPath=true, with TriggerContext.hitTarget from the melee event |
| Shared step context | Immediate multi-step right-click shares one mutable TriggerContext; DAMAGE_BEAM writes hit entity for later hit_target steps |
| CD rule | If any step succeeds (or top-level self_* side effects applied), enter CD |
| Cast FX once | Right-click success: one cast FX from top-level use_ability.fx (no per-effect template pick for multi packs) |
| Empower FX once | Deferred pack consumed on a trigger: one empower FX on victim (coalesced) |
| held unchanged | held_effects + use_ability continue to work together with no schema change |
| No content rewrite | Does not edit production weapon YAML in this PR |
| Intentional silent behavior | eighty_hammer only: without YAML edits, behavior becomes next-melee stun once deferred magic + fire-before-expiry land |

### 1.3 Out of scope

- Batch-rewriting items/maggoteers.yml content (author owns post-merge YAML, except silent eighty_hammer behavior above)
- Changing reward STAT option schema / multi-effect rewards
- Per-step independent cooldowns
- Playing a full cast FX for every step (noisy; rejected)
- Projectile ON_DAMAGE_DEALT (still melee-only catalog)
- New Effect enum values
- Requiring held weapon at consume time (deferred charges stay on the player, not the item)

---

## 2. Decisions (locked)

| Topic | Choice |
|-------|--------|
| Feature breadth | Multi immediate steps and multi deferred next-hit steps |
| YAML shape | Nested effects[] under one use_ability; keep legacy single effect: |
| CD on partial success | Any step success -> CD |
| Content in this PR | Framework + tests only (plus intentional eighty_hammer behavior change without YAML edit) |
| Cast FX | Once per successful right-click; top-level fx only (no step effect-template for multi) |
| Empower / consume FX | Once when deferred pack is consumed; play on victim via playOnEntity |
| Empower fallback | empower_fx → visible cast fx → deferred-only strike flash → else no play (never magic_fx.defaults) |
| Multi deferred same trigger | Coalesce empower FX to one play for the whole pack |
| Fire vs expiry order | Fire first, then expiry sweep (see 4.0) |
| Deferred exec path | Weapon path + melee TriggerContext (see 4.3.1) |
| Step context | Shared mutable TriggerContext across immediate steps; beam writes hit (see 4.2) |
| thunder_rod | In scope via shared context (not later) |
| Deferred default stack | REPLACE (align weapon temp; not D7 IGNORE) |
| Ability id match | Exact ability:<itemId> or prefix ability:<itemId>: (separator required) |
| Forbidden in steps | AURA, BOUND_EQUIP (and other non-weapon-ability effects) -> warn + skip |
| Deferred charge ownership | Follows player (swap weapon / death+revive keeps charge) |

---

## 3. YAML schema

### 3.1 Preferred (multi)

`yaml
use_ability:
  cooldown_sec: 10
  consume: false
  fx:
    preset: RIPPLE_RINGS
    particle: CRIT
  empower_fx:
    preset: DOT_ABOVE
    particle: CRIT
  self_clear_potions: []
  self_potions: []
  effects:
    - effect: ADD_ATTRIBUTE
      params:
        attr: ATTACK_DAMAGE
        op: PERCENT
        value: 1.0          # author content; live herafinger may stay 2.0 — not this PR
      expiry:
        trigger: ON_DAMAGE_DEALT
        charges: 1
      stack: REPLACE        # default for deferred steps if omitted
    - effect: BUFF_AREA
      params:
        radius: 3.0
        targets: enemies
        enemy_scope: tracked
        potions:
          - { potion: SLOWNESS, amp: 9, duration_ticks: 60 }
      expiry:
        trigger: ON_DAMAGE_DEALT
        charges: 1
      stack: REPLACE        # required default; do not inherit D7 IGNORE
`

### 3.2 Legacy (still valid)

`yaml
use_ability:
  cooldown_sec: 10
  effect: ADD_ATTRIBUTE
  params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 1.0 }
  expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }
  stack: REPLACE
  fx: { ... }
`

Loader normalizes legacy into a one-element internal step list.

### 3.3 Mutual exclusion / step rules

- If both effects: and top-level effect: are present -> load fail (warn + skip ability).
- Empty effects: -> skip ability.
- Each step requires effect:; step-level cooldown_sec forbidden (ignore + warn if present).
- Omitted step stack on deferred steps -> REPLACE.
- Immediate ADD_POTION with no expiry and duration_ticks > 0 keeps the existing instant-potion path; validate per step (skip bad step + warn, continue others; if zero steps remain valid, skip ability).
- Reject AURA / BOUND_EQUIP (and unsupported enums) in any step: warn + skip that step (or whole ability if none remain).

### 3.4 held_effects

Unchanged. Authors may combine passive on-hit via held_effects with right-click pack via use_ability.effects[]. Orthogonal to this design (e.g. Herafinger 1s on-hit Slowness is held, not effects[]).

---

## 4. Runtime model

### 4.0 LOCKED: fireTrigger vs expiry order

**Today (broken for same-trigger deferred magic):** sweepExpiry then dispatchTriggeredEffects.

**Required (both fireTriggerPlayer and game-wide fireTrigger):**

1. Snapshot PlayerState.effects() (or equivalent stable copy).
2. Dispatch all effects with fireTrigger == T (safe iteration so removals during dispatch do not skip siblings).
3. Then sweepExpiry for expiryTrigger == T, and if anything removed -> resyncDerived.
4. Recurring / other post-steps stay after this pair (game-wide path), unchanged in spirit.

**Why:** Deferred magic is stored as fireTrigger = ON_DAMAGE_DEALT (must execute on hit) and expiryTrigger = ON_DAMAGE_DEALT, charges: 1 (consume after execute). Sweep-first removes the row before dispatch.

ADD_ATTRIBUTE next-hit keeps working either way because it is fireTrigger = null permanent view applied on right-click; hit only needs expiry sweep. DISABLE_AI / BUFF_AREA must execute on hit and cannot use that path alone.

**Accepted side effect of fire-first:** On the same melee hit, damage percent from the deferred ADD_ATTRIBUTE layer remains in PlayerState while held_effects / deferred magic run, instead of MONITOR sweeping it off before dispatch. Existing rewards almost always use different trigger vs expiry.trigger (e.g. ON_KILL + ON_WAVE_CLEAR), so risk is low. This also makes the old fire+expiry both ON_DAMAGE_DEALT = next-attack-only contract actually true.

### 4.1 Parsed types

`
ItemAbility { itemId, cooldownSec, consume, castFx?, empowerFx?, selfPotions, selfClearPotions, steps }
AbilityStep { effect, params, expiryTrigger?, expiryCharges, stack }  # deferred default REPLACE
`

Cast FX merge for multi packs: magic_fx.weapons.<id> -> use_ability.fx only. Do not pick magic_fx.templates from any step effect type. Legacy single-step may keep today single-effect template merge for backward visual parity.

### 4.2 Right-click execution (+ shared TriggerContext)

1. Create one mutable TriggerContext for this cast (not discarded between steps).
2. Apply top-level self_clear_potions / self_potions (existing).
3. For each valid step in order:
   - If step has expiry -> write deferred PlayerEffect(s) into PlayerState (4.3). Count success if write accepted.
   - Else -> run immediately with weaponPath=true, same TriggerContext.
     - DAMAGE_BEAM must record hit living entity into ctx.hitTarget for later targets: hit_target.
     - Instant ADD_POTION (duration_ticks > 0, no expiry) uses existing dual path.
4. Any success (including self_* alone) -> return true.
5. Handler on true: start CD, play cast FX once on caster, optional consume.

shouldPlayCastAreaRing: true if any immediate step qualifies.

**thunder_rod (IN SCOPE):** effects [DAMAGE_BEAM, DISABLE_AI hit_target] works because beam writes hitTarget. Without shared context this cannot be fixed; that approach is rejected.

Note: empty-target BUFF_AREA / DISABLE_AI still return true today.

### 4.3 Deferred steps (next-hit / expiry)

| Effect family | Storage | On trigger T (after 4.0 order) |
|---------------|---------|--------------------------------|
| ADD_ATTRIBUTE / ADD_POTION (duration 0 + expiry) | Temp PlayerEffect, fireTrigger = null | No re-execute; removed in sweepExpiry + resync |
| Magic (BUFF_AREA, DISABLE_AI, DAMAGE_*, HEAL_AREA, ...) | PlayerEffect with fireTrigger = expiryTrigger = T | Execute in dispatch (weapon path), then removed in sweep |

**Id scheme:**

- Legacy single ADD_* step: ability:<itemId> (compat).
- Multi-step / any deferred magic step: ability:<itemId>:<stepIndex>.
- Pack membership for empower coalesce (separator required):
  - id.equals("ability:" + itemId) OR
  - id.startsWith("ability:" + itemId + ":")
  - Bare startsWith(ability: + itemId) is forbidden (false-positive herafinger_x).

**Ownership:** Deferred charges live on the player (PlayerState), not the held item. Swapping weapons or death+revive does not clear them by itself. Authors must not assume must still hold the weapon to consume.

**eighty_hammer:** Legacy YAML effect: DISABLE_AI + expiry becomes a deferred magic step. No YAML edit required for correct next-melee stun (intentional silent behavior change).

#### 4.3.1 LOCKED: deferred magic execution path

Must not use the STAT reward path blindly:

- executeEffect today calls magic with weaponFx = null -> STAT path: mark_fx only on mark_on single target; params.fx on caster; weapon defaults skipped.
- Immediate right-click steps are fine today; hit-time deferred BUFF_AREA / DISABLE_AI would silently wrong-path.

**Required:** When executing deferred weapon-pack magic (ids matching 4.3 pack membership), call magic execution with weaponPath=true, and pass the live TriggerContext from EffectListener (victim already filled at MONITOR).

**Re-entrancy:** Deferred DAMAGE_* on ON_DAMAGE_DEALT must still wrap with MagicDamageContext when shouldWrapMagicDamage(ctx) is true. Do not treat weaponFx != null as the only wrap condition.

### 4.4 Empower FX (deferred pack consume)

**When:** During fireTriggerPlayer for trigger T, after dispatch, if >=1 pack member for itemId was executed or removed because of T, play empower FX once for that itemId.

**Where:** On the victim (TriggerContext hit target / playOnEntity). If no victim entity, skip empower FX (do not flash caster as substitute).

**What plays:**

1. Prefer use_ability.empower_fx (weapons overlay optional; no effect-template from steps).
2. Else fall back to top-level use_ability.fx when its preset is visible (not NONE).
3. Else if the pack is **deferred-only** (all steps have expiry) -> built-in `deferredStrikeFx` (CRIT + DOT_ABOVE on victim). Covers next-hit ADD_ATTRIBUTE / DISABLE_AI weapons whose cast template is sound-only (`preset: NONE`).
4. Else -> do not play (do not use magic_fx.defaults).

**Coalesce:** Multiple deferred steps of same item on same trigger -> one empower FX.

**Not empower FX:** immediate step params.fx / mark_fx on right-click; held_effects marks; cast FX on right-click (caster).

| Moment | FX |
|--------|----|
| Right-click empower | Cast FX once on caster (use_ability.fx) |
| Next melee hit (buff still applied during fire; then consumed) | Empower FX once on victim (empower_fx or cast fx fallback) |

---

## 5. Validation

Per step, reuse existing validators with weaponPath=true:

- BUFF_AREA -> validateBuffArea
- DISABLE_AI -> validateDisableAi:
  - Immediate right-click step: pass triggerOrNull = null (warn/skip ineffective hit_target / attacker as today).
  - Deferred step: pass triggerOrNull = expiry.trigger so hit_target + ON_DAMAGE_DEALT is legal; hit_target + ON_DAMAGE_TAKEN / ON_KILL still rejected.
- SUMMON / GRANT_ITEM / ADD_* -> existing rules; instant ADD_POTION dual path per step.
- Deferred magic: require expiry.trigger in { ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_KILL } for v1; reject ON_TICK_1S / ON_INTERACT.
- Reject AURA / BOUND_EQUIP in steps.

Top-level: cooldown_sec > 0 unchanged.

---

## 6. Code touch list (expected)

| Area | Change |
|------|--------|
| EffectService.fireTrigger / fireTriggerPlayer | Reorder: dispatch fireTrigger==T then sweepExpiry; empower FX hook; deferred weaponPath exec |
| ItemAbility / AbilityStep | Steps list + empowerFx; default deferred stack REPLACE |
| ItemAbilityRegistry | Parse effects[] / legacy; per-step expiry for magic; empower_fx; validate with expiry trigger; reject AURA/BOUND_EQUIP; per-step ADD_POTION |
| EffectService.executeAbility | Shared TriggerContext; loop steps; deferred write; beam writes hitTarget; success aggregation |
| Beam path | Persist hit living entity into shared ctx |
| ConfigMagicHandler | Cast FX once; area-ring over immediate steps; single CD |
| MagicFxConfig / MagicFxService | Multi cast merge without step template; empower on victim; no defaults fallback when both fx absent |
| WeaponTempEffect / ids | Multi-step ids; pack membership helper with : separator |
| Tests | See section 8 |
| Docs | This spec; plugin-config reference; CLAUDE.md note; dev-log on implement |

---

## 7. Content checklist (author after merge — not this PR)

| Item | Intent | After this PR |
|------|--------|---------------|
| maggoteers:emp | Silence + stun 10s | Can (immediate two steps, targets: enemies). Author must rewrite YAML |
| maggoteers:herafinger | Next-hit dmg + 3m Slowness X 3s | Can (two deferred steps + cast/empower FX). Author YAML; value 1.0 vs live 2.0 / lore 200% is content choice. On-hit 1s slow = held_effects (still missing on item) |
| maggoteers:eighty_hammer | Next-hit stun 5s | Works without YAML edit once fire-before-expiry + deferred weapon path land (silent behavior change) |
| maggoteers:thunder_rod | Beam 3 dmg + stun 4s | Can via shared step context + author YAML multi immediate |
| maggoteers:mission_sure | Heal allies + Speed | Can (immediate two steps). Author YAML |

### held_effects only

| Item | Intent |
|------|--------|
| maggoteers:herafinger | On-hit 1s Slowness X |
| maggoteers:frostmourne | On-hit heal 1 + on-hit Slowness I (held currently only Wither) |

### Already OK as single effect

cataclysm, condense, class_vanguard_axe, past_fine, shattered_king, phantasm (aside from lore/CD typos).

---

## 8. Acceptance tests

Must include **runtime** fire/expiry ordering — not YAML-load-only.

1. Legacy single effect: + expiry ADD_ATTRIBUTE still applies temp buff and enters CD.
2. effects: [BUFF_AREA clear, DISABLE_AI] both run on one right-click; one CD; one cast FX.
3. effects: [ADD_ATTRIBUTE expiry, BUFF_AREA expiry] right-click does not slow yet; next melee applies AoE slow and consumes damage buff; empower FX once on victim; cast FX was on click only.
4. Same trigger: deferred DISABLE_AI executes (AI locked) then is removed from PlayerState (fire before sweep).
5. Legacy YAML eighty_hammer shape (DISABLE_AI + expiry, single effect:): right-click does not lock AI; next melee does.
6. Deferred BUFF_AREA uses weapon mark_fx path and receives hitTarget from melee ctx.
7. Empower id coalesce: ability:foo + ability:foo:0 -> 1 play; ability:foo_bar:0 does not merge with foo.
8. Shared context: after immediate DAMAGE_BEAM, DISABLE_AI + hit_target affects the beam victim.
9. If step 0 fails and step 1 succeeds -> still CD.
10. If all steps fail and no self_* -> no CD, no cast FX.
11. Two deferred steps same trigger -> empower FX invocation count = 1.
12. CD ready again while prior deferred charge unspent -> second right-click REPLACEs (does not IGNORE leave stale charge).
13. No empower_fx and no visible cast fx: deferred-only packs play built-in strike flash; non-deferred packs play nothing (defaults not used).

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| Sweep-first leaves deferred magic dead | 4.0 locked reorder + tests 4-5 |
| STAT path on deferred magic | 4.3.1 weaponPath + hitTarget |
| Double FX from step mark_fx + pack empower_fx | Document: put flash on empower_fx; keep mark_fx subtle or omit |
| Id false-positive herafinger vs herafinger_x | Separator rule in 4.3 |
| Partial apply then death before hit | Existing PlayerState cleanup |
| Authors expect per-step CD | Rejected; shared CD only |
| Authors expect must-hold-weapon to consume | Document player-owned charges (4.3) |
| Fire-first changes same-trigger reward timing | Low: rewards rarely share trigger/expiry; accepted for next-hit damage staying through the hit |
| Silent eighty_hammer behavior change | Called out in 1.2; only intentional content-behavior change without YAML edit |
| Deferred DAMAGE_* re-entrancy | Must use MagicDamageContext via shouldWrapMagicDamage |

---

## Spec self-review

- [x] Fire-before-expiry locked for player + game-wide paths
- [x] Deferred magic weaponPath + TriggerContext locked
- [x] thunder_rod in scope via shared step context
- [x] Deferred default stack REPLACE; ability id separator; empower on victim; no defaults spam
- [x] eighty_hammer silent behavior called out
- [x] Instant ADD_POTION per-step; AURA/BOUND_EQUIP rejected
- [x] Runtime ordering tests in section 8
- [x] held_effects still out of code scope

