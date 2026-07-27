# Waveplan waves.yml Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace placeholder wave content with the full waveplan as YAML (`waves.yml`, map `special_waves.yml`, supporting `config.yml`, `affixes.yml`, `items/maggoteers.yml`).

**Architecture:** All strategy definitions live in `waves.yml` (single source). Map files only add pool weights. Global follow range via `config.yml`; per-mob affixes/equipment/death spawns/passengers in step YAML. Blind-box waves use 13 fixed slime variants rotated across steps (no Java random pick).

**Tech Stack:** Paper 26.2 plugin YAML, ItemCreator items, existing `MobYamlParser` / `RunPlanner` / `WaveEngine`.

## Global Constraints

- **Zero Java** for v1; config-only content changes.
- **`mob_attributes.follow_range: 5.0`** in `config.yml`.
- Every **SLIME** entry: `affixes` includes `sticky`.
- Every **HUSK** entry: `affixes` includes `quicksand`.
- Every mob with **`on_death`**: parent `affixes` includes `infected`.
- Every **boss point** step: `affixes` includes `cataclysm`.
- Global pool weight **`2`**; map `special_waves` weight **`5`**.
- `clearReward` weak: `{ item: maggoteers:currency_normal, amount: 2 }` (strong: `4`, boss: `{ item: maggoteers:currency_boss, amount: 1 }`).
- Affix ids must exist in `affixes.yml`; equipment ids in `items/maggoteers.yml`.
- Entity types via Paper registry names: `CAMEL`, `CAMEL_HUSK`, `BOGGED`, `BREEZE`, `CREAKING`, `ARMADILLO`, `HAPPY_GHAST` (if ghast-on-strider uses `GHAST` + `STRIDER` passenger — verify `EntityType` for "恶魂").

---

### Task 1: Global tuning (`config.yml` + `affixes.yml`)

**Files:**
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/resources/affixes.yml`

**Interfaces:**
- Consumes: existing `mob_attributes` block, affix loader in `AffixService`.
- Produces: `follow_range: 5.0`; new affix id `blinding`.

- [ ] **Step 1: Set follow range**

In `src/main/resources/config.yml`, change:

```yaml
mob_attributes:
  scale: 1.0
  follow_range: 5.0
```

- [ ] **Step 2: Add blinding affix**

Append to `src/main/resources/affixes.yml` under `affixes:` (before `greedy`):

```yaml
  blinding:
    potions: [ { effect: BLINDNESS, amp: 0, dur: 999999 } ]
    display: "<gray>致盲"
```

- [ ] **Step 3: Add fortified affix (Resistance IV permanent)**

For `w2_crimson_tunnel`, `w3_duck_ops` armadillo, and similar — add:

```yaml
  fortified:
    potions: [ { effect: RESISTANCE, amp: 3, dur: 999999 } ]
    display: "<blue>重甲"
```

(`amp: 3` = Resistance IV in MC effect levels.)

- [ ] **Step 4: Verify**

Run: `mvn -q test`
Expected: PASS (no affix parse errors).

---

### Task 2: Mob ItemCreator gear (`items/maggoteers.yml`)

**Files:**
- Modify: `src/main/resources/items/maggoteers.yml`

**Interfaces:**
- Consumes: ItemCreator schema in `docs/ICReadme.md`.
- Produces: `maggoteers:mob_*` ids referenced by `waves.yml` `equipment`.

- [ ] **Step 1: Add spear items**

```yaml
maggoteers:mob_iron_spear:
  material: IRON_SWORD
  itemModel: minecraft:iron_spear
  customName: "<gray>铁矛"
  unbreakable: true
  attributeModifiers:
    - attribute: minecraft:attack_damage
      amount: 6
      operation: ADD_NUMBER
      slot: mainhand

maggoteers:mob_gold_spear:
  material: GOLDEN_SWORD
  itemModel: minecraft:golden_spear
  customName: "<gold>金矛"
  unbreakable: true
  attributeModifiers:
    - attribute: minecraft:entity_interaction_range
      amount: 2
      operation: ADD_NUMBER
      slot: mainhand
    - attribute: minecraft:attack_damage
      amount: 5
      operation: ADD_NUMBER
      slot: mainhand
```

- [ ] **Step 2: Add swords/axe/shovel/trident**

Ids: `mob_iron_sword_ub10`, `mob_iron_sword_reach` (attack speed 0.8 + reach +2 for eternal rest boss), `mob_iron_axe`, `mob_diamond_axe_sharp5`, `mob_diamond_sword`, `mob_gold_sword_flame`, `mob_gold_shovel`, `mob_trident` — use `unbreakable: true`, `enchantments` / `attributeModifiers` per waveplan.

Example `mob_iron_sword_ub10`:

```yaml
maggoteers:mob_iron_sword_ub10:
  material: IRON_SWORD
  unbreakable: true
  enchantments:
    - minecraft:unbreaking: 10
```

- [ ] **Step 3: Add bows and crossbows**

Ids: `mob_bow_power1`, `mob_bow_power2`, `mob_bow_power4`, `mob_bow_power5`, `mob_bow_punch2`, `mob_bow_flame_punch`, `mob_crossbow_power3`, `mob_crossbow_flame`, `mob_crossbow_flame_quick`, `mob_crossbow_power2` — enchantments only, `unbreakable: true`.

- [ ] **Step 4: Add armor pieces**

Split slots (referenced individually in waves):

| id | material | notes |
|----|----------|-------|
| `mob_iron_helmet_ub10` | IRON_HELMET | unbreaking 10 |
| `mob_iron_chest_ub10` | IRON_CHESTPLATE | unbreaking 10 |
| `mob_iron_legs_ub10` | IRON_LEGGINGS | unbreaking 10 |
| `mob_iron_boots_ub10` | IRON_BOOTS | unbreaking 10 |
| `mob_gold_helmet` … `mob_gold_boots` | GOLD_* | silent guard full gold |
| `mob_diamond_helmet` … `mob_diamond_boots` | DIAMOND_* | sentinel / chessboard |

- [ ] **Step 5: Verify items load**

Run: `mvn -q test`
Deploy optional smoke: start server, check log for ItemCreator warnings on missing ids.

---

### Task 3: Replace `waves.yml` — file header, pools, Act 1 strategies

**Files:**
- Modify: `src/main/resources/waves.yml` (replace entire file incrementally; start fresh)

**Interfaces:**
- Consumes: Task 1–2 outputs; `MobYamlParser` step/passenger/on_death schema.
- Produces: Act 1 strategy ids + `pools.act1`.

- [ ] **Step 1: Write pools.act1**

```yaml
pools:
  act1:
    weak:
      - { strategy: w1_bug_buddies, weight: 2 }
      - { strategy: w1_patrol_riders, weight: 2 }
      - { strategy: w1_accident, weight: 2 }
      - { strategy: w1_symbiosis, weight: 2 }
      - { strategy: w1_symptom, weight: 2 }
      - { strategy: w1_herd, weight: 2 }
    strong:
      - { strategy: s1_resort_ghosts, weight: 2 }
      - { strategy: s1_low_altitude, weight: 2 }
      - { strategy: s1_vent_breeze, weight: 2 }
    boss:
      - { strategy: b1_colossus, weight: 2 }
      - { strategy: b1_eternal_rest, weight: 2 }
      - { strategy: b1_bandit_chief, weight: 2 }
```

- [ ] **Step 2: Implement `w1_bug_buddies`**

```yaml
  w1_bug_buddies:
    repeat: 1
    clearReward: [ { item: maggoteers:currency_normal, amount: 2 } ]
    steps:
      - { point: "1", type: SPIDER, count: 4, delay: 0 }
      - { point: "1", type: ENDERMITE, count: 6, delay: 2 }
      - { point: "2", type: SPIDER, count: 4, delay: 0 }
      - { point: "2", type: CAVE_SPIDER, count: 6, delay: 2 }
      - { point: "5", type: SKELETON, count: 3, affixes: [plastic], delay: 1 }
```

- [ ] **Step 3: Implement remaining Act1 global weak (5 strategies)**

Follow `docs/superpowers/plans/waveplan` lines 11–33:
- `w1_patrol_riders`: SPIDER + SKELETON passenger, spider `coeff.speed: 1.5`, points 1/3/5 delay 10.
- `w1_accident`: stagger first skeleton via split steps on point 2 (+2s) and point 5 (+4s).
- `w1_symbiosis`: ENDERMITE batches point 3/4; `point: boss` VINDICATOR hp×2.
- `w1_symptom`: ZOMBIE then SLIME with `on_death` ENDERMITE×3, `[sticky, infected]`.
- `w1_herd`: 8 ZOMBIE ×4 steps; point 5 `speed: 1.2`.

- [ ] **Step 4: Implement Act1 global strong (3)**

- `s1_resort_ghosts`: ZOMBIE + `mob_iron_spear`; point 3 ZOMBIFIED_PIGLIN + `mob_gold_spear`.
- `s1_low_altitude`: nested STRIDER→STRIDER→ZOMBIE/SKELETON; bottom strider speed×2; zombie/skeleton dmg×2; gold spear on zombie.
- `s1_vent_breeze`: gold spear/gold axe zombies speed×1.5; BREEZE×5 affixes `[squid]` delay 5.

- [ ] **Step 5: Implement Act1 map strategies (6)**

Define but **do not** add to global pools:
- `w1_desert_four_camels`: CAMEL_HUSK + passengers (ZOMBIE plastic iron spear iron helm / SKELETON power1 bow), points 1–4.
- `w1_desert_prisoners`: point 5 HUSK×30 `[quicksand]` hp×2 gold shovel delay 1.
- `w1_desert_plague`: CREEPER scale/speed variants; point 5 invisible creeper.
- `w1_jungle_shadows`: DROWNED/BOGGED `[hidesuwa]`, repeat 2, 15s between via second step delay 15.
- `w1_jungle_spider_swap`: BOGGED/WITCH on CAVE_SPIDER passengers.
- `w1_jungle_spider_falls`: point 5 CAVE_SPIDER×40 `[poisonous]` delay 1.

- [ ] **Step 6: Implement Act1 bosses (3)**

- `b1_colossus`: IRON_GOLEM boss scale 1.2 hp 10 speed 1.3 `[truck, mechanical, cataclysm]`.
- `b1_eternal_rest`: minion steps on 1/2/5; boss SKELETON delay 15 hp×30 full iron ub10 + `mob_iron_sword_reach`; affixes for wither/slowness on boss use `[withering]` or spawn potions via affix — use `[mechanical]` + custom: add affix `eternal_curse` if needed with SLOWNESS+WITHER amp 255 dur 0, or `[withering]` only.
- `b1_bandit_chief`: strider stack PILLAGER boss delay 5; minion waves per plan.

- [ ] **Step 7: Run tests**

Run: `mvn -q test`

---

### Task 4: `waves.yml` — Act 2 strategies + pools

**Files:**
- Modify: `src/main/resources/waves.yml`

- [ ] **Step 1: Write pools.act2**

List all 6 weak, 6 strong, 3 boss ids with weight 2.

- [ ] **Step 2: Act2 weak (6 strategies)**

Key patterns from waveplan lines 93–121:
- `w2_merchant_convoy`: CREEPER hp×3 `on_death` TNT×1 `[infected]` repeat via 2 steps (delay 10); VINDICATOR scale×2 hp×5 dmg×10 speed×0.5.
- `w2_endless_spear`: point 3/4 ZOMBIE×10 iron spear speed×2 hp×1.5; point 4 stagger +5s first mob.
- `w2_fireworks`: creeper waves + BLAZE on STRIDER + GHAST on STRIDER (verify entity).
- `w2_lord_legacy`: ZOMBIE×30 hp×1.5 `on_death` ZOMBIE hp×3 `[infected]`.
- `w2_nest`: ENDERMITE waves; point 5 batch + giant ENDERMITE scale×4 hp×10 speed×0.9 dmg×3 (split step delay 1).
- `w2_territory`: 4 CREEPER hp×100 per point; boss CREEPER scale×2.5 hp×10 `on_death` 4 small fast creepers.

- [ ] **Step 3: Act2 strong (6 strategies)**

Including `s2_crimson_tunnel`: ZOMBIE×100 hp×0.2 `[fortified]` delay 1.

- [ ] **Step 4: Act2 map + grave strategies**

- badlands ×3 under `s2_badlands_*`
- `w2_grave_blind_box`: 13 slime variants ×50 (see Task 6 helper)
- `w2_grave_march`: copy from spec
- `s2_grave_phantoms`: invisible pillager/vindicator/witch/evoker waves

- [ ] **Step 5: Act2 bosses**

- `b2_silent_guard`: all piglin `[cataclysm]` on boss; gold armor equipment; piglin brutes waves with cataclysm on steps 1–2.
- `b2_breath`: plastic vindicator each point; warden boss delay 30 `[blinding, cataclysm]`.
- `b2_frost_farewell`: **every step** mobs get `[frostgrasp, antifreeze]` on all STRAY/CREEPER/boss entries.

- [ ] **Step 6: Run tests**

Run: `mvn -q test`

---

### Task 5: `waves.yml` — Act 3 strategies + pools

**Files:**
- Modify: `src/main/resources/waves.yml`

- [ ] **Step 1: Write pools.act3**

```yaml
  act3:
    weak: []   # waves_per_act weak: 0
    strong:
      - { strategy: s3_surprise_factory, weight: 2 }
      # ... all 9 strong ids
    boss:
      - { strategy: b3_into_eternal, weight: 2 }
      - { strategy: b3_sentinel, weight: 2 }
      - { strategy: b3_human_radiance, weight: 2 }
```

- [ ] **Step 2: Act3 weak (3)** — `w3_duck_ops`, `w3_rabbit_holes`, `w3_lights_out`

Note: include in strategies section for completeness; pools.act3.weak stays empty unless `waves_per_act` changed.

- [ ] **Step 3: Act3 strong (9)**

Including complex ones:
- `s3_chaos_facade`: 5 steps × ~18 entity types each (no flying: exclude PHANTOM, GHAST, etc.; no enderman teleport — use plan list literally).
- `s3_rhine_guard`: 4 SLIME steps with nested on_death CAMEL→passengers→on_death ENDERMITE; delays 20/40/60/80.
- `s3_institution`: split mixed vindicator/witch steps (YAML cannot do "2 invisible" in one step — use 2+2 steps or count with same affix batch).

- [ ] **Step 4: Act3 bosses (3)**

- `b3_human_radiance`: boss SLIME immediate; points 1–4 use radiance variant mixing (Task 6).
- `b3_sentinel`: ELDER_GUARDIAN boss delay 10 `[teleport, blinding, cataclysm]`.

- [ ] **Step 5: Run tests**

Run: `mvn -q test`

---

### Task 6: Slime variant mixing helper (blind box + human radiance)

**Files:**
- Modify: `src/main/resources/waves.yml` (strategies `w2_grave_blind_box`, `b3_human_radiance`)

**Interfaces:**
- Produces: reusable YAML pattern for 13 `on_death` templates.

- [ ] **Step 1: Define 13 on_death templates as YAML anchors or inline repeats**

Blind box variants (parent always SLIME `[sticky, infected]`):

| # | on_death spawn |
|---|----------------|
| 1 | ZOMBIE speed×2 dmg×2 |
| 2 | SKELETON hp×3 |
| 3 | CREEPER hp×20 speed×2 |
| 4 | SPIDER scale×0.4 hp×1.5 |
| 5 | WITHER_SKELETON |
| 6 | ZOMBIFIED_PIGLIN gold spear hp×2 |
| 7 | ENDERMITE×5 |
| 8 | CAVE_SPIDER×2 |
| 9 | ZOGLIN or ZOMBIFIED_PIGLIN — use `HOGLIN` if plan says 僵尸疣猪兽 → `ZOMBIFIED_PIGLIN` for hoglin-like |
| 10 | BOGGED `[hidesuwa]` |
| 11 | STRAY speed×2 punch2 bow |
| 12 | HUSK hp×5 speed×0.8 dmg×2 `[quicksand]` |
| 13 | DROWNED trident |

Radiance boosts per waveplan lines 298–311 (higher coeffs + extra affixes).

- [ ] **Step 2: Distribute 50 slimes across points 1–5**

Pattern per point (10 slimes): 13 variants → use 10 steps with `count: 1` each (different on_death) OR 13×4=52 ≈ 50: rotate variants, `count: 4` for first 12 variants + `count: 2` for one variant.

Example step:

```yaml
      - point: "1"
        type: SLIME
        count: 4
        affixes: [sticky, infected]
        delay: 1
        on_death:
          - { type: ZOMBIE, count: 1, coeff: { speed: 2.0, dmg: 2.0 } }
```

Repeat with next variant on same point, delay 1 between batches.

- [ ] **Step 3: Human radiance batching (pause 20s every 10)**

Per point 1–4, five steps:

```yaml
      - { point: "1", type: SLIME, count: 10, affixes: [sticky, infected], delay: 0, on_death: [...] }
      - { point: "1", type: SLIME, count: 10, affixes: [sticky, infected], delay: 20, on_death: [...] }
      # ... 3 more batches (total 50)
```

Rotate radiance variants across the 50 slimes.

---

### Task 7: Map `special_waves.yml` files

**Files:**
- Modify: `src/main/resources/maps/act1/desert/special_waves.yml`
- Modify: `src/main/resources/maps/act1/jungle/special_waves.yml`
- Modify: `src/main/resources/maps/act2/badlands/special_waves.yml`
- Modify: `src/main/resources/maps/act2/graveyard/special_waves.yml`

- [ ] **Step 1: act1/desert/special_waves.yml**

```yaml
weak:
  - { strategy: w1_desert_four_camels, weight: 5 }
  - { strategy: w1_desert_prisoners, weight: 5 }
  - { strategy: w1_desert_plague, weight: 5 }
```

- [ ] **Step 2: act1/jungle/special_waves.yml**

```yaml
weak:
  - { strategy: w1_jungle_shadows, weight: 5 }
  - { strategy: w1_jungle_spider_swap, weight: 5 }
  - { strategy: w1_jungle_spider_falls, weight: 5 }
```

- [ ] **Step 3: act2/badlands/special_waves.yml**

```yaml
strong:
  - { strategy: s2_badlands_fake_death, weight: 5 }
  - { strategy: s2_badlands_contamination, weight: 5 }
  - { strategy: s2_badlands_bully, weight: 5 }
```

- [ ] **Step 4: act2/graveyard/special_waves.yml**

```yaml
weak:
  - { strategy: w2_grave_blind_box, weight: 5 }
  - { strategy: w2_grave_march, weight: 5 }
strong:
  - { strategy: s2_grave_phantoms, weight: 5 }
```

- [ ] **Step 5: Leave act3/mushrooms/special_waves.yml commented-only**

---

### Task 8: Validation and deploy smoke

**Files:**
- Test: existing `src/test/java/io/mczju/maggoteers/plan/ComposeTest.java`, `StrongWaveRollTest.java`

- [ ] **Step 1: Run full test suite**

Run: `mvn test`
Expected: PASS

- [ ] **Step 2: Grep validation**

Run:

```bash
rg "strategy: w1_z_" src/main/resources/waves.yml
rg "w1_desert" src/main/resources/maps/act1/desert/special_waves.yml
```

Expected: no old placeholder ids (`w1_z_nm`, `b2_ravager`, etc.).

- [ ] **Step 3: Deploy to test server**

Use `.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1` or `mvn package` + copy to `E:/MCpaper/plugins`.

- [ ] **Step 4: In-game smoke**

- `/mgc join maggoteers` on desert map — confirm special wave can roll.
- Kill slime in blind box wave — verify on_death spawn counts toward wave clear.
- Confirm mobs chase from farther away (follow range ×5).

---

## Plan Self-Review

**Spec coverage:** All spec sections map to Tasks 1–8. Strategy catalog (~50 ids) split across Tasks 3–5. Blind box / radiance → Task 6. special_waves → Task 7.

**Placeholder scan:** No TBD tasks; entity/type edge cases flagged inline (GHAST, HOGLIN, eternal_rest curse affix).

**Gaps flagged for implementer:**
- `b1_eternal_rest` boss needs SLOWNESS 255 + WITHER 255 at spawn — if `[withering]` insufficient, add `eternal_curse` affix in Task 1.
- Spider knight = `SPIDER` + `SKELETON` passenger (not vanilla jockey type).
- `w2_merchant_convoy` TNT on_death: `{ type: TNT, count: 1 }` — verify `EntityType.TNT` spawns primed TNT.
- Act3 weak strategies defined but not pooled (`waves_per_act.act3.weak: 0`).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-26-waveplan-waves.md`. Two execution options:

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks

**2. Inline Execution** — implement all tasks in this session with checkpoints

Which approach?
