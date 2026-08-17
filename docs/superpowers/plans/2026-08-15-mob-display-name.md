# Mob Display Name + Boss Bar Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `waves.yml` set MiniMessage `name:` and opt-in `boss_bar: true` on mobs; suppress InfernalMobs nametags on Maggoteers-spawned entities; replace Bukkit String BossBars with Adventure BossBars.

**Architecture:** Parse `name` / `boss_bar` through Config → RunPlan → MobFactory → WaveEngine. After IM `mechanize` (+ equipment), call `MobDisplayNames.lock`. Tick + short delayed reassert fights IM `refreshDisplayName`. Boss bars attach only when `bossBar==true`.

**Tech Stack:** Java 25, Paper 26.2, Adventure MiniMessage + Adventure BossBar, JUnit 5, Maven

**Spec:** `docs/superpowers/specs/2026-08-15-mob-display-name-design.md`

## Global Constraints

- Paper `26.2.build.62-beta`; Java/Maven release 25.
- Do not modify InfernalMobs JAR/source; no compile dependency on IM.
- Keep MobFactory order: stats → potions/visuals → **mechanize** → **equipment** → **lock** (never move equipment before mechanize).
- Abolish heuristic `count==1 && no passengers && hpMult>=6`; only `boss_bar: true`.
- `boss_bar` YAML must be Boolean only (string `"true"` hard-fails).
- `name` trim; blank after trim = no custom name.
- Working tree may have unrelated dirty files; only stage files for this feature; commit only if user asks.
- Content migration must follow Spec Appendix A (Task 6 product defaults below).

---

## File structure

**Create:**
- `src/main/java/io/mczju/maggoteers/mob/MobDisplayNames.java`
- `src/test/java/io/mczju/maggoteers/mob/MobDisplayNamesTest.java`
- `src/test/java/io/mczju/maggoteers/config/MobDisplayParseTest.java` (or extend `MobYamlParserTest`)

**Modify:**
- `config/{StepCfg,PassengerCfg,DeathSpawnCfg,MobYamlParser,WavesConfig}.java`
- `wave/{SpawnStep,PassengerSpawn,DeathSpawn,MobSpawnProfile,WaveSpec,WaveEngine,WaveRuntime}.java`
- `plan/RunPlanner.java`
- `mob/MobFactory.java`
- `src/main/resources/waves.yml` (Appendix A)
- Tests that construct `SpawnStep` / `StepCfg` / `DeathSpawn` / `PassengerSpawn` / `MobSpawnProfile` (fix constructors)
- `.cursor/skills/maggoteers-plugin-config/reference.md` (brief field docs)
- `docs/dev-log.md`
- Spec status → approved/implemented when done

---

### Task 1: Parse `name` / `boss_bar`

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/config/MobYamlParser.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/StepCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/PassengerCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/DeathSpawnCfg.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/WavesConfig.java`
- Test: `src/test/java/io/mczju/maggoteers/config/MobYamlParserTest.java`

**Interfaces:**
- Produces: `MobYamlParser.parseName(Map<?,?> m, String context) → String|null`
- Produces: `MobYamlParser.parseBossBar(Map<?,?> m, String context) → boolean`
- Produces: `StepCfg` / `PassengerCfg` / `DeathSpawnCfg` gain `String name`, `boolean bossBar`

- [ ] **Step 1: Add failing parse tests**

```java
@Test
void parsesNameAndBossBar() {
    Map<String, Object> m = Map.of(
            "type", "ZOMBIE",
            "name", "  <gold>Boss</gold>  ",
            "boss_bar", true);
    assertEquals("<gold>Boss</gold>", MobYamlParser.parseName(m, "t"));
    assertTrue(MobYamlParser.parseBossBar(m, "t"));
}

@Test
void blankNameIsNull() {
    assertNull(MobYamlParser.parseName(Map.of("name", "  "), "t"));
    assertNull(MobYamlParser.parseName(Map.of(), "t"));
}

@Test
void bossBarStringFails() {
    assertThrows(IllegalStateException.class,
            () -> MobYamlParser.parseBossBar(Map.of("boss_bar", "true"), "t"));
}

@Test
void nameNonStringFails() {
    assertThrows(IllegalStateException.class,
            () -> MobYamlParser.parseName(Map.of("name", 1), "t"));
}
```

- [ ] **Step 2: Run tests — expect FAIL (methods missing)**

```powershell
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy -Dtest=MobYamlParserTest
```

(or IntelliJ Maven `test -Dtest=MobYamlParserTest`)

- [ ] **Step 3: Implement parsers + wire into StepCfg / PassengerCfg / DeathSpawnCfg / WavesConfig / MobYamlParser nests**

```java
public static String parseName(Map<?, ?> m, String context) {
    if (!m.containsKey("name") || m.get("name") == null) return null;
    Object raw = m.get("name");
    if (!(raw instanceof String s)) {
        throw new IllegalStateException("name must be string (" + context + ")");
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
}

public static boolean parseBossBar(Map<?, ?> m, String context) {
    if (!m.containsKey("boss_bar") || m.get("boss_bar") == null) return false;
    Object raw = m.get("boss_bar");
    if (!(raw instanceof Boolean b)) {
        throw new IllegalStateException("boss_bar must be boolean (" + context + ")");
    }
    return b;
}
```

Add `String name, boolean bossBar` to the three cfg records (default `null`/`false` in compact constructors). In `WavesConfig.parseStrategy` and passenger/on_death builders, pass `parseName` / `parseBossBar` with context like `strategyId + " step"`.

- [ ] **Step 4: Re-run `MobYamlParserTest` — PASS**

- [ ] **Step 5: Commit only if user asked** (otherwise skip)

---

### Task 2: Thread fields through RunPlan models

**Files:**
- Modify: `wave/SpawnStep.java`, `PassengerSpawn.java`, `DeathSpawn.java`, `MobSpawnProfile.java`, `WaveSpec.java`
- Modify: `plan/RunPlanner.java`
- Fix: all test call sites compiling against new record components

**Interfaces:**
- Consumes: cfg `name()` / `bossBar()`
- Produces: `SpawnStep.name()`, `SpawnStep.bossBar()`; same on `PassengerSpawn`, `DeathSpawn`; `MobSpawnProfile(name, bossBar, dropMult, …)` — put `name`/`bossBar` after existing fields or at end for minimal churn; **keep one consistent order across records: `…, String name, boolean bossBar` at end before `repeat` on SpawnStep**.

Recommended component order addition:
- `SpawnStep`: after `passengers`, before `repeat`: `String name, boolean bossBar` — actually put **after repeat** to reduce ctor breakage: append `String name, boolean bossBar` as last components.
- Same append pattern for others.
- `MobSpawnProfile`: `double dropMult, List affixes, InfernalCfg infernal, List onDeath, String name, boolean bossBar`

- [ ] **Step 1: Update records + RunPlanner `new SpawnStep` / passenger / death constructors to pass `sc.name()`, `sc.bossBar()` (and pc/dc equivalents)**

- [ ] **Step 2: Update `MobSpawnProfile.fromStep/fromPassenger/fromDeathSpawn` to copy name/bossBar**

- [ ] **Step 3: `mvn test-compile` / full test — fix every broken constructor in tests**

- [ ] **Step 4: Commit if user asked**

---

### Task 3: `MobDisplayNames` lock + reassert helpers

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/mob/MobDisplayNames.java`
- Create: `src/test/java/io/mczju/maggoteers/mob/MobDisplayNamesTest.java`

**Interfaces:**
- Produces:
  - `void lock(LivingEntity le, String nameOrNull)`
  - `void reassertIfNeeded(LivingEntity le)`
  - `Optional<String> lockedDisplayName(LivingEntity le)`  // MiniMessage raw if present
  - `boolean hasNameLock(LivingEntity le)`
  - `Component titleComponent(LivingEntity le, EntityType fallbackType)` for boss bar

PDC keys: `new NamespacedKey(plugin, "name_lock")` BYTE `1`; `display_name` STRING (only when non-null name).

Without MockBukkit, unit-test pure helpers:

```java
static String normalizeConfiguredName(String raw) { /* trim; empty→null */ }
static Component resolveTitle(String lockedMmOrNull, EntityType type) {
    if (lockedMmOrNull != null) return MiniMessage.miniMessage().deserialize(lockedMmOrNull);
    return Component.text(type.name());
}
```

- [ ] **Step 1: Failing tests for normalize + resolveTitle**

```java
assertNull(MobDisplayNames.normalizeConfiguredName("  "));
assertEquals("<red>A</red>", MobDisplayNames.normalizeConfiguredName(" <red>A</red> "));
assertEquals("ZOMBIE", PlainTextComponentSerializer.plainText()
        .serialize(MobDisplayNames.resolveTitle(null, EntityType.ZOMBIE)));
```

- [ ] **Step 2: Implement `MobDisplayNames` (lock/reassert use Bukkit PDC + Adventure customName)**

`lock`: set name_lock; if name non-null after normalize → set display_name PDC + `le.customName(MM.deserialize)` + visible true; else remove display_name + `customName(null)` + visible false.

`reassertIfNeeded`: if no name_lock return; compare intent vs current; if mismatch call lock again.

- [ ] **Step 3: Tests PASS**

- [ ] **Step 4: Commit if asked**

---

### Task 4: MobFactory — remove affix nametags; lock after mechanize

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`

**Interfaces:**
- Consumes: `step.name()`, `ps.name()`, `ds.name()`; `MobDisplayNames.lock`
- After `lock`, schedule reassert: `runTask` + `runTaskLater(1)` + `runTaskLater(2)` calling `reassertIfNeeded`

- [ ] **Step 1: In `spawnMount` / `spawnPassengerEntity` / `spawnDeathMob`, delete `applyName(...)`; after `applyEquipment`, call:**

```java
MobDisplayNames.lock(le, /* name from step/ps/ds */);
scheduleNameReassert(le);
```

```java
private static void scheduleNameReassert(LivingEntity le) {
    var plugin = MaggoteersPlugin.getInstance();
    Runnable r = () -> { if (le.isValid()) MobDisplayNames.reassertIfNeeded(le); };
    Bukkit.getScheduler().runTask(plugin, r);
    Bukkit.getScheduler().runTaskLater(plugin, r, 1L);
    Bukkit.getScheduler().runTaskLater(plugin, r, 2L);
}
```

- [ ] **Step 2: Ensure `spawnDebugMob` path ends in same lock (name null → clear IM/affix names). Fake `SpawnStep` must include name/bossBar fields.**

- [ ] **Step 3: Delete private `applyName` method entirely**

- [ ] **Step 4: Compile + existing unit tests PASS**

---

### Task 5: WaveEngine Adventure BossBar + flag gate

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveRuntime.java`
- Modify: `src/main/java/io/mczju/maggoteers/wave/WaveEngine.java`

**Interfaces:**
- `WaveRuntime.bossBars`: change to `Map<UUID, net.kyori.adventure.bossbar.BossBar>`
- Attach when `step != null ? step.bossBar() : profile.bossBar()`
- Title via `MobDisplayNames.resolveTitle(lockedDisplayName.orElse(null), le.getType())`
- Show: `pe.player().showBossBar(bar)`; clear: `hideBossBar`
- `tick`: update `bar.progress(...)`; call `MobDisplayNames.reassertIfNeeded(le)` for living tracked mobs with name_lock

- [ ] **Step 1: Replace heuristic block**

Delete:
```java
if (step != null && step.count() == 1 && step.passengers().isEmpty() && step.hpMult() >= 6.0)
```

Replace with reading bossBar from step or profile:

```java
boolean wantBar = step != null ? step.bossBar()
        : (profile != null && profile.bossBar());
if (wantBar) attachBossBar(game, rt, le);
```

- [ ] **Step 2: Rewrite `attachBossBar` / cleanup / death removal for Adventure BossBar**

```java
private static void attachBossBar(AbstractGame game, WaveRuntime rt, LivingEntity le) {
    Component title = MobDisplayNames.resolveTitle(
            MobDisplayNames.lockedDisplayName(le).orElse(null), le.getType());
    var bar = net.kyori.adventure.bossbar.BossBar.bossBar(
            title, 1f,
            net.kyori.adventure.bossbar.BossBar.Color.RED,
            net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS);
    for (var pe : game.getPlayers()) pe.player().showBossBar(bar);
    rt.bossBars.put(le.getUniqueId(), bar);
}
```

- [ ] **Step 3: Update all `BossBar::removeAll` / `setProgress` call sites to Adventure equivalents**

- [ ] **Step 4: Compile + tests**

---

### Task 6: `waves.yml` Appendix A migration

**Files:**
- Modify: `src/main/resources/waves.yml`

**Product defaults (apply unless user overrides before this task):**

| Item | Action |
|---|---|
| A1 clear bosses (`w2_nest` boss, `w2_territory` boss, `b2_frost_farewell` both boss STRAY, `b3_into_eternal` point5 PIGLIN + boss WITHER_SKELETON, `b3_human_radiance` boss SLIME, `s2_forbidden` IRON_GOLEM) | add `boss_bar: true` |
| A1 `w3_duck_ops` CHICKEN/POLAR_BEAR | **do not** add (drop accidental bars) |
| A1 `b3_sentinel` DROWNED hp6 | **do not** add (drop accidental bars) |
| A1 `s3_rhine_guard` SLIME hp6 | **do not** add unless clearly a mini-boss; default drop |
| A2 all other `point: boss` steps currently hp&lt;6 | add `boss_bar: true` (real bosses that never got bars) |
| A3 passengers / on_death | skip |
| `name:` | optional; add Chinese names at least for A1 bosses you touch (recommended MiniMessage) |

- [ ] **Step 1: Re-audit A0** — list every `point: boss` in `waves.yml`; mark each with boss_bar decision in PR notes / dev-log

- [ ] **Step 2: Apply YAML edits** (boolean `boss_bar: true` only; never `"true"`)

Example:
```yaml
- point: boss
  type: CREEPER
  count: 1
  name: "<gold>领主苦力怕</gold>"
  boss_bar: true
  coeff: { hp: 10.0, scale: 2.5 }
```

- [ ] **Step 3: Ensure WavesConfig still loads** (unit test that loads `waves.yml` if one exists; else `mvn test` covering config load)

---

### Task 7: Docs + skill reference

**Files:**
- Modify: `.cursor/skills/maggoteers-plugin-config/reference.md` (document `name`, `boss_bar`)
- Modify: `docs/dev-log.md` (date, decisions, Appendix A defaults used)
- Modify: spec status line to implemented / ready for playtest
- Optionally `CLAUDE.md` one-liner under waves if it mentions boss bars

- [ ] **Step 1: Write docs**
- [ ] **Step 2: Full `mvn test` via build-and-deploy `-SkipDeploy`**
- [ ] **Step 3: Offer deploy if user wants in-game verify**

---

## Self-review (plan vs spec)

| Spec requirement | Task |
|---|---|
| `name:` / `boss_bar:` parse + hard-fail | T1 |
| Thread to RunPlan / profile | T2 |
| lock after mechanize; kill applyName | T3–T4 |
| equipment after mechanize unchanged | T4 |
| Adventure BossBar + title MM | T5 |
| Abolish heuristic | T5 |
| 5-tick + 0/1/2 reassert | T4 + T5 tick |
| spawnDebugMob lock | T4 |
| Appendix A migration | T6 |
| infernal without name → no custom name | T3–T4 + test intent in T1/T3 |
| trim / boolean-only | T1 |

No TBD placeholders. Constructor order must stay consistent across T2 call sites.

---

## Execution handoff

Plan saved to `docs/superpowers/plans/2026-08-15-mob-display-name.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — this session with executing-plans checkpoints  

Which approach?
