# Configurable Magic Weapon System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-weapon Java handlers with a config-driven magic weapon system (`use_ability`), unified magic damage/heal execution, virtual `MAGIC_DAMAGE` stat, and reward trigger validation.

**Architecture:** Virtual stats live in `PlayerState` only; `EffectService.executeMagicEffect` is the single core for `DAMAGE_AREA` / `DAMAGE_BEAM` / `HEAL_AREA`; weapons route through `ItemAbilityRegistry` + `ConfigMagicHandler`; rewards keep existing `PlayerEffect` + `fireTrigger` path into the same core.

**Tech Stack:** Paper 26.2 API, Java 25, Maven, JUnit 5, existing `EffectService` / `RewardService` / `MagicFxConfig`.

**Spec:** `docs/superpowers/specs/2026-07-27-config-magic-abilities-design.md` (v1.2)

## Global Constraints

- Paper **26.2**; JDK **25**; build via `.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy` for tests.
- Absolute coords never in map configs; weapon/reward definitions in `items/*.yml` + `rewards.yml` only.
- `use_ability.cooldown_sec` is authoritative CD; ItemCreator `useCooldown` cosmetic only.
- `MAGIC_DAMAGE` multiplier: `1.0 + sum(PERCENT values)`; FLAT unsupported v1.
- Reward `fireTrigger` whitelist: omit, `ON_KILL`, `ON_DAMAGE_DEALT`, `ON_DAMAGE_TAKEN` only.
- HEAL_AREA load: accept `targets: allies` or `both` + `include_self: true`; else warn+skip.
- Damage defaults: `targets: enemies`, `enemy_scope: tracked`.
- Keep `registerHandler` as optional override escape hatch.

---

## File map (created / modified)

| File | Role |
|------|------|
| `effect/VirtualStats.java` | **Create** — `MAGIC_DAMAGE` registry |
| `effect/PlayerCombatStats.java` | **Create** — multiplier aggregation |
| `effect/MagicDamageContext.java` | **Create** — same-tick re-entry guard |
| `effect/TargetResolver.java` | **Create** — allies/enemies selection |
| `effect/ItemAbility.java` | **Create** — immutable ability DTO |
| `effect/ItemAbilityRegistry.java` | **Create** — parse `use_ability` from items YAML |
| `item/interact/ConfigMagicHandler.java` | **Create** — generic weapon right-click |
| `effect/Effect.java` | **Modify** — add `DAMAGE_BEAM`, `HEAL_AREA` |
| `effect/EffectKeys.java` | **Modify** — `RAY_LENGTH`, `BEAM_RADIUS`, reuse `INCLUDE_SELF` |
| `effect/EffectService.java` | **Modify** — virtual attr, `executeMagicEffect`, `executeAbility` |
| `effect/EffectListener.java` | **Modify** — respect `MagicDamageContext` on `ON_DAMAGE_DEALT` |
| `util/GameRegistries.java` | **Modify** — `isVirtualAttribute` |
| `reward/RewardOption.java` | **Modify** — virtual putAttr; parse beam keys |
| `reward/RewardService.java` | **Modify** — load validation whitelist |
| `item/ItemInteractRouter.java` | **Modify** — registry route; remove tier-3 |
| `item/fx/MagicFxConfig.java` | **Modify** — layer-3 from `use_ability.fx` / legacy `use_fx` |
| `MaggoteersPlugin.java` | **Modify** — load registry; remove 3 handler registrations |
| `resources/items/maggoteers.yml` | **Modify** — `use_ability` for 3 weapons |
| `resources/rewards.yml` | **Modify** — remove interact passive; add targets; P4 entries |
| Tests under `src/test/java/...` | **Create** — see tasks |

**Delete after parity:** `TestBladeHandler.java`, `ExcaliburHandler.java`, `HealingStaffHandler.java`

---

### Task 1: VirtualStats + PlayerCombatStats

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/VirtualStats.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/PlayerCombatStats.java`
- Modify: `src/main/java/io/mczju/maggoteers/util/GameRegistries.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/PlayerCombatStatsTest.java`

**Interfaces:**
- Produces: `VirtualStats.parse(String) -> Optional<VirtualStats>`
- Produces: `GameRegistries.isVirtualAttribute(String) -> boolean`
- Produces: `PlayerCombatStats.magicDamageMultiplier(MaggoteersGame, UUID) -> double`

- [ ] **Step 1: Write failing test**

```java
// PlayerCombatStatsTest.java
@Test
void multiplierSumsPercentLayers() {
    List<PlayerEffect> effects = List.of(
        attrEffect("m1", 0.10),
        attrEffect("m2", 0.15)
    );
    assertEquals(1.25, PlayerCombatStats.magicDamageMultiplierFromEffects(effects), 1e-9);
}

@Test
void ignoresNonMagicAttributes() {
    // ADD_ATTRIBUTE ATTACK_DAMAGE only -> multiplier 1.0
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy -TestOnly`  
(or `mvn -q test -Dtest=PlayerCombatStatsTest`)

- [ ] **Step 3: Implement**

```java
// VirtualStats.java
public enum VirtualStats { MAGIC_DAMAGE;
  public static Optional<VirtualStats> parse(String name) { ... }
}

// GameRegistries.java
public static boolean isVirtualAttribute(String name) {
  return VirtualStats.parse(name).isPresent();
}

// PlayerCombatStats.java — package-private helper for tests
static double magicDamageMultiplierFromEffects(List<PlayerEffect> effects) {
  double sum = 0.0;
  for (PlayerEffect e : effects) {
    if (e.effect() != Effect.ADD_ATTRIBUTE) continue;
    if (!"MAGIC_DAMAGE".equalsIgnoreCase(e.params().get(EffectKeys.ATTR_NAME))) continue;
    if (!"PERCENT".equalsIgnoreCase(e.params().getOrDefault(EffectKeys.OP, "FLAT"))) continue;
    sum += e.params().getOrDefault(EffectKeys.VALUE, 0.0);
  }
  return 1.0 + sum;
}
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit** (only if user requested commits)

---

### Task 2: Virtual ADD_ATTRIBUTE apply path

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardOption.java` (`putAttr`)
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java` (`apply`, `applyDerived`, `applyAttribute`)
- Create: `src/test/java/io/mczju/maggoteers/config/VirtualAttributeConfigTest.java`

**Interfaces:**
- Consumes: Task 1 `GameRegistries.isVirtualAttribute`
- Produces: virtual `ADD_ATTRIBUTE` merged into `PlayerState` without Bukkit modifiers

- [ ] **Step 1: Test** — apply `MAGIC_DAMAGE` via `RewardService.applyStat` mock or direct `EffectService.apply`; assert `PlayerState.effects()` contains entry; assert player `ATTACK_DAMAGE` modifiers unchanged.

- [ ] **Step 2: Implement `putAttr`** — always `ctx.put(EffectKeys.ATTR_NAME, name)`; only `ctx.put(EffectKeys.ATTR, a)` when Bukkit attr non-null and not virtual.

- [ ] **Step 3: Implement `applyDerived`** — if virtual stat: return early (no `applyAttribute`).

- [ ] **Step 4: Run tests**

---

### Task 3: TargetResolver + MagicDamageContext

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/TargetResolver.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/MagicDamageContext.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectListener.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/TargetResolverTest.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/MagicDamageContextTest.java`

**Interfaces:**
- Produces: `TargetResolver.collect(MaggoteersGame, Player source, Location center, double radius, EffectContext params) -> List<LivingEntity>`
- Produces: `MagicDamageContext.run(MaggoteersGame, UUID source, Runnable)` try/finally mark
- Produces: `MagicDamageContext.shouldSuppressOnDamageDealt(MaggoteersGame, Player damager) -> boolean`

- [ ] **Step 1: TargetResolverTest** — enemies+tracked excludes Player allies and untracked entities (mock or stub `WaveEngine.isTracked`).

- [ ] **Step 2: Implement TargetResolver** — reuse patterns from `AuraService` / `AllyTargeting`; defaults `enemies` + `tracked`.

- [ ] **Step 3: MagicDamageContext** — key `(identityHashCode(game), uuid, Bukkit.getCurrentTick())` in `ThreadLocal` or static `Set`.

- [ ] **Step 4: EffectListener.onDamageDealt** — if `MagicDamageContext.shouldSuppressOnDamageDealt`, return before `fireTrigger`.

- [ ] **Step 5: Run tests**

---

### Task 4: executeMagicEffect + DAMAGE_AREA refactor (P1 core)

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`
- Modify: `src/main/resources/rewards.yml` — add `targets`/`enemy_scope` to `a1s_damage_area`
- Create: `src/test/java/io/mczju/maggoteers/effect/MagicEffectCoreTest.java` (pure logic where possible)

**Interfaces:**
- Produces: `private static void executeMagicEffect(Player, MaggoteersGame, Effect, EffectContext, @Nullable MagicFxContext)`
- Consumes: Tasks 1–3

- [ ] **Step 1: Extract `executeMagicEffect`** from existing `DAMAGE_AREA` case.

- [ ] **Step 2: Apply formula** — `finalDmg = base * PlayerCombatStats.magicDamageMultiplier(game, uuid)`.

- [ ] **Step 3: Wrap damage dealing** in `MagicDamageContext.run(...)`.

- [ ] **Step 4: Route `executeEffect` DAMAGE_AREA** through `executeMagicEffect`.

- [ ] **Step 5: Update `a1s_damage_area` YAML** — `targets: enemies`, `enemy_scope: tracked`.

- [ ] **Step 6: Run full test suite**

---

### Task 5: ItemAbilityRegistry + FX merge (P2)

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/ItemAbility.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/ItemAbilityRegistry.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java` — `ItemAbilityRegistry.load(this)` after `MagicFxConfig.load`
- Create: `src/test/java/io/mczju/maggoteers/effect/ItemAbilityRegistryTest.java`

**Interfaces:**
- Produces: `ItemAbilityRegistry.get(String itemId) -> Optional<ItemAbility>`
- Produces: `ItemAbility`: `cooldownSec()`, `effect()`, `params()`, `fx()` (merged `MagicUseFx`)

- [ ] **Step 1: ItemAbilityRegistryTest** — parse sample YAML; missing cooldown skips; HEAL_AREA invalid targets skips.

- [ ] **Step 2: Parse `use_ability`** from items YAML (mirror `MagicFxConfig` item scan); validate HEAL_AREA targets per spec §5.4.

- [ ] **Step 3: MagicFxConfig** — add `mergedFxForAbility(itemId, abilityFxSection)` honoring order: defaults → config.weapons → ability.fx; legacy `use_fx` alias with warning.

- [ ] **Step 4: Run tests**

---

### Task 6: ConfigMagicHandler + ItemInteractRouter (P2)

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/item/interact/ConfigMagicHandler.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemInteractRouter.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java` — public/package `executeAbility`

**Interfaces:**
- Consumes: `ItemAbilityRegistry.get`, `CooldownService`, `MagicFxService`, `executeMagicEffect`

- [ ] **Step 1: ConfigMagicHandler.onUse** — CD → FX → `EffectService.executeAbility(player, game, ability)`.

- [ ] **Step 2: executeAbility** — thin wrapper calling `executeMagicEffect` (FX already played).

- [ ] **Step 3: ItemInteractRouter** — order: ItemKind → `registerHandler` override → `ItemAbilityRegistry` → no cancel. **Delete tier-3 ON_INTERACT block** (lines ~120–142).

- [ ] **Step 4: Manual smoke** — not required for plan; note for human: main-hand only.

---

### Task 7: DAMAGE_BEAM + HEAL_AREA effects (P3)

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/Effect.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectKeys.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java` — beam + heal area in `executeMagicEffect`
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardOption.java` — parse `ray_length`, `beam_radius`
- Create: `src/test/java/io/mczju/maggoteers/effect/DamageBeamLogicTest.java` (length shorten math)

**Interfaces:**
- Produces: beam defaults `ray_length=32`, `beam_radius=1.25`; Excalibur parity behavior §5.3

- [ ] **Step 1: DamageBeamLogicTest** — target at 10 blocks → length 10 not 32.

- [ ] **Step 2: Implement DAMAGE_BEAM** — port loop from `ExcaliburHandler` into core; magic multiplier + MagicDamageContext.

- [ ] **Step 3: Implement HEAL_AREA** — `AllyTargeting.alliesInRadius`; no magic multiplier.

- [ ] **Step 4: Run tests**

---

### Task 8: Migrate weapons YAML + delete handlers (P3)

**Files:**
- Modify: `src/main/resources/items/maggoteers.yml`
- Delete: `TestBladeHandler.java`, `ExcaliburHandler.java`, `HealingStaffHandler.java`
- Modify: `MaggoteersPlugin.java` — remove 3 `registerHandler` lines

- [ ] **Step 1: Add `use_ability`** for `test_blade`, `excalibur`, `healing_staff` (move `use_fx` into `use_ability.fx`).

- [ ] **Step 2: Delete handler classes + registrations.**

- [ ] **Step 3: Deploy + manual test** whirlwind / beam / heal on `E:\MCpaper`.

---

### Task 9: Reward load validation + cleanup (P3)

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardService.java`
- Modify: `src/main/resources/rewards.yml`
- Create: `src/test/java/io/mczju/maggoteers/reward/RewardLoadValidationTest.java`

- [ ] **Step 1: Validation in `RewardService.load`** for each STAT option:
  - forbidden `fireTrigger` → warn + skip
  - `AURA` with trigger/expiry → warn + skip
  - `expiry.trigger == ON_INTERACT` → warn + skip
  - `MAGIC_DAMAGE` with `op: FLAT` → warn + skip
  - `HEAL_AREA` invalid targets → warn + skip

- [ ] **Step 2: Remove `a2s_damage_area_interact`** from rewards.yml.

- [ ] **Step 3: RewardLoadValidationTest** — feed bad option maps; assert skipped count / warnings.

- [ ] **Step 4: Run full test suite**

---

### Task 10: P4 pool content (minimal)

**Files:**
- Modify: `src/main/resources/rewards.yml`

- [ ] **Step 1: Add `MAGIC_DAMAGE` entries** — e.g. `a1w_magic_dmg` (+10%), `a2s_magic_dmg` (+15%) per spec §8.

- [ ] **Step 2: Add 1–2 tier WEAPON examples** (e.g. `flame_cleaver`) with `use_ability` in items YAML — can stub item if not art-ready.

- [ ] **Step 3: Align with `docs/superpowers/plans/rewardplan` notes** (optional content pass).

---

### Task 11: Documentation sync

**Files:**
- Modify: `CLAUDE.md` (§10, §15)
- Modify: `.cursor/skills/maggoteers-plugin-config/reference.md`
- Modify: `docs/dev-log.md`

- [ ] **Step 1: CLAUDE** — virtual stats, `use_ability`, handler escape hatch, reward fireTrigger whitelist.

- [ ] **Step 2: plugin-config reference** — `use_ability` schema, HEAL_AREA validation, MAGIC_DAMAGE.

- [ ] **Step 3: dev-log entry** dated 2026-07-27.

---

## Spec coverage self-check (v1.2)

| Spec section | Task |
|--------------|------|
| §4 use_ability schema | 5, 8 |
| §4.2 cooldown authority | 6 |
| §4.3 FX merge | 5 |
| §5 targets/enemy_scope | 3, 4 |
| §5.3 DAMAGE_BEAM | 7 |
| §5.4 HEAL_AREA validation | 5, 7, 9 |
| §6 MAGIC_DAMAGE virtual | 1, 2 |
| §6.3 multiplier formula | 1, 4 |
| §7.1 router + escape hatch | 6, 8 |
| §7.3 single core | 4, 6, 7 |
| §7.4 re-entry guard | 3, 4 |
| §3.2–3.3 reward validation | 9 |
| §9 migration | 8, 9 |
| §10 P4 content | 10 |
| §12 docs | 11 |
| P5 Lore | deferred |

---

## Verification gate (final)

Run: `.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy`  
Expected: all tests PASS.

Manual checklist:
- [ ] Main-hand whirlwind damages tracked mobs only, not ally
- [ ] Kill shockwave respects MAGIC_DAMAGE stacks
- [ ] Excalibur beam shortens to targeted mob
- [ ] Healing staff heals allies in radius
- [ ] No ON_INTERACT reward in pools; load warns on bad test fixture
