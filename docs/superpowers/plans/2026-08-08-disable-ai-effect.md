# DISABLE_AI Effect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `Effect.DISABLE_AI` — temporarily `setAI(false)` on hostile mobs for `duration_ticks`, with longer-wins re-apply, unload-safe restore, weapon `use_ability` + STAT combat triggers (Phase 1).

**Architecture:** Shared `executeMagicEffect` case (must also join `executeEffect` switch). Target resolve via `DisableAiTargets` (`enemies` | `hit_target` | `attacker`) plus hostile filter (skip players / allies / `SummonRegistry` summons). Mob lock state in `MobAiLockRegistry` with 1-tick scan and `EntityRemoveEvent` restore-before-drop.

**Tech Stack:** Paper 26.2, Java 25, Maven, JUnit 5 (no Mockito — registry tested via package-visible bookkeeping helpers).

**Spec:** `docs/superpowers/specs/2026-08-08-disable-ai-effect-design.md` (v1.1)

## Global Constraints

- Paper **26.2**; JDK **25**; test command:
  `E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd -f E:/Intellij_Idea/plugins/MCPlugin/pom.xml test`
  (fallback: `E:/Intellij_Idea/IntelliJ IDEA 2025.1.1.1/plugins/maven/lib/maven3/bin/mvn.cmd`)
- **Phase 1 only:** `use_ability` + STAT. Do **not** wire `held_effects` / `WeaponEffectValidator` DISABLE_AI (Phase 2 after held Path B).
- Mechanism: `LivingEntity.setAI(false/true)` only — no damage cancel, no velocity pin.
- Duration key: `duration_ticks` (>0 required).
- Re-apply: longer remaining wins (`max` expire tick); keep first `restoreAi`.
- Unload: if entity still valid → restore AI then drop registry (prevent permanent NoAI NBT).
- Hostile filter always: skip `Player`, dead/invalid, allies, `SummonRegistry.isTrackedSummon`.
- `ON_KILL` + `targets: hit_target` → **warn+skip** at load.
- Docs/YAML must be saved as **UTF-8**.

## File map

| File | Role |
|------|------|
| `effect/Effect.java` | Add `DISABLE_AI` |
| `effect/MagicEffectParams.java` | `withDefaults(DISABLE_AI)` + `validateDisableAi` |
| `effect/MobAiLockRegistry.java` | **Create** — lock, tick expire, unload take, restoreAll, start/stop |
| `effect/MobAiLockListener.java` | **Create** — EntityRemoveEvent |
| `effect/DisableAiTargets.java` | **Create** — resolve + hostile filter |
| `effect/BuffAreaTargets.java` | Add `TARGET_ATTACKER = "attacker"` |
| `effect/EffectService.java` | executeEffect case + executeMagicEffect body |
| `effect/ItemAbilityRegistry.java` | Weapon load validation |
| `reward/RewardLoadValidator.java` | STAT validate + mark_on coerce |
| `game/MaggoteersGame.java` | cleanup → restoreAll |
| `MaggoteersPlugin.java` | register listener; start/stop scan; onDisable restore |
| Tests under `src/test/java/io/mczju/maggoteers/effect/` | MagicEffectParamsDisableAiTest, MobAiLockRegistryTest, DisableAiTargetsTest |
| Reward tests | STAT DISABLE_AI skip cases |
| `CLAUDE.md`, skill reference, `docs/dev-log.md` | Doc sync |
| Optional `items/maggoteers.yml` sample | stun staff |

Out of plan (Phase 2): WeaponEffectValidator held DISABLE_AI.

---

### Task 1: Effect enum + MagicEffectParams validation

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/Effect.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/MagicEffectParams.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/BuffAreaTargets.java` (`TARGET_ATTACKER`)
- Create: `src/test/java/io/mczju/maggoteers/effect/MagicEffectParamsDisableAiTest.java`

**Interfaces:**
- `Effect.DISABLE_AI`
- `withDefaults` → `targets=enemies`, `enemy_scope=tracked`, `radius=5.0`
- `validateDisableAi(params, triggerOrNull, weaponPath)` → `Optional<String>` skip reason

- [ ] **Step 1: Write the failing test**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicEffectParamsDisableAiTest {

    private static EffectContext base() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        return p;
    }

    @Test
    void defaults() {
        EffectContext p = MagicEffectParams.withDefaults(Effect.DISABLE_AI, new EffectContext());
        assertEquals(AuraParams.TARGET_ENEMIES, p.get(EffectKeys.TARGETS));
        assertEquals(AuraParams.ENEMY_TRACKED, p.get(EffectKeys.ENEMY_SCOPE));
        assertEquals(5.0, p.get(EffectKeys.RADIUS));
    }

    @Test
    void missingDurationSkipped() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false)
                .orElse("").toLowerCase().contains("duration"));
    }

    @Test
    void zeroDurationSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.DURATION_TICKS, 0);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void hitTargetPlusOnDamageTakenSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_TAKEN, false).isPresent());
    }

    @Test
    void hitTargetPlusOnKillSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_KILL, false).isPresent());
    }

    @Test
    void attackerPlusOnDamageDealtSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_ATTACKER);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void attackerPlusOnDamageTakenAllowed() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_ATTACKER);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_TAKEN, false).isEmpty());
    }

    @Test
    void hitTargetPlusOnDamageDealtAllowed() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isEmpty());
    }

    @Test
    void statRequiresFireTrigger() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, null, false)
                .orElse("").toLowerCase().contains("trigger"));
    }

    @Test
    void weaponPathAllowsNullTrigger() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, null, true).isEmpty());
    }

    @Test
    void rejectsSelfTargets() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_SELF);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void rejectsAlliesTargets() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ALLIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isPresent());
    }

    @Test
    void enemiesWithNonPositiveRadiusSkipped() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        p.put(EffectKeys.RADIUS, 0.0);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false)
                .orElse("").toLowerCase().contains("radius"));
    }

    @Test
    void enemiesDefaultRadiusOkWhenOmitted() {
        EffectContext p = base();
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
        assertTrue(MagicEffectParams.validateDisableAi(p, Trigger.ON_DAMAGE_DEALT, false).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
& "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" `
  -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" `
  -Dtest=MagicEffectParamsDisableAiTest test
```

Expected: FAIL (enum / methods missing).

- [ ] **Step 3: Minimal implementation**

**`Effect.java`** — add enum constant (near other magic AoE effects):

```java
DISABLE_AI,
```

**`BuffAreaTargets.java`** — add constant:

```java
public static final String TARGET_ATTACKER = "attacker";
```

**`MagicEffectParams.withDefaults`** — add case:

```java
case DISABLE_AI -> {
    if (!p.has(EffectKeys.TARGETS)) {
        p.put(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES);
    }
    if (!p.has(EffectKeys.ENEMY_SCOPE)) {
        p.put(EffectKeys.ENEMY_SCOPE, AuraParams.ENEMY_TRACKED);
    }
    if (!p.has(EffectKeys.RADIUS)) {
        p.put(EffectKeys.RADIUS, 5.0);
    }
}
```

**`MagicEffectParams.validateDisableAi`** — full method:

```java
/**
 * DISABLE_AI load validation (spec §6).
 *
 * @param triggerOrNull STAT/held fireTrigger; null for weapon path
 * @param weaponPath    true = use_ability (fireTrigger not required)
 * @return empty if acceptable; otherwise short reason for warn+skip
 */
public static Optional<String> validateDisableAi(EffectContext params,
                                                 Trigger triggerOrNull,
                                                 boolean weaponPath) {
    if (params == null) {
        return Optional.of("missing params");
    }
    EffectContext p = withDefaults(Effect.DISABLE_AI, params);

    Integer duration = p.get(EffectKeys.DURATION_TICKS);
    if (duration == null || duration <= 0) {
        return Optional.of("duration_ticks must be > 0");
    }

    String targetsRaw = p.get(EffectKeys.TARGETS);
    String t = targetsRaw == null || targetsRaw.isBlank()
            ? AuraParams.TARGET_ENEMIES
            : targetsRaw.trim().toLowerCase(Locale.ROOT);

    if (!Set.of(
            AuraParams.TARGET_ENEMIES,
            BuffAreaTargets.TARGET_HIT_TARGET,
            BuffAreaTargets.TARGET_ATTACKER
    ).contains(t)) {
        return Optional.of("unsupported targets: " + t);
    }

    if (AuraParams.TARGET_ENEMIES.equals(t) && p.has(EffectKeys.RADIUS)) {
        Double radius = p.get(EffectKeys.RADIUS);
        if (radius != null && radius <= 0) {
            return Optional.of("radius must be > 0");
        }
    }

    if (AuraParams.TARGET_ENEMIES.equals(t) && p.has(EffectKeys.ENEMY_SCOPE)) {
        String scope = p.get(EffectKeys.ENEMY_SCOPE);
        if (scope != null && !scope.isBlank()) {
            String s = scope.trim().toLowerCase(Locale.ROOT);
            if (!AuraParams.ENEMY_TRACKED.equals(s) && !"all_living".equals(s)) {
                return Optional.of("invalid enemy_scope: " + s);
            }
        }
    }

    if (!weaponPath && triggerOrNull == null) {
        return Optional.of("fireTrigger required");
    }

    if (triggerOrNull != null) {
        if (BuffAreaTargets.TARGET_HIT_TARGET.equals(t)) {
            if (triggerOrNull == Trigger.ON_DAMAGE_TAKEN) {
                return Optional.of("targets=hit_target incompatible with ON_DAMAGE_TAKEN");
            }
            if (triggerOrNull == Trigger.ON_KILL) {
                return Optional.of("targets=hit_target incompatible with ON_KILL");
            }
        }
        if (BuffAreaTargets.TARGET_ATTACKER.equals(t)) {
            if (triggerOrNull == Trigger.ON_KILL) {
                return Optional.of("targets=attacker incompatible with ON_KILL");
            }
            if (triggerOrNull == Trigger.ON_DAMAGE_DEALT) {
                return Optional.of("targets=attacker incompatible with ON_DAMAGE_DEALT");
            }
        }
    }

    return Optional.empty();
}
```

Ensure imports: `java.util.Locale`, `java.util.Optional`, `java.util.Set`. Prefer `AuraParams.ENEMY_ALL_LIVING` if that constant exists in-tree.

- [ ] **Step 4: Re-run tests — expect PASS**

```powershell
& "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" `
  -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" `
  -Dtest=MagicEffectParamsDisableAiTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/Effect.java \
  src/main/java/io/mczju/maggoteers/effect/MagicEffectParams.java \
  src/main/java/io/mczju/maggoteers/effect/BuffAreaTargets.java \
  src/test/java/io/mczju/maggoteers/effect/MagicEffectParamsDisableAiTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(effect): add DISABLE_AI enum and load validation"
```

---

### Task 2: MobAiLockRegistry

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/MobAiLockRegistry.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/MobAiLockRegistryTest.java`

**Public API:** `lock(LivingEntity, durationTicks)`, `tick()`, `onEntityRemove(Entity)`, `restoreAll()`, `start(Plugin)`, `stop()`

**Package/test helpers:** `noteLock`, `pollExpired`, `takeForUnloadRestore`, `getForTests`, `clearForTests`, `Entry` record

- [ ] **Step 1: Write failing tests**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure bookkeeping tests (no Bukkit entity). Exercise noteLock / pollExpired /
 * takeForUnloadRestore; lock()/setAI covered at integration time.
 */
class MobAiLockRegistryTest {

    @AfterEach
    void tearDown() {
        MobAiLockRegistry.clearForTests();
    }

    @Test
    void firstLockStoresRestoreAndExpire() {
        UUID id = UUID.randomUUID();
        int now = 1000;
        MobAiLockRegistry.noteLock(id, now, 40, true);
        Optional<MobAiLockRegistry.Entry> e = MobAiLockRegistry.getForTests(id);
        assertTrue(e.isPresent());
        assertEquals(1040, e.get().expireAtTick());
        assertTrue(e.get().restoreAi());
    }

    @Test
    void longerWinsExtendsExpireKeepsRestore() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, true);   // expire 1040
        MobAiLockRegistry.noteLock(id, 1010, 50, false);  // would expire 1060 — wins
        Optional<MobAiLockRegistry.Entry> e = MobAiLockRegistry.getForTests(id);
        assertEquals(1060, e.get().expireAtTick());
        assertTrue(e.get().restoreAi()); // first restore kept
    }

    @Test
    void shorterReapplyIgnored() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 100, true); // expire 1100
        MobAiLockRegistry.noteLock(id, 1050, 10, false); // expire 1060 < 1100
        assertEquals(1100, MobAiLockRegistry.getForTests(id).get().expireAtTick());
    }

    @Test
    void pollExpiredReturnsEntryAndRemoves() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, true);
        assertTrue(MobAiLockRegistry.pollExpired(id, 1039).isEmpty());
        Optional<MobAiLockRegistry.Entry> expired = MobAiLockRegistry.pollExpired(id, 1040);
        assertTrue(expired.isPresent());
        assertTrue(expired.get().restoreAi());
        assertTrue(MobAiLockRegistry.getForTests(id).isEmpty());
    }

    @Test
    void unloadValidTakesRestoreValue() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, false);
        Optional<Boolean> restore = MobAiLockRegistry.takeForUnloadRestore(id, true);
        assertTrue(restore.isPresent());
        assertFalse(restore.get());
        assertTrue(MobAiLockRegistry.getForTests(id).isEmpty());
    }

    @Test
    void unloadInvalidDropsWithoutRestoreValue() {
        UUID id = UUID.randomUUID();
        MobAiLockRegistry.noteLock(id, 1000, 40, true);
        Optional<Boolean> restore = MobAiLockRegistry.takeForUnloadRestore(id, false);
        assertTrue(restore.isEmpty()); // no restore signal — entity already gone
        assertTrue(MobAiLockRegistry.getForTests(id).isEmpty());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```powershell
& "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" `
  -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" `
  -Dtest=MobAiLockRegistryTest test
```

- [ ] **Step 3: Implement full registry**

```java
package io.mczju.maggoteers.effect;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks temporary setAI(false) locks on hostile mobs.
 * Longer remaining duration wins; first restoreAi is kept.
 * Unload path restores AI while entity is still valid before drop.
 */
public final class MobAiLockRegistry {

    public record Entry(int expireAtTick, boolean restoreAi) {}

    private static final Map<UUID, Entry> LOCKS = new ConcurrentHashMap<>();
    private static BukkitTask scanTask;

    private MobAiLockRegistry() {}

    public static void lock(LivingEntity entity, int durationTicks) {
        if (entity == null || !entity.isValid() || entity.isDead() || durationTicks <= 0) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        UUID id = entity.getUniqueId();
        Entry existing = LOCKS.get(id);
        if (existing == null) {
            boolean restore = entity.hasAI();
            entity.setAI(false);
            LOCKS.put(id, new Entry(now + durationTicks, restore));
        } else {
            int newExpire = now + durationTicks;
            if (newExpire > existing.expireAtTick()) {
                LOCKS.put(id, new Entry(newExpire, existing.restoreAi()));
            }
            // already locked — ensure AI stays off
            entity.setAI(false);
        }
    }

    /** Package/test: record lock without touching Bukkit entity. */
    static void noteLock(UUID id, int nowTick, int durationTicks, boolean restoreAi) {
        if (id == null || durationTicks <= 0) return;
        Entry existing = LOCKS.get(id);
        int newExpire = nowTick + durationTicks;
        if (existing == null) {
            LOCKS.put(id, new Entry(newExpire, restoreAi));
        } else if (newExpire > existing.expireAtTick()) {
            LOCKS.put(id, new Entry(newExpire, existing.restoreAi()));
        }
    }

    /** Package/test: if expired, remove and return entry. */
    static Optional<Entry> pollExpired(UUID id, int nowTick) {
        Entry e = LOCKS.get(id);
        if (e == null) return Optional.empty();
        if (nowTick < e.expireAtTick()) return Optional.empty();
        LOCKS.remove(id);
        return Optional.of(e);
    }

    /**
     * Unload/remove bookkeeping.
     * @param entityStillValid if true and entry exists → return restoreAi for caller to apply
     *                         if false → drop silently (no restore value)
     */
    static Optional<Boolean> takeForUnloadRestore(UUID id, boolean entityStillValid) {
        Entry e = LOCKS.remove(id);
        if (e == null) return Optional.empty();
        if (!entityStillValid) return Optional.empty();
        return Optional.of(e.restoreAi());
    }

    static Optional<Entry> getForTests(UUID id) {
        return Optional.ofNullable(LOCKS.get(id));
    }

    static void clearForTests() {
        LOCKS.clear();
        stop();
    }

    public static void tick() {
        int now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Entry>> it = LOCKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> mapEntry = it.next();
            UUID id = mapEntry.getKey();
            Entry e = mapEntry.getValue();
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                it.remove();
                continue;
            }
            if (now >= e.expireAtTick()) {
                le.setAI(e.restoreAi());
                it.remove();
            }
        }
    }

    public static void onEntityRemove(Entity entity) {
        if (entity == null) return;
        UUID id = entity.getUniqueId();
        if (!LOCKS.containsKey(id)) return;
        boolean stillValid = entity.isValid();
        Optional<Boolean> restore = takeForUnloadRestore(id, stillValid);
        if (restore.isPresent() && entity instanceof LivingEntity le && le.isValid()) {
            le.setAI(restore.get());
        }
    }

    public static void restoreAll() {
        for (Map.Entry<UUID, Entry> mapEntry : LOCKS.entrySet()) {
            Entity entity = Bukkit.getEntity(mapEntry.getKey());
            if (entity instanceof LivingEntity le && le.isValid()) {
                le.setAI(mapEntry.getValue().restoreAi());
            }
        }
        LOCKS.clear();
    }

    public static void start(Plugin plugin) {
        stop();
        if (plugin == null) return;
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, MobAiLockRegistry::tick, 1L, 1L);
    }

    public static void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }
}
```

Notes:
- `Bukkit.getCurrentTick()` is Paper API — use it; do not wall-clock millis for expire.
- Tests never call `lock()` (needs live entity); they use `noteLock` / `pollExpired` / `takeForUnloadRestore`.

- [ ] **Step 4: Re-run tests — PASS**

```powershell
& "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" `
  -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" `
  -Dtest=MobAiLockRegistryTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/MobAiLockRegistry.java \
  src/test/java/io/mczju/maggoteers/effect/MobAiLockRegistryTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(effect): add MobAiLockRegistry with longer-wins and unload restore"
```

---

### Task 3: DisableAiTargets

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/DisableAiTargets.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/DisableAiTargetsTest.java`
- Modify if needed: `BuffAreaTargets.TARGET_ATTACKER` (Task 1)

- [ ] **Step 1: Failing tests**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisableAiTargetsTest {

    @Test
    void emptyHitTargetWithoutContext() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_HIT_TARGET);
        List<?> out = DisableAiTargets.resolve(null, null, p, null);
        assertTrue(out.isEmpty());
    }

    @Test
    void emptyAttackerWithoutContext() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_ATTACKER);
        assertTrue(DisableAiTargets.resolve(null, null, p, null).isEmpty());
    }

    @Test
    void unknownKeyEmpty() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.DURATION_TICKS, 40);
        p.put(EffectKeys.TARGETS, "self");
        assertTrue(DisableAiTargets.resolveTargetKey("self", null, null, p, null).isEmpty());
    }

    @Test
    void passesHostileFilterNullIsFalse() {
        assertFalse(DisableAiTargets.passesHostileFilter(null, null));
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement**

```java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.game.AllyTargeting;
import io.mczju.maggoteers.game.MaggoteersGame;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves DISABLE_AI targets + hostile filter (spec §5.1 / §5.3). */
public final class DisableAiTargets {

    private DisableAiTargets() {}

    public static List<LivingEntity> resolve(MaggoteersGame game, Player caster,
                                             EffectContext params, TriggerContext ctx) {
        EffectContext p = MagicEffectParams.withDefaults(Effect.DISABLE_AI, params);
        String t = p.getOrDefault(EffectKeys.TARGETS, AuraParams.TARGET_ENEMIES)
                .toLowerCase(Locale.ROOT);
        List<LivingEntity> raw = resolveTargetKey(t, game, caster, p, ctx);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : raw) {
            if (passesHostileFilter(le, game)) {
                out.add(le);
            }
        }
        return out;
    }

    static List<LivingEntity> resolveTargetKey(String targetsKey, MaggoteersGame game, Player caster,
                                               EffectContext params, TriggerContext ctx) {
        if (targetsKey == null || targetsKey.isBlank()) {
            return List.of();
        }
        return switch (targetsKey.toLowerCase(Locale.ROOT)) {
            case BuffAreaTargets.TARGET_HIT_TARGET -> ctx != null && ctx.hitTarget() != null
                    ? List.of(ctx.hitTarget()) : List.of();
            case BuffAreaTargets.TARGET_ATTACKER -> ctx != null && ctx.attacker() != null
                    ? List.of(ctx.attacker()) : List.of();
            case AuraParams.TARGET_ENEMIES -> {
                if (caster == null) {
                    yield List.of();
                }
                double r = params.getOrDefault(EffectKeys.RADIUS, 5.0);
                Location center = TriggerContext.resolveOrigin(caster, ctx);
                if (center == null) {
                    yield List.of();
                }
                yield TargetResolver.collect(game, caster, center, r, Effect.DISABLE_AI, params);
            }
            default -> List.of();
        };
    }

    /**
     * Skip null / dead / invalid / Player / ally participants / tracked summons.
     */
    public static boolean passesHostileFilter(LivingEntity le, MaggoteersGame game) {
        if (le == null || !le.isValid() || le.isDead()) {
            return false;
        }
        if (le instanceof Player) {
            return false;
        }
        if (SummonRegistry.isTrackedSummon(le)) {
            return false;
        }
        // If AllyTargeting.isAllyLiving does not exist, add a thin helper matching
        // damage-taken ally skip rules. Do not skip the summon check.
        if (game != null && AllyTargeting.isAllyLiving(game, le)) {
            return false;
        }
        return true;
    }
}
```

Optionally extend `BuffAreaTargets.resolveTargetKey` with `attacker` for reuse — `DisableAiTargets` may call into that for `hit_target`/`attacker` singletons, but **enemies** must use `Effect.DISABLE_AI` in `TargetResolver.collect`.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/DisableAiTargets.java \
  src/test/java/io/mczju/maggoteers/effect/DisableAiTargetsTest.java \
  src/main/java/io/mczju/maggoteers/game/AllyTargeting.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(effect): resolve DISABLE_AI targets with hostile filter"
```

---

### Task 4: EffectService wiring

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`

- [ ] **Step 1: Join executeEffect case group**

Change:

```java
case DAMAGE_AREA, DAMAGE_BEAM, HEAL_AREA, BUFF_AREA ->
    executeMagicEffect(p, game, e.effect(), e.params(), null, ctx);
```

To:

```java
case DAMAGE_AREA, DAMAGE_BEAM, HEAL_AREA, BUFF_AREA, DISABLE_AI ->
    executeMagicEffect(p, game, e.effect(), e.params(), null, ctx);
```

- [ ] **Step 2: Add executeMagicEffect case + executeDisableAi**

Inside `executeMagicEffect` switch (alongside `BUFF_AREA`):

```java
case DISABLE_AI -> {
    executeDisableAi(p, game, params, weaponFx, useCtx);
    return true;
}
```

Implement (mirror BUFF_AREA FX/mark):

```java
private static void executeDisableAi(Player p, MaggoteersGame game, EffectContext params,
                                     MagicUseFx weaponFx, TriggerContext ctx) {
    boolean weaponPath = weaponFx != null;
    if (!weaponPath) {
        EffectContext fxCtx = params.get(EffectKeys.FX);
        if (fxCtx != null) {
            MagicUseFx fx = MagicFxBuilder.fromContext(fxCtx, MagicUseFx.defaults());
            MagicFxPresets.play(p, fx);
        }
    }

    int duration = params.getOrDefault(EffectKeys.DURATION_TICKS, 0);
    if (duration <= 0) return;

    List<LivingEntity> targets = DisableAiTargets.resolve(game, p, params, ctx);
    EffectContext markFxCtx = params.get(EffectKeys.MARK_FX);
    MagicUseFx markFx = markFxCtx == null ? null
            : MagicFxBuilder.fromContext(markFxCtx, MagicUseFx.defaults());

    for (LivingEntity le : targets) {
        MobAiLockRegistry.lock(le, duration);
        if (weaponPath && markFx != null) {
            playMarkFx(le, markFx);
        }
    }
    if (!weaponPath && markFx != null) {
        LivingEntity markTarget = resolveStatMarkTarget(params, ctx);
        if (markTarget != null) {
            playMarkFx(markTarget, markFx);
        }
    }
}
```

Reuse existing `resolveStatMarkTarget` / `playMarkFx`. Weapon caster FX stays on `use_ability.fx` path (ConfigMagicHandler / executeAbility) — do **not** double-play inside this method when `weaponFx != null`.

`executeAbility` already routes through `executeMagicEffect` — **no extra branch**.

Ensure `TargetResolver` accepts `Effect.DISABLE_AI` the same way as `DAMAGE_AREA` / enemy collect (add to any effect whitelist switch if present).

- [ ] **Step 3: Compile**

```powershell
& "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" `
  -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" `
  -DskipTests compile
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectService.java
# + TargetResolver.java if whitelist touched
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(effect): wire DISABLE_AI through executeEffect and executeMagicEffect"
```

---

### Task 5: ItemAbilityRegistry

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/ItemAbilityRegistry.java`

- [ ] **Step 1: Weapon DISABLE_AI block** (mirror BUFF_AREA, after BUFF_AREA block)

```java
if (effect == Effect.DISABLE_AI) {
    if (params.has(EffectKeys.MARK_ON)) {
        LOG.warning("items: " + itemId + " mark_on ignored on weapons");
    }
    String targets = params.get(EffectKeys.TARGETS);
    if (targets != null) {
        String t = targets.trim().toLowerCase(java.util.Locale.ROOT);
        if (BuffAreaTargets.TARGET_HIT_TARGET.equals(t)
                || BuffAreaTargets.TARGET_ATTACKER.equals(t)) {
            LOG.warning("items: " + itemId + " targets=" + t
                    + " ineffective on weapon right-click");
        }
    }
    Optional<String> err = MagicEffectParams.validateDisableAi(params, null, true);
    if (err.isPresent()) {
        LOG.warning("items: " + itemId + " DISABLE_AI " + err.get() + " — skipped");
        return;
    }
}
```

Confirm `Effect.DISABLE_AI` is already in the allowed `use_ability` effect set / switch that parses abilities (extend any allow-list enum switch that previously omitted it).

- [ ] **Step 2: Compile / targeted tests**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/ItemAbilityRegistry.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(items): load-validate weapon use_ability DISABLE_AI"
```

---

### Task 6: RewardLoadValidator

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardLoadValidator.java`
- Create/modify reward tests for STAT DISABLE_AI skip cases

- [ ] **Step 1: Branch in validateStatOption**

After BUFF_AREA branch:

```java
if (opt.effect() == Effect.DISABLE_AI) {
    return validateAndNormalizeDisableAi(opt).skipReason();
}
```

- [ ] **Step 2: validateAndNormalizeDisableAi** (mirror BUFF_AREA mark_on coerce)

```java
/** Validates DISABLE_AI and returns normalized params (mark_on coercion). */
public static ValidationResult validateAndNormalizeDisableAi(RewardOption opt) {
    if (opt == null || opt.effect() != Effect.DISABLE_AI) {
        return new ValidationResult(Optional.empty(), opt == null ? null : opt.params());
    }
    EffectContext params = opt.params() == null ? new EffectContext() : opt.params().copy();
    params = MagicEffectParams.withDefaults(Effect.DISABLE_AI, params);

    Optional<String> general = MagicEffectParams.validateDisableAi(params, opt.trigger(), false);
    if (general.isPresent()) {
        return new ValidationResult(general, params);
    }

    Trigger fire = opt.trigger();
    // mark_on coerce (same rules as BUFF_AREA / spec §6.2)
    String markOn = params.get(EffectKeys.MARK_ON);
    if (markOn != null && !markOn.isBlank()) {
        String m = markOn.trim().toLowerCase(java.util.Locale.ROOT);
        if ("attacker".equals(m) && fire != Trigger.ON_DAMAGE_TAKEN) {
            LOG.warning("rewards.yml option " + opt.id()
                    + " mark_on=attacker coerced to none (needs ON_DAMAGE_TAKEN)");
            params.put(EffectKeys.MARK_ON, "none");
        } else if ("hit_target".equals(m) && fire == Trigger.ON_DAMAGE_TAKEN) {
            LOG.warning("rewards.yml option " + opt.id()
                    + " mark_on=hit_target coerced to none on ON_DAMAGE_TAKEN");
            params.put(EffectKeys.MARK_ON, "none");
        } else if (!java.util.Set.of("none", "hit_target", "attacker").contains(m)) {
            LOG.warning("rewards.yml option " + opt.id()
                    + " invalid mark_on=" + m + " → none");
            params.put(EffectKeys.MARK_ON, "none");
        }
    }

    return new ValidationResult(Optional.empty(), params);
}
```

**Mirror normalized-params application call sites** — wherever BUFF_AREA’s `ValidationResult` replaces `opt.params()` with normalized context, do the same for DISABLE_AI (search `validateAndNormalizeBuffArea` usages and dual-path).

- [ ] **Step 3: Tests** — `ON_KILL` + `hit_target` skipped; `attacker` + `ON_DAMAGE_DEALT` skipped; happy path `ON_DAMAGE_DEALT` + `hit_target` accepted.

Example assertion shape (adapt to existing RewardLoadValidationTest helpers):

```java
@Test
void disableAiOnKillHitTargetSkipped() {
    // build RewardOption id=x category=STAT trigger=ON_KILL effect=DISABLE_AI
    // params duration_ticks=40 targets=hit_target
    // assert validateStat / load skip reason present
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/reward/RewardLoadValidator.java \
  src/test/java/io/mczju/maggoteers/reward/
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(rewards): validate STAT DISABLE_AI target/trigger matrix"
```

---

### Task 7: Lifecycle (scan, unload listener, cleanup)

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/MobAiLockListener.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java`

- [ ] **Step 1: Listener**

```java
package io.mczju.maggoteers.effect;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

/** Restores AI before chunk unload / remove drops registry entry. */
public final class MobAiLockListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemove(EntityRemoveEvent event) {
        MobAiLockRegistry.onEntityRemove(event.getEntity());
    }
}
```

**Note:** If `EntityRemoveEvent` API differs on Paper 26.2, use the equivalent that fires on unload with an accessible entity — **do not omit unload restore**. Alternatives if needed: `EntityRemoveFromWorldEvent` (Paper) with same MONITOR + `onEntityRemove`.

- [ ] **Step 2: MaggoteersPlugin**

In `onEnable` (near other effect listeners):

```java
getServer().getPluginManager().registerEvents(new MobAiLockListener(), this);
MobAiLockRegistry.start(this);
```

In `onDisable` (before/after other cleanup):

```java
MobAiLockRegistry.restoreAll();
MobAiLockRegistry.stop();
```

- [ ] **Step 3: MaggoteersGame cleanup**

Near `SummonRegistry.clearAll(this);`:

```java
io.mczju.maggoteers.effect.MobAiLockRegistry.restoreAll();
```

(If locks are global rather than per-game, `restoreAll` is correct for single concurrent game; if multi-game later, scope by world — Phase 1 = restoreAll is fine.)

- [ ] **Step 4: Full test suite**

```powershell
& "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" `
  -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" test
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/MobAiLockListener.java \
  src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java \
  src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(effect): DISABLE_AI tick scan, unload listener, and cleanup restore"
```

---

### Task 8: Docs + optional sample

**Files:**
- `CLAUDE.md` — §10.2 Effect catalog add `DISABLE_AI`; §15 weapon `use_ability` example note
- `.cursor/skills/maggoteers-plugin-config/reference.md` — if effect list present
- `docs/dev-log.md` — entry `2026-08-08` DISABLE_AI Phase 1
- Optional: `src/main/resources/items/maggoteers.yml` stun staff (`use_ability` DISABLE_AI) — **UTF-8**
- Spec status → update when code lands (plan phase already “Ready for implementation”)

- [ ] **Step 1: Doc edits (UTF-8 via Python write if needed)**

CLAUDE.md §10.2 add to Effect directory line:

`DISABLE_AI` (`duration_ticks` + `targets` enemies|hit_target|attacker; Phase 1: `use_ability` + STAT)

§15 sample:

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

dev-log entry: Phase 1 shipped — MobAiLockRegistry longer-wins + unload restore; held deferred Phase 2.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md docs/dev-log.md \
  .cursor/skills/maggoteers-plugin-config/reference.md \
  src/main/resources/items/maggoteers.yml \
  docs/superpowers/specs/2026-08-08-disable-ai-effect-design.md
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "docs: catalog DISABLE_AI Phase 1"
```

---

## Phase 2 (deferred)

Wire `held_effects` DISABLE_AI in `WeaponEffectValidator` **after** weapon-held-effects Path B merges. Same `targets`↔trigger matrix as STAT. Forbidden as permanent held (no trigger). Do not start Phase 2 in this plan’s commits.

---

## Spec coverage table

| Spec v1.1 requirement | Task |
|----------------------|------|
| `Effect.DISABLE_AI` enum | Task 1 |
| `withDefaults` enemies/tracked/radius 5 | Task 1 |
| `validateDisableAi` duration/targets/scope/trigger matrix | Task 1 |
| `ON_KILL`+`hit_target` warn+skip | Task 1, 6 |
| `attacker` target key | Task 1, 3 |
| `MobAiLockRegistry` lock / longer-wins / restoreAi | Task 2 |
| 1-tick expire scan | Task 2, 7 |
| Unload restore-before-drop | Task 2, 7 |
| Hostile filter (Player / ally / summon) | Task 3 |
| `DisableAiTargets` enemies\|hit_target\|attacker | Task 3 |
| `executeEffect` joins DAMAGE_AREA/BUFF_AREA case | Task 4 |
| `executeMagicEffect` DISABLE_AI body + FX/mark | Task 4 |
| Weapon `use_ability` load validation | Task 5 |
| STAT `RewardLoadValidator` + mark_on coerce | Task 6 |
| Plugin start/stop + game cleanup restoreAll | Task 7 |
| Docs / optional stun sample UTF-8 | Task 8 |
| `held_effects` / WeaponEffectValidator | **Phase 2** |
| Damage cancel / velocity pin | Out of scope |

---

## Manual test checklist (post-implementation)

- [ ] Right-click stun staff: nearby tracked hostiles freeze (`NoAI`), resume after `duration_ticks`
- [ ] Re-apply longer stun while shorter active → total window matches longer end
- [ ] Chunk unload while stunned → mob not permanently NoAI on reload
- [ ] STAT on-hit (`hit_target`) stuns victim only; does not stun players/wolves
- [ ] STAT thorns (`attacker`) stuns melee attacker
- [ ] Load `ON_KILL`+`hit_target` STAT → warn+skip
- [ ] Game end / plugin disable restores AI on living locked mobs

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-08-disable-ai-effect.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks
2. **Inline Execution** — implement task-by-task in this session with checkpoints

Which approach?
