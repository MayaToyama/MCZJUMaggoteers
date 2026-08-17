# Affix Trigger Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor affix `on` triggers so `hit` buffs the mob on any damage, `hit-by-player` debuffs the attacking player, and parsing/dispatch is centralized via `AffixTrigger` + `AffixCombatService`.

**Architecture:** Replace raw `String on` with `AffixTrigger` enum parsed at load time (fail-fast). `AffixCombatService` applies potions to player or mob by trigger. `CombatAffixListener` routes `EntityDamageByEntityEvent` (hit-player, hit-by-player) and `EntityDamageEvent` (hit). `MobFactory` / `RunPlanner` use `trigger.isCombatTriggered()` to exclude combat potions from spawn.

**Tech Stack:** Java 25, Paper 26.2 API, JUnit 5, Maven (IntelliJ bundled mvn + MC runtime JDK 25)

## Global Constraints

- Affix definitions live only in `affixes.yml` (iron rule 2).
- Combat-trigger potions must NOT be applied at spawn or merged into RunPlanner `step.potions`.
- Tracked mobs only (`WaveEngine.isTracked` / `resolveCombatAffixes`).
- Player buffs only when in `MaggoteersGame` (`PlayerExt.isInGame()`).
- Use `addPotionEffect(..., true)` for forced overwrite.
- `hit` listens on `EntityDamageEvent` for any damage source (fall, fire, entity, etc.).
- Config migration: `poisonous`/`sticky` -> `hit-by-player`; `hidesuwa`/`teleport`/`cataclysm` stay `hit`.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `config/AffixTrigger.java` | Create | Enum + parse + isCombatTriggered |
| `config/Affix.java` | Modify | `String on` -> `AffixTrigger trigger` |
| `config/AffixService.java` | Modify | Parse trigger; fail-fast on unknown `on` |
| `effect/AffixCombatService.java` | Create | applyToPlayer / applyToMob |
| `listener/CombatAffixListener.java` | Modify | Two-event router |
| `mob/MobFactory.java` | Modify | Use `AffixTrigger` instead of string checks |
| `plan/RunPlanner.java` | Modify | Use `AffixTrigger.isCombatTriggered()` |
| `src/main/resources/affixes.yml` | Modify | Migrate `on` values + header comment |
| `test/.../AffixTriggerTest.java` | Create | Enum parse tests |
| `test/.../AffixCombatServiceTest.java` | Create | Trigger filter tests |
| `test/.../ComposeTest.java` etc. | Modify | `AffixTrigger.SPAWN` instead of `null` |
| `.cursor/skills/maggoteers-plugin-config/SKILL.md` | Modify | Document three combat triggers |

---

### Task 1: AffixTrigger enum + Affix record

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/config/AffixTrigger.java`
- Create: `src/test/java/io/mczju/maggoteers/config/AffixTriggerTest.java`
- Modify: `src/main/java/io/mczju/maggoteers/config/Affix.java`

**Interfaces:**
- Produces: `AffixTrigger.parse(String)`, `AffixTrigger.isCombatTriggered()`, `AffixTrigger.yamlKey()`
- Produces: `Affix` record field `AffixTrigger trigger()` (replaces `on()`)

- [ ] **Step 1: Write failing test**

```java
package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AffixTriggerTest {
    @Test
    void parseKnown() {
        assertEquals(AffixTrigger.SPAWN, AffixTrigger.parse(null));
        assertEquals(AffixTrigger.SPAWN, AffixTrigger.parse(""));
        assertEquals(AffixTrigger.HIT_PLAYER, AffixTrigger.parse("hit-player"));
        assertEquals(AffixTrigger.HIT_BY_PLAYER, AffixTrigger.parse("hit-by-player"));
        assertEquals(AffixTrigger.HIT, AffixTrigger.parse("hit"));
    }

    @Test
    void parseUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> AffixTrigger.parse("hit-mob"));
    }

    @Test
    void combatTriggered() {
        assertFalse(AffixTrigger.SPAWN.isCombatTriggered());
        assertTrue(AffixTrigger.HIT.isCombatTriggered());
        assertTrue(AffixTrigger.HIT_BY_PLAYER.isCombatTriggered());
        assertTrue(AffixTrigger.HIT_PLAYER.isCombatTriggered());
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `$env:JAVA_HOME="C:\Users\Chalcanthite\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon"; & "E:\Intellij_Idea\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" -f "E:\Intellij_Idea\plugins\MCPlugin\pom.xml" test -Dtest=AffixTriggerTest`

Expected: FAIL — class not found

- [ ] **Step 3: Implement AffixTrigger**

```java
package io.mczju.maggoteers.config;

import org.jetbrains.annotations.Nullable;

public enum AffixTrigger {
    SPAWN(null),
    HIT_PLAYER("hit-player"),
    HIT_BY_PLAYER("hit-by-player"),
    HIT("hit");

    private final String yamlKey;

    AffixTrigger(String yamlKey) { this.yamlKey = yamlKey; }

    public static AffixTrigger parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return SPAWN;
        for (AffixTrigger t : values()) {
            if (t != SPAWN && t.yamlKey.equals(raw.trim())) return t;
        }
        throw new IllegalArgumentException("Unknown affix on: " + raw);
    }

    public boolean isCombatTriggered() { return this != SPAWN; }

    public @Nullable String yamlKey() { return yamlKey; }
}
```

- [ ] **Step 4: Update Affix record**

Change last `String on` parameter to `AffixTrigger trigger` in both constructors. Add accessor `trigger()` via record.

Update compact constructor — no change needed for trigger null (use SPAWN only via parse).

- [ ] **Step 5: Run test — expect PASS**

Same mvn command as Step 2.

Expected: PASS

---

### Task 2: AffixService load-time parsing

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/config/AffixService.java`
- Modify: `src/test/java/io/mczju/maggoteers/plan/ComposeTest.java`
- Modify: `src/test/java/io/mczju/maggoteers/plan/RunPlannerTest.java`
- Modify: `src/test/java/io/mczju/maggoteers/plan/StrongWaveRollTest.java`

**Interfaces:**
- Consumes: `AffixTrigger.parse(String)`
- Produces: `Affix` instances with `AffixTrigger trigger` field

- [ ] **Step 1: Update AffixService.parse**

Replace:
```java
pots, s.getString("on"), s.getString("display", id));
```
With:
```java
pots, AffixTrigger.parse(s.getString("on")), s.getString("display", id));
```

And null section default:
```java
return new Affix(id, 1, 1, 1, 1, List.of(), AffixTrigger.SPAWN, id);
```

Wrap parse in try/catch and rethrow as:
```java
throw new IllegalStateException("词缀 " + id + " 未知 on: " + s.getString("on"), e);
```

- [ ] **Step 2: Fix test Affix constructors**

Replace all `new Affix(..., null, ...)` with `AffixTrigger.SPAWN`:
- `ComposeTest.java` lines 27-28, 37
- `RunPlannerTest.java` line 64
- `StrongWaveRollTest.java` line 120

- [ ] **Step 3: Run full test suite**

Run: `build-and-deploy.ps1 -SkipDeploy` (or `mvn test` with JDK 25)

Expected: PASS (compile + all tests)

---

### Task 3: AffixCombatService

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/AffixCombatService.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/AffixCombatServiceTest.java`

**Interfaces:**
- Consumes: `AffixService.getInstance()`, `Affix.trigger()`, `GameRegistries.potionEffect`
- Produces:
  - `void applyToPlayer(List<String> affixIds, AffixTrigger trigger, Player player)`
  - `void applyToMob(List<String> affixIds, AffixTrigger trigger, LivingEntity mob)`

- [ ] **Step 1: Write failing test**

```java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.wave.PotionSpec;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AffixCombatServiceTest {
    @BeforeEach
    void setup() {
        Affix toxic = new Affix("toxic", 1, 1, 1, 1,
                List.of(new PotionSpec("POISON", 1, 20)),
                AffixTrigger.HIT_PLAYER, "t");
        Affix poisonous = new Affix("poisonous", 1, 1, 1, 1,
                List.of(new PotionSpec("POISON", 1, 20)),
                AffixTrigger.HIT_BY_PLAYER, "p");
        Affix hide = new Affix("hidesuwa", 1, 1, 1, 1,
                List.of(new PotionSpec("INVISIBILITY", 0, 100)),
                AffixTrigger.HIT, "h");
        AffixService.forTesting(Map.of(
                "toxic", toxic, "poisonous", poisonous, "hidesuwa", hide));
    }

    @Test
    void filtersByTrigger() {
        assertEquals(1, AffixCombatService.matchingAffixes(
                List.of("toxic", "poisonous"), AffixTrigger.HIT_PLAYER).size());
        assertEquals(1, AffixCombatService.matchingAffixes(
                List.of("toxic", "poisonous"), AffixTrigger.HIT_BY_PLAYER).size());
        assertEquals(1, AffixCombatService.matchingAffixes(
                List.of("hidesuwa"), AffixTrigger.HIT).size());
        assertTrue(AffixCombatService.matchingAffixes(
                List.of("toxic"), AffixTrigger.HIT_BY_PLAYER).isEmpty());
    }
}
```

Expose package-private or public `matchingAffixes` for testability (returns filtered affix list).

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn test -Dtest=AffixCombatServiceTest`

- [ ] **Step 3: Implement AffixCombatService**

```java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.config.*;
import io.mczju.maggoteers.util.GameRegistries;
import io.mczju.maggoteers.wave.PotionSpec;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public final class AffixCombatService {
    public static void applyToPlayer(List<String> affixIds, AffixTrigger trigger, Player player) {
        applyPotions(matchingAffixes(affixIds, trigger), player);
    }

    public static void applyToMob(List<String> affixIds, AffixTrigger trigger, LivingEntity mob) {
        applyPotions(matchingAffixes(affixIds, trigger), mob);
    }

    static List<Affix> matchingAffixes(List<String> affixIds, AffixTrigger trigger) {
        if (affixIds == null || affixIds.isEmpty()) return List.of();
        var svc = AffixService.getInstance();
        List<Affix> out = new ArrayList<>();
        for (String id : affixIds) {
            Affix a = svc.get(id);
            if (a != null && a.trigger() == trigger) out.add(a);
        }
        return out;
    }

    private static void applyPotions(List<Affix> affixes, LivingEntity target) {
        for (Affix a : affixes) {
            for (PotionSpec ps : a.potions()) {
                PotionEffectType type = GameRegistries.potionEffect(ps.effect());
                if (type != null) {
                    target.addPotionEffect(new PotionEffect(type, ps.dur(), ps.amp()), true);
                }
            }
        }
    }

    private AffixCombatService() {}
}
```

- [ ] **Step 4: Run test — expect PASS**

---

### Task 4: CombatAffixListener refactor

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/listener/CombatAffixListener.java`

**Interfaces:**
- Consumes: `AffixCombatService`, `WaveEngine.resolveCombatAffixes`, `WaveEngine.isTracked`

- [ ] **Step 1: Replace listener implementation**

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
    if (e.getEntity() instanceof Player victim) {
        if (!inMaggoteers(victim)) return;
        AffixCombatService.applyToPlayer(
                WaveEngine.resolveCombatAffixes(e.getDamager()),
                AffixTrigger.HIT_PLAYER, victim);
        return;
    }
    Player attacker = resolvePlayerAttacker(e.getDamager());
    if (attacker == null || !inMaggoteers(attacker)) return;
    if (!(e.getEntity() instanceof LivingEntity mob)) return;
    if (!WaveEngine.isTracked(mob.getUniqueId())) return;
    AffixCombatService.applyToPlayer(
            WaveEngine.resolveCombatAffixes(mob),
            AffixTrigger.HIT_BY_PLAYER, attacker);
}

@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onEntityDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof LivingEntity mob)) return;
    if (!WaveEngine.isTracked(mob.getUniqueId())) return;
    AffixCombatService.applyToMob(
            WaveEngine.resolveCombatAffixes(mob),
            AffixTrigger.HIT, mob);
}
```

Remove old `applyPotions` / `applyHit` private methods. Keep `resolvePlayerAttacker` and `inMaggoteers`.

- [ ] **Step 2: Run mvn test**

Expected: PASS

---

### Task 5: MobFactory + RunPlanner enum migration

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/mob/MobFactory.java`
- Modify: `src/main/java/io/mczju/maggoteers/plan/RunPlanner.java`

- [ ] **Step 1: MobFactory — replace string check**

Change:
```java
if (a == null || isCombatTriggerAffix(a.on())) continue;
```
To:
```java
if (a == null || a.trigger().isCombatTriggered()) continue;
```

Delete `isCombatTriggerAffix(String on)` private method.

- [ ] **Step 2: RunPlanner — replace filter**

Change:
```java
.filter(a -> !isCombatTriggerAffix(a.on()))
```
To:
```java
.filter(a -> !a.trigger().isCombatTriggered())
```

Delete `isCombatTriggerAffix` private method.

- [ ] **Step 3: Run mvn test**

Expected: PASS

---

### Task 6: affixes.yml migration + docs

**Files:**
- Modify: `src/main/resources/affixes.yml`
- Modify: `.cursor/skills/maggoteers-plugin-config/SKILL.md`

- [ ] **Step 1: Add header comment to affixes.yml**

```yaml
# on 触发（战斗型不在刷怪时施加）：
#   hit-player     — 怪打到玩家 → 玩家 buff
#   hit-by-player  — 玩家打到怪 → 玩家 buff（反伤/黏住）
#   hit            — 怪受到任意伤害 → 怪自身 buff
#   (省略 on)      — 刷怪时施加给怪
```

- [ ] **Step 2: Migrate affix entries**

```yaml
poisonous:
  on: hit-by-player
sticky:
  on: hit-by-player
# hidesuwa, teleport, cataclysm keep on: hit
```

Update inline comments for poisonous/sticky.

- [ ] **Step 3: Update plugin-config SKILL.md**

Replace single `hit-player` mention with table of three combat triggers.

- [ ] **Step 4: Append dev-log entry** (one paragraph, date + decision)

File: `docs/dev-log.md`

---

### Task 7: Build, deploy, smoke checklist

**Files:** none (verification only)

- [ ] **Step 1: Run build-and-deploy**

```powershell
$env:JAVA_HOME = "C:\Users\Chalcanthite\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon"
Set-Location "E:\Intellij_Idea\plugins\MCPlugin"
.\.cursor\skills\maggoteers-build-deploy\scripts\build-and-deploy.ps1
```

Expected: 62+ tests PASS, jar copied to `E:\MCpaper\plugins\`

- [ ] **Step 2: Restart Paper and smoke**

| Check | Expected |
|-------|----------|
| Husk + quicksand hits player | Player Slowness II |
| Mob + poisonous, player hits | Player Poison |
| Mob + hidesuwa, any damage | Mob invisible |
| Mob + sticky, player hits | Player Slowness |
| Invalid `on: foo` in affixes.yml | Server fails on enable |

---

## Spec Coverage Check

| Spec requirement | Task |
|------------------|------|
| AffixTrigger enum | Task 1 |
| AffixService fail-fast | Task 2 |
| AffixCombatService | Task 3 |
| hit-player / hit-by-player events | Task 4 |
| hit on EntityDamageEvent | Task 4 |
| MobFactory spawn exclusion | Task 5 |
| RunPlanner potion exclusion | Task 5 |
| affixes.yml migration | Task 6 |
| Unit tests | Tasks 1, 3 |
| Acceptance smoke | Task 7 |

## Self-Review

- [x] No TBD placeholders
- [x] All code blocks complete
- [x] Type names consistent (`AffixTrigger`, `trigger()`, not `on()`)
- [x] Test commands include JDK 25 path
