# Weapon Main-Hand Effect System (Held Passives + Right-Click Active)

> Date: 2026-08-08 (rev.3 - escape fix, AURA grant, triggered semantics)
> Status: Implemented (2026-08-08)
> Related: ItemAbilityRegistry, ItemInteractRouter, EffectService, WeaponTempEffect, items/*.yml

-

## 1. Goals

### 1.1 Problems

- **Path A (right-click use_ability)**: Instant magic works; temporary buffs only cover ADD_ATTRIBUTE + expiry. Missing instant timed potions (rewardplan: 5s Strength, 0.2s Levitation). ItemAbilityRegistry.parseParams does not parse potion / amp / duration_ticks; executeAbility has no instant ADD_POTION path.
- **Path B (held passives)**: Missing entirely — no inject/remove by main-hand PDC.
- **Prior spec bug (rev.1)**: held_effects + expiry contradicts remount-on-resync lifecycle.

### 1.2 Success criteria

| Criterion | Detail |
|-|-|
| Main-hand PDC | Read maggoteers:id, main hand only |
| Held passives | held_effects: [] -> inject/remove held:<itemId>:<n> while holding; **no expiry** |
| Right-click active | Keep use_ability; dual ADD_POTION model (instant vs PlayerState+expiry) |
| Triggered held | ON_DAMAGE_DEALT / ON_DAMAGE_TAKEN / ON_KILL only |
| Coexist with rewards | Separate id namespace |
| Param names | Match
rewards.yml (potion:, duration_ticks: 0 for permanent) |

### 1.3 Out of scope

- Off-hand weapon effects
- held_effects expiry (forbidden — see §4.2)
- Weapon ON_WAVE_CLEAR / ON_ACT_ENTER / ON_DEATH passives
- GRANT_ITEM / SUMMON as held passive
- UPGRADE_LEVEL on held effects
- Lore/scoreboard

-

## 2. Chosen approach

**Approach 1: dual config blocks + unified validation** (brainstorming 2026-08-08).

- use_ability — right-click active (existing)
- held_effects: [] — lifecycle = **while main hand holds item** only
- WeaponHeldRegistry + WeaponHeldService
- WeaponTempEffect (rename from WeaponTempAttribute)
- WeaponEffectValidator

-

## 3. Architecture

```
items/*.yml
  use_ability       -> ItemAbilityRegistry -> ItemInteractRouter -> ConfigMagicHandler
  held_effects[]    -> WeaponHeldRegistry  -> WeaponHeldService
                           |
                           v
                 WeaponEffectValidator
                           |
                           v
                 PlayerState.effects
                           |
                           v
                 EffectService (+ AuraService.refresh on AURA mount/unmount)
                           ^
                 EffectListener (ON_KILL / ON_DAMAGE_DEALT / ON_DAMAGE_TAKEN)
```

**Effect id namespaces:**

| Prefix | Source | Lifecycle |
|-|-|-|
| held:<itemId>:<n> | held_effects | Mount on hold, unmount on unhold/resync — **no expiry** |
| ability:<itemId> | use_ability + expiry | Event expiry (PlayerState) |
| reward option id | rewards.yml | Existing model |

-

## 4. held_effects schema

### 4.1 Example (correct param names + AURA grant)

AURA uses nested `grant:` (same as `rewards.yml` `a1s_war_banner`), **not** top-level `potions:`.

```yaml
maggoteers:example_blade:
  material: IRON_SWORD
  pdc:
    - key: maggoteers:id
      type: STRING
      value: maggoteers:example_blade
  held_effects:
    - effect: ADD_ATTRIBUTE
      params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 0.10 }
      stack: ADD
    - effect: ADD_POTION
      params: { potion: FIRE_RESISTANCE, amp: 0, duration_ticks: 0 }
      stack: IGNORE
    - effect: AURA
      params:
        radius: 12.0
        targets: allies
        grant:
          effect: ADD_ATTRIBUTE
          attr: ATTACK_DAMAGE
          op: PERCENT
          value: 0.08
        carrier_fx:
          preset: THICK_RING
          particle: TOTEM_OF_UNDYING
          radius: 12.0
          density: 36
      stack: IGNORE
    - effect: ADD_POTION
      trigger: ON_DAMAGE_DEALT
      params: { potion: POISON, amp: 0, duration_ticks: 100 }
      stack: IGNORE
    - effect: HEAL
      trigger: ON_KILL
      params: { amount: 2.0 }
      stack: IGNORE
    - effect: BUFF_AREA
      trigger: ON_DAMAGE_DEALT
      params:
        targets: hit_target
        potions: [{ potion: POISON, amp: 0, duration_ticks: 100 }]
      stack: IGNORE
  use_ability:
    cooldown_sec: 15
    effect: ADD_ATTRIBUTE
    params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 1.0 }
    expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }
    stack: REPLACE
```

### 4.2 Fields

| Field | Rule |
|-|-|
| effect | Required — see §4.4 for permanent vs triggered |
| trigger | Omit = **permanent held** (only ADD_ATTRIBUTE / ADD_POTION / AURA). Combat effects **must** have trigger |
| params | Same keys as `rewards.yml` STAT (potion: not effect: for ADD_POTION) |
| expiry | **Forbidden on held_effects** (load warn + skip entry). Lifecycle = holding only |
| stack | Triggered default IGNORE; permanent attr may use ADD / REPLACE / IGNORE |
| upgrade_max | **Forbidden on held** (no upgrade path while holding) |

Runtime id: held:<itemId>:<index>.

### 4.3 Why held must not use expiry (rev.2 fix)

Held effects are remounted on EffectService.resync, slot change, and re-hold after unhold. If a held entry had expiry, losing charges on ON_DAMAGE_DEALT would be undone on next remount — expiry would be meaningless.

**Rule:** expiry is **only** for use_ability temporary effects (ability:<itemId>).

### 4.4 Permanent vs triggered (held)

| Mode | Allowed effect | Requires |
|-|-|-|
| Permanent (no trigger) | ADD_ATTRIBUTE, ADD_POTION, AURA only | — |
| Triggered | HEAL, DAMAGE_AREA, DAMAGE_BEAM, HEAL_AREA, BUFF_AREA, ADD_ATTRIBUTE*, ADD_POTION* | trigger in §4.6 |

\* ADD_ATTRIBUTE / ADD_POTION with trigger = one-shot triggered passive (e.g. on-kill buff), not permanent derived.

**Reject at load:** permanent HEAL / DAMAGE_* / BUFF_AREA without trigger (would sit in PlayerState forever and never fire).


### 4.5 Triggered ADD_ATTRIBUTE / ADD_POTION semantics

**Triggered ADD_ATTRIBUTE** uses existing `TriggeredGrantAttribute`:

- Template id `held:<itemId>:<n>` stays in PlayerState with `fireTrigger` set.
- On trigger fire, engine spawns grant layer `held:<itemId>:<n>:grant` (same pattern as reward `{id}:grant`).
- Grant applies Bukkit attr or virtual stat per grant params.

**Triggered ADD_POTION** uses `executeEffect` ADD_POTION branch:

- Requires **`duration_ticks > 0`** in params (engine only calls `addPotionEffect` when `dur > 0`).
- Validator **must reject** triggered ADD_POTION with `duration_ticks <= 0` (warn + skip).
- Distinct from permanent held ADD_POTION (`duration_ticks: 0`, no trigger -> `applyPotionPermanent` refresh loop).

### 4.6 Trigger whitelist — held_effects.trigger (fireTrigger)

| Allowed | Forbidden |
|-|-|
| *(omit — permanent only)* | ON_DEATH |
| ON_DAMAGE_DEALT | ON_WAVE_CLEAR |
| ON_DAMAGE_TAKEN | ON_ACT_ENTER |
| ON_KILL | ON_INTERACT, ON_TICK_1S, ON_GAME_END, ON_REVIVE |

No expiry block on held entries.

### 4.7 Forbidden — effect types on held

| Forbidden | Reason |
|-|-|
| GRANT_ITEM | Use rewards or use_ability |
| SUMMON | Use rewards or use_ability |
| Any entry with expiry: | Held lifecycle only (§4.3) |
| Permanent AURA with trigger or expiry | AURA held = permanent carrier only |
| ADD_ATTRIBUTE + op: REVOKE_GRANTS | Rewards bundle only |
| MAGIC_DAMAGE + op: FLAT | Same as rewards |
| stack: UPGRADE_LEVEL or upgrade_max | No held upgrade path |
| Triggered ADD_POTION with duration_ticks <= 0 | Engine no-op (reject at load) |

Reuse HEAL_AREA / BUFF_AREA param validation from RewardLoadValidator / MagicEffectParams.

-

## 5. use_ability — Path A (right-click)

### 5.1 Already works (instant, no PlayerState)

DAMAGE_AREA, DAMAGE_BEAM, HEAL_AREA, BUFF_AREA, GRANT_ITEM, SUMMON.

### 5.2 ADD_ATTRIBUTE + expiry (already works)

```yaml
use_ability:
  cooldown_sec: 15
  effect: ADD_ATTRIBUTE
  params: { attr: ATTACK_DAMAGE, op: PERCENT, value: 0.50 }
  expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }
  stack: REPLACE
```

-> ability:<itemId> in PlayerState until expiry trigger;
resyncDerived for Bukkit attrs.

### 5.3 ADD_POTION — two explicit paths (rev.2)

Do **not** mix duration_ticks: 40 with PlayerState expiry (current engine ignores duration_ticks on permanent ADD_POTION — uses 30s refresh loop).

#### Path A1 — Instant timed potion (primary for rewardplan)

**Condition:** no expiry block **and** duration_ticks > 0

```yaml
use_ability:
  cooldown_sec: 20
  effect: ADD_POTION
  params: { potion: SPEED, amp: 2, duration_ticks: 100 }
  # no expiry
```

**Runtime:** right-click -> player.addPotionEffect(type, duration_ticks, amp) — **does not** write PlayerState. Matches rewardplan weapons (5s Strength, 0.2s Levitation, etc.).

#### Path A2 — PlayerState until event expiry

**Condition:** expiry: block present **and** duration_ticks must be **0 or omitted** (validator warn+skip if > 0)

```yaml
use_ability:
  cooldown_sec: 20
  effect: ADD_POTION
  params: { potion: STRENGTH, amp: 0, duration_ticks: 0 }
  expiry: { trigger: ON_DAMAGE_DEALT, charges: 1 }
  stack: REPLACE
```

**Runtime:** ability:<itemId> -> applyDerived uses permanent potion refresh (30s loop) until expiry removes entry — same as reward permanent ADD_POTION model. duration_ticks in params is ignored on this path.

#### Decision table

| expiry | duration_ticks | Path |
|-|-|-|
| absent | > 0 | A1 instant |
| absent | 0 / omitted | warn+skip (ambiguous) |
| present | 0 / omitted | A2 PlayerState + expiry |
| present | > 0 | warn+skip (contradictory) |

### 5.4 Param parsing fix (required)

ItemAbilityRegistry.parseParams must add (mirror RewardOption):

- potion -> EffectKeys.POTION
- amp -> EffectKeys.AMP
- duration_ticks -> EffectKeys.DURATION_TICKS

Using effect: SPEED in params will **not** parse — authors must use potion:.

### 5.5 executeAbility fix (required)

Current code: only ADD_ATTRIBUTE -> PlayerState; everything else -> executeMagicEffect (no ADD_POTION branch -> false).

**New routing:**

```
executeAbility(ability):
  if ADD_ATTRIBUTE && expiry -> applyWeaponTempEffect (PlayerState)
  if ADD_POTION && expiry -> applyWeaponTempEffect (PlayerState, duration_ticks must be 0)
  if ADD_POTION && !expiry && duration_ticks > 0 -> applyInstantPotion (addPotionEffect, return)
  else -> executeMagicEffect (existing instant magic)
```

Rename WeaponTempAttribute -> WeaponTempEffect; validate both ADD_ATTRIBUTE and ADD_POTION expiry paths.

-

## 6. Sync timing (WeaponHeldService)

### 6.1 Mount / unmount events

| Event | Action |
|-|-|
| PlayerItemHeldEvent | Main-hand id changed -> unmount old held:*, mount new |
| InventoryClickEvent | Main-hand slot changed -> same |
| PlayerSwapHandItemsEvent | Main-hand id changed -> same |
| **PlayerDropItemEvent** | If dropped item was/is main hand -> re-sync (slot may still show item until empty; also handle drop from selected slot) |
| **EntityPickupItemEvent** | If pickup lands in main hand (or hotbar slot that becomes selected) -> sync |
| EffectService.resync(player) | Remove all held:* -> remount from current main hand |
| Game cleanup | Cleared with PlayerState |

Detection: ItemService.itemIdOf(player.getInventory().getItemInMainHand()); null -> remove all `held:*`.


**Unmount removal rule:** delete every `PlayerEffect` whose `id` starts with `held:<itemId>:` for the weapon being cleared. This **includes** grant sub-layers `held:<itemId>:<n>:grant` from triggered ADD_ATTRIBUTE (`TriggeredGrantAttribute`). Do not leave orphan grants.

When clearing all held weapons (no PDC / empty hand): remove **all** ids with prefix `held:`.


### 6.2 Unmount side effects

After removing held entries from PlayerState:

1.
resyncDerived(player, state) — strip/reapply Bukkit attrs + permanent potions
2. **AuraService.refresh(game)** if any removed (or added) entry was effect: AURA — AURA does not use applyDerived; skipping refresh leaves stale aura carriers for one tick+

Same refresh on mount when held AURA is added.

### 6.3 Coexist with rewards

Separate id space; parallel fire on same trigger.

-

## 7. Code change list

| File | Action |
|-|-|
| effect/WeaponHeldEntry.java | Create |
| effect/WeaponHeldRegistry.java | Create — parse held_effects, reject expiry |
| effect/WeaponHeldService.java | Create — mount/unmount + AuraService.refresh |
| effect/WeaponEffectValidator.java | Create — held rules + use_ability ADD_POTION dual path |
| effect/WeaponTempAttribute.java | Rename -> WeaponTempEffect |
| effect/ItemAbilityRegistry.java | parseParams: potion/amp/duration_ticks; expiry for ADD_POTION; route validation |
| effect/EffectService.java | executeAbility: instant ADD_POTION + PlayerState expiry paths |
| item/WeaponHeldListener.java | Create — includes drop + pickup |
| item/ItemInteractRouter.java | Fix javadoc |
| MaggoteersPlugin.java | Register listener + load registry |
| test/WeaponEffectValidatorTest.java | held no-expiry; permanent type rules; ADD_POTION paths |
| test/WeaponHeldServiceTest.java | mount/unmount; AURA refresh; drop item clears held |
| test/WeaponTempEffectTest.java | Rename from WeaponTempAttributeTest |

-

## 8. Path A integration (explicit)

1. ItemInteractRouter javadoc — remove ON_INTERACT fallback text
2. ItemAbilityRegistry.parseParams — add potion/amp/duration_ticks
3. WeaponEffectValidator — ADD_POTION dual-path rules (§5.3 table)
4. EffectService.executeAbility — instant ADD_POTION + PlayerState ADD_ATTRIBUTE/ADD_POTION expiry
5. WeaponTempEffect — generalize validate/apply from WeaponTempAttribute
6. Unit tests for instant potion (no PlayerState entry after use)

-

## 9. Tests

| Test | Assert |
|-|-|
| WeaponEffectValidatorTest | Reject held expiry:; reject permanent HEAL; reject UPGRADE_LEVEL on held |
| WeaponEffectValidatorTest | Reject use_ability ADD_POTION with expiry + duration_ticks>0 |
| WeaponHeldServiceTest | Unmount removes held:...:n:grant orphans |
| WeaponHeldServiceTest | Drop main-hand item -> held:* removed; pickup -> mounted |
| WeaponHeldServiceTest | Unmount held AURA -> AuraService.refresh called |
| WeaponTempEffectTest | ability:* expiry for ADD_ATTRIBUTE unchanged |
| Instant potion test | use_ability ADD_POTION duration_ticks=100 -> addPotionEffect, PlayerState empty |

Manual: swap weapon -> permanent attr changes; power_strike -> next-hit buff clears; 5s Strength right-click -> timed potion only; drop weapon -> held passive gone immediately.

-

## 10. Doc sync

- CLAUDE.md §15: held_effects (no expiry), use_ability ADD_POTION dual path
- maggoteers-plugin-config reference.md: param names potion:, duration_ticks rules
- docs/dev-log.md after implementation

-

## Appendix: rev.3 changelog

| Fix | Detail |
|-|-|
| Escape corruption | No BEL/TAB; use line-based writer for commits |
| AURA example | Nested `grant:` per rewards.yml |
| Triggered ADD_ATTRIBUTE | Document `:grant` sub-id; prefix unmount |
| Triggered ADD_POTION | Validator requires duration_ticks > 0 |

## Appendix: rev.2 review checklist (superseded)

| Issue (rev.1) | Fix |
|-|-|
| held + expiry vs remount | expiry forbidden on held; holding = lifecycle |
| ADD_POTION example mixed models | §5.3 dual path A1 instant / A2 PlayerState+expiry |
| effect: vs potion: | Use potion: per rewards.yml |
| duration_ticks: -1 | Permanent = 0; instant = >0 without expiry |
| Missing drop/pickup sync | §6.1 PlayerDropItemEvent + EntityPickupItemEvent |
| Permanent held too broad | §4.4 ADD_ATTRIBUTE / ADD_POTION / AURA only |
| UPGRADE_LEVEL on held | Forbidden §4.2 |
| §4.3 table confusion | Split §4.6 triggers vs §4.7 forbidden effects |
| executeAbility gaps | §5.4 parseParams + §5.5 routing |
| AURA unmount | §6.2 AuraService.refresh |
