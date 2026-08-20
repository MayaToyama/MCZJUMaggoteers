# Waveplan to waves.yml Configuration Design

> Source: `docs/superpowers/plans/waveplan`. Date: 2026-07-26.

## Goal

- **Fully replace** placeholder strategies and pools in `waves.yml`.
- Reference affixes from `affixes.yml` (add `blinding` only).
- Map-specific waves: **defined** in `waves.yml`, **pooled** in each map's `special_waves.yml` (`weight: 5`).
- **Zero Java** for v1: random death spawns and per-type affixes use pure YAML.

## Out of Scope (v1)

- Runtime random `on_death` pick (blind box / human radiance use variant mixing).
- `MobFactory` auto-affix by entity type.
- Player PDC weapons on mobs.

---

## File Responsibilities

| File | Role |
|------|------|
| `config.yml` | `mob_attributes.follow_range: 5.0` |
| `affixes.yml` | Add `blinding` (permanent blindness; not hit-based `squid`) |
| `items/maggoteers.yml` | Mob-only ItemCreator gear (`mob_*` prefix) |
| `waves.yml` | All `strategies` + per-Act **global** `pools` |
| `maps/.../special_waves.yml` | `{ strategy, weight: 5 }` references only |

`RunPlanner` merges `special_waves.yml` into the act tier pool after map pick.

---

## Global Rules

| Plan rule | Implementation |
|-----------|----------------|
| All mobs follow range x5 | `config.yml` -> `mob_attributes.follow_range: 5.0` |
| All slimes `sticky` | Every SLIME step/passenger/parent of `on_death` includes `sticky` in `affixes` |
| All husks `quicksand` | Every HUSK includes `quicksand` |
| Death-spawn parents `infected` | Parent `affixes` includes `infected` |
| All bosses `cataclysm` | Boss point step includes `cataclysm` |

### Timing Workarounds

Engine: `count` spawns all at once; `delay` is pre-step wait (seconds).

| Plan wording | YAML |
|--------------|------|
| First mob at 2s/4s/5s/10s | Split steps: `count: 1` + different `delay` |
| Repeat after 15s | `repeat: 2` on strategy |
| Repeat batch after 10s | Two steps or `repeat: 2` + second step `delay: 10` |
| Human radiance: 50 slimes, pause 20s every 10 | Points 1-4: five steps each `count: 10, delay: 20` (first delay 0) |
| Boss after N seconds | `point: boss, delay: N` |

---

## Strategy Naming

`<tier><act>_<english_slug>` — e.g. `w1_bug_buddies`, `s1_resort_ghosts`, `b1_colossus`.

---

## Strategy Catalog (1:1 with waveplan)

### Act 1

**Global weak (7):** `w1_bug_buddies`, `w1_patrol_riders`, `w1_accident`, `w1_symbiosis`, `w1_symptom`, `w1_herd`

**Global strong (3):** `s1_resort_ghosts`, `s1_low_altitude`, `s1_vent_breeze`

**Boss (3):** `b1_colossus`, `b1_eternal_rest`, `b1_bandit_chief`

**desert:** `w1_desert_four_camels`, `w1_desert_prisoners`, `w1_desert_plague`

**jungle:** `w1_jungle_shadows`, `w1_jungle_spider_swap`, `w1_jungle_spider_falls`

### Act 2

**Global weak (6):** `w2_merchant_convoy`, `w2_endless_spear`, `w2_fireworks`, `w2_lord_legacy`, `w2_nest`, `w2_territory`

**Global strong (6):** `s2_ice_shadows`, `s2_migration`, `s2_forbidden`, `s2_chessboard`, `s2_crimson_tunnel`, `s2_controversy`

**badlands:** `s2_badlands_fake_death`, `s2_badlands_contamination`, `s2_badlands_bully`

**graveyard:** `w2_grave_blind_box`, `s2_grave_phantoms`, `w2_grave_march`

**Boss (3):** `b2_silent_guard`, `b2_breath`, `b2_frost_farewell`

### Act 3

**weak (3):** `w3_duck_ops`, `w3_rabbit_holes`, `w3_lights_out`

**strong (9):** `s3_surprise_factory`, `s3_across_fire`, `s3_out_of_control`, `s3_chaos_facade`, `s3_keep_out`, `s3_fire_water`, `s3_institution`, `s3_witch_pact`, `s3_rhine_guard`

**Boss (3):** `b3_into_eternal`, `b3_sentinel`, `b3_human_radiance`

---

## Grave March (`w2_grave_march`)

Designed where waveplan had name only (three zombie + three skeleton horde):

```yaml
steps:
  - { point: "1", type: ZOMBIE, count: 8, delay: 0 }
  - { point: "1", type: SKELETON, count: 8, delay: 0 }
  - { point: "2", type: HUSK, count: 8, affixes: [quicksand], delay: 8 }
  - { point: "2", type: STRAY, count: 8, delay: 8 }
  - { point: "3", type: DROWNED, count: 8, delay: 8 }
  - { point: "3", type: BOGGED, count: 8, delay: 8 }
  - { point: "4", type: ZOMBIE, count: 10, coeff: { speed: 1.2 }, delay: 6 }
  - { point: "4", type: SKELETON, count: 10, coeff: { speed: 1.2 }, delay: 6 }
  - { point: "5", type: HUSK, count: 12, affixes: [quicksand], delay: 6 }
  - { point: "5", type: STRAY, count: 12, delay: 6 }
repeat: 1
clearReward: [ { item: maggoteers:currency_normal, amount: 2 } ]
```

---

## Blind Box / Human Radiance (Variant Mixing)

13 fixed `on_death` variants; ~4 steps each (`count: 4`) rotated across points 1-5 for 50 slimes. Each slime: `affixes: [sticky, infected]`.

Variants match waveplan lists (zombie fast, skeleton tank, creeper bomb, spider small, wither skeleton, piglin spear, endermites, cave spiders, hoglin, bogged hide, stray punch bow, husk tank, drowned trident; radiance uses boosted coeffs/enchants).

---

## Pool Weights

- Global weak/strong/boss: `weight: 2`
- Map `special_waves`: `weight: 5`

Act3 `waves_per_act`: `weak: 0, strong: 4` — only strong + boss in global pool.

---

## special_waves.yml Mapping

| Map | Strategies (weight 5) |
|-----|-------------------------|
| act1/desert | 3 desert strategies (tier per waveplan) |
| act1/jungle | 3 jungle strategies |
| act2/badlands | 3 badlands strong strategies |
| act2/graveyard | `w2_grave_blind_box`, `w2_grave_march`, `s2_grave_phantoms` |
| act3/mushrooms | none (comment only) |

---

## affixes.yml Addition

```yaml
blinding:
  potions: [ { effect: BLINDNESS, amp: 0, dur: 999999 } ]
  display: "<gray>致盲"
```

Existing affixes cover the rest: `plastic`, `sticky`, `quicksand`, `infected`, `cataclysm`, `hidesuwa`, `squid`, `truck`, `mechanical`, `frostgrasp`, `antifreeze`, `fireresistance`, `withering`, `exoskeleton`, `poisonous`, `toxic`, `vampiric`, `lifesteal`, `invisible`, `teleport`.

---

## items/maggoteers.yml (mob_* gear)

Mob-only ItemCreator ids for `equipment` in waves.yml:

**Melee:** `mob_iron_spear`, `mob_gold_spear` (+2 reach via attributeModifiers), `mob_iron_sword_ub10`, `mob_iron_axe`, `mob_diamond_axe_sharp5`, `mob_diamond_sword`, `mob_gold_sword_flame`, `mob_gold_shovel`, `mob_trident`

**Ranged:** `mob_bow_power1`, `mob_bow_power2`, `mob_bow_power4`, `mob_bow_power5`, `mob_bow_punch2`, `mob_bow_flame_punch`, `mob_crossbow_power3`, `mob_crossbow_flame`, `mob_crossbow_flame_quick`

**Armor (split slots or sets):** `mob_iron_helmet_ub10`, iron/gold/diamond chest/legs/feet variants with unbreaking X as needed

---

## Implementation Order (single batch)

1. `config.yml` — follow_range 5.0
2. `affixes.yml` — blinding
3. `items/maggoteers.yml` — all mob_*
4. `waves.yml` — all strategies + pools (remove old ids)
5. Map `special_waves.yml` files
6. `mvn test` + deploy smoke

---

## Acceptance

- [ ] onEnable: no unknown strategy/affix
- [ ] RunPlanner merges global + special pools per map
- [ ] Global rules visible on representative strategies
- [ ] Blind box / human radiance: 13 death variants across 50 slimes
- [ ] Desert/jungle/badlands/graveyard can roll weight-5 specials

---

## Decision Log

| Decision | Choice |
|----------|--------|
| Replace scope | Full waves.yml replace |
| Random on_death | YAML variant mixing |
| Grave march | Designed 3-zombie 3-skeleton horde |
| Slime/husk affixes | Manual per step in YAML |
| Equipment | Full mob_* ItemCreator set |
| Map specials | special_waves.yml weight 5 |
| Batch | Single large commit |
