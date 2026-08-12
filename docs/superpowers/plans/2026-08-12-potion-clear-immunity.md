# Potion Clear & Immunity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fake amp-255 potion "clear/immunity" with real `clear_potions` / `self_clear_potions` (`removePotionEffect`) and run-long `ADD_POTION` + `immunity: true`, with hard-fail for stale deployed YAML.

**Architecture:** Shared `PotionImmunity` queries PlayerState; parsers/validators enforce clear-only BUFF_AREA, fake-255 hard-fail, and non-instant immunity lifecycle. `EffectService` clears then applies potions; DAMAGE_AREA order is damage → clear → potions. Listeners cancel `EntityPotionEffectEvent` ADDED/CHANGED; `PurifyListener` uses immunity flags. Migrate jar YAML and require deploy sync of server copies.

**Tech Stack:** Paper 26.2, Java 25, Maven, JUnit 5 (pure-logic unit tests; no Mockito required for helpers).

**Spec:** `docs/superpowers/specs/2026-08-12-potion-clear-immunity-design.md` (v1.1)

## Global Constraints

- Paper **26.2**; JDK **25**; test command:
  `E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd -f E:/Intellij_Idea/plugins/MCPlugin/pom.xml test`
  (fallback: `E:/Intellij_Idea/IntelliJ IDEA 2025.1.1.1/plugins/maven/lib/maven3/bin/mvn.cmd`)
- Docs/YAML must be saved as **UTF-8** (no UTF-16).
- Fake-255 hard-fail applies to **rewards.yml + items/*.yml only** — never affixes.
- DAMAGE_AREA order is fixed: **damage → clear_potions → potions**.
- Immunity requires: `ADD_POTION`, `immunity: true`, `fireTrigger == null`, `expiryTrigger == null`, `recurringIntervalSec <= 0`, non-instant potion (`PotionEffectType.isInstant() == false`).
- Do not add new Effect enum values.
- Removing `amp >= 255` special-case ships only after hard-fail validators + migrated resources exist.

## File map

| File | Role |
|------|------|
| `effect/EffectKeys.java` | Add `IMMUNITY`, `CLEAR_POTIONS` |
| `effect/ClearPotionsParser.java` | **Create** — parse string list → types + hard-fail errors |
| `effect/Fake255Rules.java` | **Create** — detect deprecated amp/duration pattern |
| `effect/PotionImmunity.java` | **Create** — `isImmune` / `matchesImmunityEffect` / cause helpers |
| `effect/ItemAbility.java` | Add `selfClearPotions` |
| `effect/EffectParamsParser.java` | Parse `clear_potions`, `immunity` |
| `effect/ItemAbilityRegistry.java` | Parse `self_clear_potions`; fake-255 on self_potions/potions |
| `effect/MagicEffectParams.java` | BUFF_AREA: potions **or** clear_potions |
| `effect/EffectService.java` | Clear runtime; immunity apply; remove 255 hack; executeAbility success |
| `effect/PotionMerge.java` | Gate applies with `PotionImmunity` when target is Player |
| `effect/AuraService.java` | Same gate via PotionMerge or explicit check |
| `listener/PurifyListener.java` | Rewrite to immunity flag + EntityPotionEffectEvent (or split listener) |
| `item/interact/ConfigMagicHandler.java` | Charge CD only after successful executeAbility |
| `reward/RewardLoadValidator.java` | Immunity lifecycle + fake-255 hard-fail |
| `reward/RewardOption.java` | Parse `immunity` if params map path needs it |
| `items/maggoteers.yml`, `rewards.yml` | Migrate silence/purify/immunity configs |
| Tests under `src/test/java/io/mczju/maggoteers/effect/` | Helpers + validator + listener action policy |
| `CLAUDE.md`, config/deploy skill refs, `docs/dev-log.md` | Doc + deploy sync |

---

### Task 1: Core helpers (keys, fake-255, clear parse, immunity predicate)

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectKeys.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/Fake255Rules.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/ClearPotionsParser.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/PotionImmunity.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/Fake255RulesTest.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/PotionImmunityTest.java`

**Interfaces:**
- `EffectKeys.IMMUNITY` → `EffectKey<Boolean>`
- `EffectKeys.CLEAR_POTIONS` → `EffectKey<List<PotionEffectType>>`
- `Fake255Rules.isFakeClear(int amp, int durationTicks, boolean immunityTrue)` → `boolean`
- `ClearPotionsParser.parse(Object yamlList, String locationPrefix)` → `ParseResult(List<PotionEffectType> types, List<String> errors)`
- `PotionImmunity.matchesImmunityEffect(PlayerEffect e, PotionEffectType type)` → `boolean`
- `PotionImmunity.isImmune(Player player, PotionEffectType type)` → MaggoteersGame + PlayerState
- `PotionImmunity.isImmuneToCause(Player player, DamageCause cause)` → POISON/WITHER mapping

- [ ] **Step 1: Write failing tests**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Fake255RulesTest {
    @Test void detectsZeroDuration() {
        assertTrue(Fake255Rules.isFakeClear(255, 0, false));
    }
    @Test void detectsOneTick() {
        assertTrue(Fake255Rules.isFakeClear(255, 1, false));
    }
    @Test void immunityExempt() {
        assertFalse(Fake255Rules.isFakeClear(255, 0, true));
    }
    @Test void realLong255NotFake() {
        assertFalse(Fake255Rules.isFakeClear(255, 200, false));
    }
    @Test void normalPotionNotFake() {
        assertFalse(Fake255Rules.isFakeClear(0, 1, false));
    }
}
```

```java
package io.mczju.maggoteers.effect;

import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PotionImmunityTest {
    private static PlayerEffect immunity(String id, PotionEffectType type) {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.POTION, type);
        p.put(EffectKeys.IMMUNITY, true);
        return new PlayerEffect(id, Effect.ADD_POTION, p, null, null, 0, 0, null, Stack.IGNORE, 0, 0);
    }

    @Test void matchesWhenLegal() {
        PotionEffectType type = PotionEffectType.WITHER;
        Assumptions.assumeTrue(type != null && !type.isInstant());
        assertTrue(PotionImmunity.matchesImmunityEffect(immunity("a3w_imm_wither", type), type));
    }

    @Test void rejectsWhenExpiryPresent() {
        PotionEffectType type = PotionEffectType.WITHER;
        Assumptions.assumeTrue(type != null);
        EffectContext p = new EffectContext();
        p.put(EffectKeys.POTION, type);
        p.put(EffectKeys.IMMUNITY, true);
        PlayerEffect e = new PlayerEffect("x", Effect.ADD_POTION, p, null,
                Trigger.ON_WAVE_CLEAR, -1, 0, null, Stack.IGNORE, 0, 0);
        assertFalse(PotionImmunity.matchesImmunityEffect(e, type));
    }

    @Test void rejectsWrongType() {
        PotionEffectType wither = PotionEffectType.WITHER;
        PotionEffectType poison = PotionEffectType.POISON;
        Assumptions.assumeTrue(wither != null && poison != null);
        assertFalse(PotionImmunity.matchesImmunityEffect(immunity("a", wither), poison));
    }
}
```

If registry types are null under plain JUnit, those tests skip via `Assumptions`; keep `Fake255RulesTest` always green.

- [ ] **Step 2: Run tests — expect FAIL (missing classes)**

```
E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd -f E:/Intellij_Idea/plugins/MCPlugin/pom.xml -Dtest=Fake255RulesTest,PotionImmunityTest test
```

- [ ] **Step 3: Implement helpers**

Add to `EffectKeys.java`:

```java
public static final EffectKey<Boolean> IMMUNITY = new EffectKey<>("immunity");
public static final EffectKey<java.util.List<org.bukkit.potion.PotionEffectType>> CLEAR_POTIONS =
        new EffectKey<>("clear_potions");
```

`Fake255Rules.java`:

```java
package io.mczju.maggoteers.effect;

public final class Fake255Rules {
    private Fake255Rules() {}
    public static boolean isFakeClear(int amp, int durationTicks, boolean immunityTrue) {
        if (immunityTrue) return false;
        return amp == 255 && (durationTicks == 0 || durationTicks == 1);
    }
}
```

`ClearPotionsParser`: parse YAML list of strings (or maps with `potion` key). Unknown names → error `"<locationPrefix>: unknown potion '<name>'"`. Never silently drop unknown names.

`PotionImmunity` core:

```java
public static boolean matchesImmunityEffect(PlayerEffect e, PotionEffectType type) {
    if (e == null || type == null) return false;
    if (e.effect() != Effect.ADD_POTION) return false;
    if (!Boolean.TRUE.equals(e.params().get(EffectKeys.IMMUNITY))) return false;
    if (e.fireTrigger() != null) return false;
    if (e.expiryTrigger() != null) return false;
    if (e.recurringIntervalSec() > 0) return false;
    PotionEffectType have = e.params().get(EffectKeys.POTION);
    if (have == null || !have.equals(type)) return false;
    if (type.isInstant()) return false;
    return true;
}
```

Also implement `isImmune(Player, PotionEffectType)` and `isImmuneToCause(Player, DamageCause)`.

- [ ] **Step 4: Re-run tests — expect PASS** (or skipped Assumptions)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectKeys.java \
  src/main/java/io/mczju/maggoteers/effect/Fake255Rules.java \
  src/main/java/io/mczju/maggoteers/effect/ClearPotionsParser.java \
  src/main/java/io/mczju/maggoteers/effect/PotionImmunity.java \
  src/test/java/io/mczju/maggoteers/effect/Fake255RulesTest.java \
  src/test/java/io/mczju/maggoteers/effect/PotionImmunityTest.java
git commit -m "feat(effect): add potion clear/immunity helper types"
```

---

### Task 2: Validation + parse wiring

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/MagicEffectParams.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectParamsParser.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/ItemAbility.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/ItemAbilityRegistry.java`
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardLoadValidator.java`
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardOption.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/MagicEffectParamsClearPotionsTest.java`

**Interfaces:**
- Consumes Task 1
- `validateBuffArea`: error only when both potions and clear_potions empty
- Item/reward load: hard-fail (skip with severe log including id + path) on `Fake255Rules`
- Immunity STAT: reject instant; reject trigger/expiry/recurring

- [ ] **Step 1: Failing tests**

```java
@Test
void clearOnlyBuffAreaAccepted() {
    EffectContext p = new EffectContext();
    p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_SELF);
    Assumptions.assumeTrue(PotionEffectType.WITHER != null);
    p.put(EffectKeys.CLEAR_POTIONS, java.util.List.of(PotionEffectType.WITHER));
    assertTrue(MagicEffectParams.validateBuffArea(p, null, true).isEmpty());
}

@Test
void bothEmptyRejected() {
    EffectContext p = new EffectContext();
    p.put(EffectKeys.TARGETS, BuffAreaTargets.TARGET_SELF);
    assertTrue(MagicEffectParams.validateBuffArea(p, null, true).orElse("").contains("potions"));
}
```

- [ ] **Step 2: Run — expect FAIL on clear-only (`missing potions`)**

- [ ] **Step 3: Implement**

```java
List<BuffPotionSpec> potions = p.get(EffectKeys.POTIONS);
List<PotionEffectType> clears = p.get(EffectKeys.CLEAR_POTIONS);
boolean hasPotions = potions != null && !potions.isEmpty();
boolean hasClear = clears != null && !clears.isEmpty();
if (!hasPotions && !hasClear) {
    return Optional.of("missing potions or clear_potions");
}
```

`EffectParamsParser`: `"clear_potions"` → `ClearPotionsParser.parse`; `"immunity"` → boolean.

`ItemAbility`: add `List<PotionEffectType> selfClearPotions`; constructors default `List.of()`.

`ItemAbilityRegistry`: parse `self_clear_potions`; fake-255 in self_potions/params → skip ability with itemId in message.

`RewardLoadValidator` / `RewardOption`: ADD_POTION immunity lifecycle + non-instant; fake-255 without immunity → fatal skip of option.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(effect): validate clear_potions, immunity, and hard-fail fake-255"
```

---

### Task 3: EffectService runtime + PotionMerge gate

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/PotionMerge.java`
- Optional create: `src/main/java/io/mczju/maggoteers/effect/PotionClear.java`
- Optional create: `src/test/java/io/mczju/maggoteers/effect/AreaPotionOrderTest.java`

**Interfaces:**
- `PotionClear.apply(LivingEntity target, List<PotionEffectType> types)`
- DAMAGE_AREA per target: damage → clear → potions
- BUFF_AREA: no early-return when potions empty if clear non-empty
- Immunity apply/refresh: remove only; **delete amp>=255 branch**
- `executeAbility` success:

```java
boolean didSide = applySelfClear(p, ability.selfClearPotions());
didSide |= applySelfPotionsReturningWork(p, ability.selfPotions());
boolean main = /* existing main path */;
boolean sideConfigured = !ability.selfClearPotions().isEmpty() || !ability.selfPotions().isEmpty();
return main || (sideConfigured && didSide);
```

`PotionMerge.applyIfNeeded`: if target is Player and `PotionImmunity.isImmune` → return false.

- [ ] **Step 1: Implement runtime changes (TDD optional for order helper)**

If extracting order:

```java
enum Phase { DAMAGE, CLEAR, POTION }
static List<Phase> damageAreaPhases() {
    return List.of(Phase.DAMAGE, Phase.CLEAR, Phase.POTION);
}
```

- [ ] **Step 2: Run focused tests PASS**

```
mvn -Dtest=PotionMergeTest,Fake255RulesTest,PotionImmunityTest,MagicEffectParamsClearPotionsTest test
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(effect): real potion clear/immunity runtime; remove amp-255 hack"
```

---

### Task 4: Listeners + cooldown success alignment

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/listener/PurifyListener.java` (or add `PotionImmunityListener.java`)
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java` if new class
- Modify: `src/main/java/io/mczju/maggoteers/item/interact/ConfigMagicHandler.java`
- Create: `src/main/java/io/mczju/maggoteers/listener/PotionImmunityEventPolicy.java` (optional tiny helper)
- Create: `src/test/java/io/mczju/maggoteers/listener/PotionImmunityEventPolicyTest.java`

**Interfaces:**
- `@EventHandler(priority = HIGHEST, ignoreCancelled = true)` on `EntityPotionEffectEvent`
- Cancel only `ADDED` / `CHANGED` when immune; never `REMOVED` / `CLEARED`
- Damage: `PotionImmunity.isImmuneToCause` for POISON/WITHER
- Drop `immunity_<cause>` id convention
- `ConfigMagicHandler`: check `onCooldown` first; execute; **charge CD only on success**; play FX after success

```java
if (CooldownService.onCooldown(player.getUniqueId(), itemId)) {
    player.sendMessage(Component.text("技能冷却中…", NamedTextColor.GRAY));
    return;
}
if (!(game instanceof MaggoteersGame mg)) return;
boolean success = EffectService.executeAbility(player, mg, ability);
if (!success) return;
CooldownService.tryUse(player, stack, itemId, ability.cooldownSec());
MagicFxService.play(...); // move after success
if (ability.consume()) spendOneFromMainHand(player);
```

- [ ] **Step 1: Policy test**

```java
@Test
void shouldCancelAddedAndChangedOnly() {
    assertTrue(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.ADDED));
    assertTrue(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.CHANGED));
    assertFalse(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.REMOVED));
    assertFalse(PotionImmunityEventPolicy.shouldCancel(EntityPotionEffectEvent.Action.CLEARED));
}
```

- [ ] **Step 2: Implement listeners + CD fix**

- [ ] **Step 3: Tests PASS**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(effect): block immune potion reapply; charge weapon CD on success only"
```

---

### Task 5: Migrate jar YAML resources

**Files:**
- Modify: `src/main/resources/items/maggoteers.yml` (`cataclysm`, `emp`, `holy_water`, `phantasm`)
- Modify: `src/main/resources/rewards.yml` (`a3w_imm_wither`, `a3w_imm_poison`, `a3w_imm_slow`)
- Do **not** modify `affixes.yml`

**Silence list:** `STRENGTH, SPEED, INVISIBILITY, FIRE_RESISTANCE, RESISTANCE, REGENERATION`  
**Purify list:** `WITHER, SLOWNESS, WEAKNESS, BLINDNESS, POISON, DARKNESS`

- [ ] **Step 1: cataclysm** — replace fake 255 block with `clear_potions: [STRENGTH, SPEED, INVISIBILITY, FIRE_RESISTANCE, RESISTANCE, REGENERATION]`

- [ ] **Step 2: emp** — same `clear_potions`; keep only `SLOWNESS` amp 9 duration 200 in `potions`

- [ ] **Step 3: holy_water** — purify `clear_potions`; remove fake 255; keep real heal entries if any; `targets: self`

- [ ] **Step 4: phantasm** — purify types → `self_clear_potions`; keep INSTANT_DAMAGE in `self_potions`; keep RESISTANCE temp + expiry

- [ ] **Step 5: a3w_imm_*** — `immunity: true` only; no amp/duration/trigger/expiry

```yaml
        effect: ADD_POTION
        params:
          potion: WITHER
          immunity: true
        unique: true
        stack: IGNORE
```

- [ ] **Step 6: Grep leftovers**

```
rg "amp:\s*255" src/main/resources/items src/main/resources/rewards.yml
```

Any remaining duration 0/1 in rewards/items must be migrated. Affixes-only OK.

- [ ] **Step 7: Commit**

```bash
git commit -m "content: migrate silence/purify/immunity YAML off fake amp-255"
```

---

### Task 6: Docs, deploy sync, spec status

**Files:**
- Modify: `CLAUDE.md` (§10.2 / §18)
- Modify: `.cursor/skills/maggoteers-plugin-config/reference.md`
- Modify: `.cursor/skills/maggoteers-build-deploy/SKILL.md`
- Modify: `.cursor/skills/maggoteers-build-deploy/reference.md` — **add `items/maggoteers.yml` to default sync list** (rewards already synced)
- Modify: `scripts/build-and-deploy.ps1` if it hardcodes the file list
- Modify: `docs/dev-log.md`
- Modify: `docs/superpowers/specs/2026-08-12-potion-clear-immunity-design.md` status → `Approved`

Deploy note: jar alone is insufficient; `saveResource(false)` never overwrites; hard-fail catches stale servers.

- [ ] **Step 1: Update docs/skills (UTF-8)**

- [ ] **Step 2: Full `mvn test`**

- [ ] **Step 3: Commit**

```bash
git commit -m "docs: retire D1 amp-255 purify; require rewards/items deploy sync"
```

---

### Task 7: Deploy verification (manual)

- [ ] **Step 1:** Build + deploy (jar + `rewards.yml` + `items/maggoteers.yml`)

- [ ] **Step 2:** Confirm server files contain `clear_potions` / `immunity: true`

- [ ] **Step 3:** Restart Paper; enable log has no fake-255 hard-fail

- [ ] **Step 4:** Smoke
  1. Strength+Resistance mob → cataclysm: damage under Resistance, then buffs gone, no amp-255
  2. EMP: buffs cleared, Slowness remains
  3. Holy water / phantasm: debuffs cleared
  4. `a3w_imm_wither`: no wither apply/damage
  5. Optional stale YAML → load hard-fails

---

## Spec coverage checklist

| Spec item | Task |
|-----------|------|
| `clear_potions` / `self_clear_potions` | 1–3, 5 |
| clear-only BUFF_AREA | 2–3, 5 |
| damage → clear → potions | 3 |
| `immunity: true` lifecycle + non-instant | 1–2, 5 |
| fake-255 hard-fail rewards/items | 2, 5 |
| deploy sync / saveResource | 6–7 |
| `PotionImmunity` shared helper | 1, 3 |
| EntityPotionEffectEvent ADDED/CHANGED | 4 |
| PurifyListener rewrite | 4 |
| Remove amp>=255 hack | 3 |
| executeAbility / CD success | 3–4 |
| YAML migration | 5 |
| CLAUDE / skill / dev-log | 6 |
| Manual tests | 7 |

## Out of scope

- `affixes.yml` amp 255
- New Effect enums / clear_preset macros
- Auto-migrating files already under `plugins/Maggoteers/`
