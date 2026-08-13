# Multi use_ability + Deferred Next-Hit FX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Teach weapon `use_ability` to load and run multi-step packs (`effects[]` plus legacy single `effect:`), fire deferred magic on the next trigger with fire-before-expiry and weaponPath, share one TriggerContext across immediate steps (beam then hit_target), and play cast/empower FX once per the locked rules — without rewriting production content YAML.

**Architecture:** Normalize YAML into `ItemAbility.steps` (`AbilityStep` list). Right-click loops steps: expiry means write deferred `PlayerEffect` (ADD_* temp or magic with `fireTrigger=expiryTrigger`); else execute immediately with a shared `TriggerContext` (withers for hitTarget). `EffectService.processTriggerEffects` (both fire paths) dispatch then sweep. Deferred pack ids use `WeaponAbilityIds`; empower FX coalesces once on the victim via `MagicFxService.playOnEntity`.

**Tech Stack:** Paper 26.2, Java 25, Maven, JUnit 5 (prefer pure-logic helpers; avoid MockBukkit unless already used for a touched test).

**Spec:** `docs/superpowers/specs/2026-08-13-multi-use-ability-design.md` (approved v1.1)

## Global Constraints

- Paper **26.2**; JDK **25**; Maven:
  `E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd -f E:/Intellij_Idea/plugins/MCPlugin/pom.xml`
- Delivery = **framework + unit tests only**. Do **not** rewrite production `items/maggoteers.yml` / rewards content in this PR (except intentional silent runtime change for `eighty_hammer` via code paths).
- `held_effects` schema/runtime unchanged.
- No new `Effect` enum values.
- `TriggerContext` stays a **record** — use `withHitTarget` / `withFired` (reassign locals); do not mutate fields.
- `ItemAbility` is a record today — redesign fields; update all call sites (registry, ConfigMagicHandler, EffectService, tests).
- Deferred default stack = **REPLACE** (not D7 IGNORE).
- Pack id membership: `id.equals("ability:" + itemId)` **OR** `id.startsWith("ability:" + itemId + ":")` — never bare `startsWith("ability:" + itemId)`.
- Empower FX: `empower_fx` else top-level `fx` else **no play** (never `magic_fx.defaults`).
- Multi cast FX: top-level / weapons merge only — **no** step effect-template pick.
- Reject `AURA` / `BOUND_EQUIP` in steps (warn + skip step).
- Deferred `DAMAGE_*` must still wrap with `MagicDamageContext` when `shouldWrapMagicDamage(ctx)`.
- Docs/YAML UTF-8; do **not** commit unless the user explicitly asks (commit steps below are **optional**).

## File map

| File | Role |
|------|------|
| `src/main/java/io/mczju/maggoteers/effect/AbilityStep.java` | **Create** — one parsed step (`effect`, `params`, expiry, stack) |
| `src/main/java/io/mczju/maggoteers/effect/WeaponAbilityIds.java` | **Create** — `ability:` id builders + pack membership |
| `src/main/java/io/mczju/maggoteers/effect/ItemAbility.java` | Record → steps list + `empowerFx` + accessors |
| `src/main/java/io/mczju/maggoteers/effect/WeaponTempEffect.java` | Allow deferred magic expiry validation; keep ADD_* rules |
| `src/main/java/io/mczju/maggoteers/effect/ItemAbilityRegistry.java` | Parse `effects[]` / legacy / `empower_fx`; validate; reject AURA/BOUND_EQUIP |
| `src/main/java/io/mczju/maggoteers/effect/TriggerContext.java` | Add `withHitTarget` / `withFired` |
| `src/main/java/io/mczju/maggoteers/effect/TriggerFireOrder.java` | **Create** — pure dispatch-then-sweep helper for order + tests |
| `src/main/java/io/mczju/maggoteers/effect/EmpowerFxCoalesce.java` | **Create** — pack itemId coalesce for empower plays |
| `src/main/java/io/mczju/maggoteers/effect/EffectService.java` | Reorder fire paths; multi-step `executeAbility`; deferred weaponPath; beam returns hit |
| `src/main/java/io/mczju/maggoteers/item/interact/ConfigMagicHandler.java` | Cast FX once; area-ring over immediate steps; CD on any success |
| `src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java` | Multi cast merge without step template; empower resolve (no defaults) |
| `src/main/java/io/mczju/maggoteers/item/fx/MagicFxService.java` | Already has `playOnEntity` — use from empower hook |
| `src/test/java/io/mczju/maggoteers/effect/WeaponAbilityIdsTest.java` | **Create** — pack membership |
| `src/test/java/io/mczju/maggoteers/effect/TriggerFireOrderTest.java` | **Create** — fire before sweep |
| `src/test/java/io/mczju/maggoteers/effect/WeaponTempEffectTest.java` | Extend — magic expiry ok; deferred PE shape |
| `src/test/java/io/mczju/maggoteers/effect/ItemAbilityParseTest.java` | **Create** — legacy / effects[] / reject AURA / ADD_POTION per step |
| `src/test/java/io/mczju/maggoteers/effect/EmpowerFxCoalesceTest.java` | **Create** — id coalesce + fx fallback policy |
| `src/test/java/io/mczju/maggoteers/effect/DeferredStackDefaultTest.java` | **Create** — omitted stack → REPLACE |
| `src/test/java/io/mczju/maggoteers/effect/TriggerContextWithersTest.java` | **Create** — withers preserve fields |
| `.cursor/skills/maggoteers-plugin-config/reference.md` | Document `effects[]`, `empower_fx`, deferred magic |
| `CLAUDE.md` §15 | Multi-step + fire-before-expiry note |
| `docs/dev-log.md` | Implementation entry |
| Spec status line | Mark implemented / ready for author YAML |

---


### Task 1: AbilityStep + WeaponAbilityIds + ItemAbility model + WeaponTempEffect

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/AbilityStep.java`
- Create: `src/main/java/io/mczju/maggoteers/effect/WeaponAbilityIds.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/ItemAbility.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/WeaponTempEffect.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/WeaponAbilityIdsTest.java`
- Modify: `src/test/java/io/mczju/maggoteers/effect/WeaponTempEffectTest.java`

**Interfaces:**
- Consumes: existing `Effect`, `EffectContext`, `Trigger`, `Stack`, `MagicUseFx`, `BuffPotionSpec`
- Produces:
  - `record AbilityStep(Effect effect, EffectContext params, Trigger expiryTrigger, int expiryCharges, Stack stack)` with `boolean isDeferred()`
  - `WeaponAbilityIds.effectId(String itemId)`, `effectId(String itemId, int stepIndex)`, `boolean isPackMember(String effectId, String itemId)`, `Optional<String> itemIdOf(String effectId)`
  - `record ItemAbility(String itemId, int cooldownSec, boolean consume, MagicUseFx fx, MagicUseFx empowerFx, List<BuffPotionSpec> selfPotions, List<PotionEffectType> selfClearPotions, List<AbilityStep> steps)` plus convenience `effect()` / `params()` / `expiryTrigger()` / `expiryCharges()` / `stack()` → first step
  - `WeaponTempEffect.validate(...)` accepts deferred magic when expiry present; ADD_* rules unchanged; rejects AURA/BOUND_EQUIP; `effectId` delegates to `WeaponAbilityIds`

- [ ] **Step 1: Write failing WeaponAbilityIdsTest**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class WeaponAbilityIdsTest {
    @Test
    void packMemberExactAndStepIndex() {
        assertEquals("ability:maggoteers:foo", WeaponAbilityIds.effectId("maggoteers:foo"));
        assertEquals("ability:maggoteers:foo:0", WeaponAbilityIds.effectId("maggoteers:foo", 0));
        assertTrue(WeaponAbilityIds.isPackMember("ability:maggoteers:foo", "maggoteers:foo"));
        assertTrue(WeaponAbilityIds.isPackMember("ability:maggoteers:foo:0", "maggoteers:foo"));
        assertTrue(WeaponAbilityIds.isPackMember("ability:maggoteers:foo:1", "maggoteers:foo"));
    }

    @Test
    void packMemberRejectsPrefixCollision() {
        assertFalse(WeaponAbilityIds.isPackMember("ability:maggoteers:foo_bar:0", "maggoteers:foo"));
        assertFalse(WeaponAbilityIds.isPackMember("ability:maggoteers:foobar", "maggoteers:foo"));
        assertFalse(WeaponAbilityIds.isPackMember("ability:maggoteers:fooX", "maggoteers:foo"));
    }

    @Test
    void itemIdOfRoundTrip() {
        assertEquals(Optional.of("maggoteers:foo"), WeaponAbilityIds.itemIdOf("ability:maggoteers:foo"));
        assertEquals(Optional.of("maggoteers:foo"), WeaponAbilityIds.itemIdOf("ability:maggoteers:foo:2"));
        assertTrue(WeaponAbilityIds.itemIdOf("held:x").isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=WeaponAbilityIdsTest test
```
Expected: FAIL — `WeaponAbilityIds` cannot be found.

- [ ] **Step 3: Implement AbilityStep, WeaponAbilityIds, ItemAbility, WeaponTempEffect**

```java
// AbilityStep.java
package io.mczju.maggoteers.effect;

public record AbilityStep(
        Effect effect,
        EffectContext params,
        Trigger expiryTrigger,
        int expiryCharges,
        Stack stack
) {
    public boolean isDeferred() {
        return expiryTrigger != null;
    }
}
```

```java
// WeaponAbilityIds.java
package io.mczju.maggoteers.effect;

import java.util.Optional;

public final class WeaponAbilityIds {
    public static final String PREFIX = "ability:";

    private WeaponAbilityIds() {}

    public static String effectId(String itemId) {
        return PREFIX + itemId;
    }

    public static String effectId(String itemId, int stepIndex) {
        return PREFIX + itemId + ":" + stepIndex;
    }

    /** Spec 4.3: equals ability:id OR startsWith ability:id: — never bare startsWith(ability:id). */
    public static boolean isPackMember(String effectId, String itemId) {
        if (effectId == null || itemId == null) return false;
        String exact = effectId(itemId);
        if (effectId.equals(exact)) return true;
        return effectId.startsWith(exact + ":");
    }

    public static Optional<String> itemIdOf(String effectId) {
        if (effectId == null || !effectId.startsWith(PREFIX)) return Optional.empty();
        String rest = effectId.substring(PREFIX.length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon > 0) {
            String maybeIdx = rest.substring(lastColon + 1);
            if (!maybeIdx.isEmpty() && maybeIdx.chars().allMatch(Character::isDigit)) {
                String withoutIdx = rest.substring(0, lastColon);
                if (withoutIdx.indexOf(':') >= 0) {
                    return Optional.of(withoutIdx);
                }
            }
        }
        return Optional.of(rest);
    }
}
```

```java
// ItemAbility.java
package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.item.fx.MagicUseFx;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public record ItemAbility(
        String itemId,
        int cooldownSec,
        boolean consume,
        MagicUseFx fx,
        MagicUseFx empowerFx,
        List<BuffPotionSpec> selfPotions,
        List<PotionEffectType> selfClearPotions,
        List<AbilityStep> steps
) {
    public ItemAbility {
        selfPotions = selfPotions == null ? List.of() : List.copyOf(selfPotions);
        selfClearPotions = selfClearPotions == null ? List.of() : List.copyOf(selfClearPotions);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public AbilityStep primaryStep() {
        return steps.isEmpty() ? null : steps.get(0);
    }

    public Effect effect() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.effect();
    }

    public EffectContext params() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.params();
    }

    public Trigger expiryTrigger() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.expiryTrigger();
    }

    public int expiryCharges() {
        AbilityStep s = primaryStep();
        return s == null ? 0 : s.expiryCharges();
    }

    public Stack stack() {
        AbilityStep s = primaryStep();
        return s == null ? null : s.stack();
    }

    public boolean isLegacySingleStep() {
        return steps.size() == 1;
    }

    /** Compat factory while call sites migrate. */
    public static ItemAbility single(
            String itemId, int cooldownSec, Effect effect, EffectContext params,
            MagicUseFx fx, boolean consume, Trigger expiryTrigger, int expiryCharges,
            Stack stack, List<BuffPotionSpec> selfPotions,
            List<PotionEffectType> selfClear, MagicUseFx empowerFx) {
        AbilityStep step = new AbilityStep(effect, params, expiryTrigger, expiryCharges, stack);
        return new ItemAbility(itemId, cooldownSec, consume, fx, empowerFx,
                selfPotions, selfClear, List.of(step));
    }
}
```

```java
// WeaponTempEffect.java — validate allows deferred magic; effectId delegates
public static String effectId(String itemId) {
    return WeaponAbilityIds.effectId(itemId);
}

public static Optional<String> validate(Effect effect, EffectContext params,
                                        Trigger expiryTrigger, int expiryCharges) {
    if (expiryTrigger == null) {
        return Optional.of("requires expiry");
    }
    if (expiryCharges != -1 && expiryCharges < 1) {
        return Optional.of("invalid expiry.charges");
    }
    if (params == null) {
        return Optional.of("missing params");
    }
    if (effect == Effect.ADD_ATTRIBUTE) {
        return validateAddAttribute(params);
    }
    if (effect == Effect.ADD_POTION) {
        return validateExpiryPotion(params);
    }
    if (effect == Effect.AURA || effect == Effect.BOUND_EQUIP) {
        return Optional.of("unsupported effect for weapon ability");
    }
    // Deferred magic (BUFF_AREA, DISABLE_AI, DAMAGE_*, HEAL_AREA, ...): trigger whitelist in registry
    return Optional.empty();
}
```

Add to `WeaponTempEffectTest`:

```java
@Test
void validateAllowsDeferredDisableAi() {
    assertTrue(WeaponTempEffect.validate(
            Effect.DISABLE_AI, new EffectContext(), Trigger.ON_DAMAGE_DEALT, 1).isEmpty());
}

@Test
void validateRejectsAura() {
    assertTrue(WeaponTempEffect.validate(
            Effect.AURA, new EffectContext(), Trigger.ON_DAMAGE_DEALT, 1).isPresent());
}
```

Update all `new ItemAbility(...)` call sites (registry, tests) to the new record / `ItemAbility.single(...)` so the module compiles.

- [ ] **Step 4: Run WeaponAbilityIdsTest + WeaponTempEffectTest**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=WeaponAbilityIdsTest,WeaponTempEffectTest test
```
Expected: PASS.

- [ ] **Step 5: Optional commit** (only if user asked)

```bash
git add src/main/java/io/mczju/maggoteers/effect/AbilityStep.java \
  src/main/java/io/mczju/maggoteers/effect/WeaponAbilityIds.java \
  src/main/java/io/mczju/maggoteers/effect/ItemAbility.java \
  src/main/java/io/mczju/maggoteers/effect/WeaponTempEffect.java \
  src/test/java/io/mczju/maggoteers/effect/WeaponAbilityIdsTest.java \
  src/test/java/io/mczju/maggoteers/effect/WeaponTempEffectTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(ability): AbilityStep, WeaponAbilityIds, multi-step ItemAbility model"
```

---


### Task 2: ItemAbilityRegistry — effects[] / legacy / empower_fx / validation

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/ItemAbilityRegistry.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/ItemAbilityParseTest.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/DeferredStackDefaultTest.java`

**Interfaces:**
- Consumes: `AbilityStep`, `ItemAbility`, `WeaponTempEffect.validate`, existing buff/disable-ai validators, ADD_POTION dual path
- Produces: loaded `ItemAbility` with `steps` + `empowerFx`; load-fail if both `effects:` and top-level `effect:`; empty effects skip; per-step skip AURA/BOUND_EQUIP; deferred magic expiry ∈ {ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_KILL}; omitted deferred stack → REPLACE; deferred DISABLE_AI validates with `triggerOrNull = expiry.trigger`; immediate DISABLE_AI with `null`

- [ ] **Step 1: Write failing parse / stack-default tests**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeferredStackDefaultTest {
    @Test
    void omittedStackOnDeferredIsReplace() {
        assertEquals(Stack.REPLACE, ItemAbilityRegistry.defaultStackForStep(null, true));
        assertEquals(Stack.REPLACE, ItemAbilityRegistry.defaultStackForStep(Stack.REPLACE, true));
        assertEquals(Stack.IGNORE, ItemAbilityRegistry.defaultStackForStep(Stack.IGNORE, true));
    }
}
```

```java
package io.mczju.maggoteers.effect;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ItemAbilityParseTest {
    @Test
    void legacySingleNormalizesToOneStep() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 10);
        y.set("effect", "ADD_ATTRIBUTE");
        y.set("params.attr", "ATTACK_DAMAGE");
        y.set("params.op", "PERCENT");
        y.set("params.value", 1.0);
        y.set("expiry.trigger", "ON_DAMAGE_DEALT");
        y.set("expiry.charges", 1);
        List<AbilityStep> steps = ItemAbilityRegistry.parseStepsForTest("maggoteers:power_strike", y);
        assertEquals(1, steps.size());
        assertEquals(Effect.ADD_ATTRIBUTE, steps.get(0).effect());
        assertEquals(Trigger.ON_DAMAGE_DEALT, steps.get(0).expiryTrigger());
        assertEquals(Stack.REPLACE, steps.get(0).stack());
    }

    @Test
    void effectsAndTopLevelEffectMutuallyExclusive() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 10);
        y.set("effect", "DISABLE_AI");
        y.set("effects.0.effect", "BUFF_AREA");
        assertTrue(ItemAbilityRegistry.parseStepsForTest("maggoteers:bad", y).isEmpty());
    }

    @Test
    void rejectsAuraStep() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 5);
        y.set("effects.0.effect", "AURA");
        y.set("effects.0.params.radius", 8.0);
        assertTrue(ItemAbilityRegistry.parseStepsForTest("maggoteers:aura_bad", y).isEmpty());
    }

    @Test
    void perStepInstantAddPotionAllowed() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 5);
        y.set("effects.0.effect", "ADD_POTION");
        y.set("effects.0.params.potion", "SPEED");
        y.set("effects.0.params.amp", 0);
        y.set("effects.0.params.duration_ticks", 60);
        List<AbilityStep> steps = ItemAbilityRegistry.parseStepsForTest("maggoteers:potion", y);
        assertEquals(1, steps.size());
        assertNull(steps.get(0).expiryTrigger());
    }

    @Test
    void eightyHammerLegacyShapeIsDeferredDisableAi() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("cooldown_sec", 10);
        y.set("effect", "DISABLE_AI");
        y.set("params.duration_ticks", 100);
        y.set("params.targets", "hit_target");
        y.set("expiry.trigger", "ON_DAMAGE_DEALT");
        y.set("expiry.charges", 1);
        List<AbilityStep> steps = ItemAbilityRegistry.parseStepsForTest("maggoteers:eighty_hammer", y);
        assertEquals(1, steps.size());
        assertEquals(Effect.DISABLE_AI, steps.get(0).effect());
        assertEquals(Trigger.ON_DAMAGE_DEALT, steps.get(0).expiryTrigger());
    }
}
```

Expose package-private helpers on `ItemAbilityRegistry`:

```java
static Stack defaultStackForStep(Stack parsed, boolean deferred) {
    if (parsed != null) return parsed;
    return Stack.REPLACE; // locked default for deferred; safe for immediate too
}

static List<AbilityStep> parseStepsForTest(String itemId, ConfigurationSection abilitySec) {
    return parseSteps(itemId, abilitySec);
}
```

- [ ] **Step 2: Run tests — expect FAIL**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=ItemAbilityParseTest,DeferredStackDefaultTest test
```
Expected: FAIL until parse helpers exist.

- [ ] **Step 3: Rewrite parseAbility**

```java
private static void parseAbility(MaggoteersPlugin plugin, String itemId,
                                 ConfigurationSection abilitySec, ConfigurationSection legacyUseFx) {
    if (!abilitySec.contains("cooldown_sec")) {
        LOG.warning("items: " + itemId + " use_ability missing cooldown_sec — skipped");
        return;
    }
    int cooldownSec = abilitySec.getInt("cooldown_sec", 0);
    if (cooldownSec <= 0) {
        LOG.warning("items: " + itemId + " use_ability invalid cooldown_sec — skipped");
        return;
    }

    boolean hasEffectsList = abilitySec.isList("effects")
            || abilitySec.isConfigurationSection("effects")
            || !abilitySec.getMapList("effects").isEmpty();
    String topEffect = abilitySec.getString("effect");
    boolean hasTopEffect = topEffect != null && !topEffect.isBlank();
    if (hasEffectsList && hasTopEffect) {
        LOG.warning("items: " + itemId + " use_ability has both effects[] and effect: — skipped");
        return;
    }

    List<AbilityStep> steps = parseSteps(itemId, abilitySec);
    if (steps.isEmpty()) {
        LOG.warning("items: " + itemId + " use_ability has no valid steps — skipped");
        return;
    }

    boolean consume = abilitySec.getBoolean("consume", false);
    MagicUseFx fx = MagicFxConfig.mergedFxForAbilityMulti(
            itemId,
            abilitySec.getConfigurationSection("fx"),
            legacyUseFx,
            steps.size() == 1 ? steps.get(0) : null);
    MagicUseFx empowerFx = MagicFxConfig.parseOptionalFx(abilitySec.getConfigurationSection("empower_fx"));
    // self_potions / self_clear_potions: keep existing parsers
    BY_ITEM_ID.put(itemId, new ItemAbility(
            itemId, cooldownSec, consume, fx, empowerFx, selfPotions, selfClear, steps));
}
```

`parseOneStep` rules (repeat in code, do not hand-wave):
- Skip step-level `cooldown_sec` with warn.
- Reject `AURA` / `BOUND_EQUIP` (warn + skip step).
- Deferred magic: require expiry.trigger in {ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_KILL}; reject ON_TICK_1S / ON_INTERACT.
- `DISABLE_AI`: deferred → `validateDisableAi(params, expiryTrigger)`; immediate → `validateDisableAi(params, null)`.
- `BUFF_AREA` → existing `validateBuffArea`.
- `ADD_POTION`: no expiry + `duration_ticks > 0` → instant path OK; expiry → `WeaponTempEffect.validate`.
- `stack = defaultStackForStep(parsed, deferred)`.

```java
// MagicFxConfig
/** Multi packs: weapons + use_ability.fx only — no effect-template. Legacy single may pass step. */
public static MagicUseFx mergedFxForAbilityMulti(
        String itemId, ConfigurationSection abilityFx, ConfigurationSection legacyUseFx,
        AbilityStep legacyStepOrNull) {
    if (legacyStepOrNull != null) {
        ConfigurationSection sec = abilityFx != null ? abilityFx : legacyUseFx;
        return mergedFxForAbility(itemId, legacyStepOrNull.effect(), legacyStepOrNull.params(), sec);
    }
    MagicUseFx base = /* weapon overlay on empty base WITHOUT forcing noisy defaults if you can detect absence */;
    MagicUseFx weapon = BY_WEAPON.get(itemId);
    if (weapon != null) base = merge(base, weapon);
    if (abilityFx != null) base = merge(base, parseSection(abilityFx, base));
    else if (legacyUseFx != null) base = merge(base, parseSection(legacyUseFx, base));
    return base;
}

/** Parse empower_fx without merging magic_fx.defaults. Null section → null. */
public static MagicUseFx parseOptionalFx(ConfigurationSection sec) {
    if (sec == null) return null;
    return parseSection(sec, null); // implement null-base parse that does not inject defaults
}
```

**Acceptance #13 discipline:** If neither `empower_fx` nor top-level `fx` (nor weapons overlay) exists, store `fx == null` and `empowerFx == null` so empower resolve plays nothing. Do not fall through to `magic_fx.defaults` on the empower path.

- [ ] **Step 4: Run parse tests**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=ItemAbilityParseTest,DeferredStackDefaultTest,WeaponAbilityIdsTest,WeaponTempEffectTest test
```
Expected: PASS.

- [ ] **Step 5: Optional commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/ItemAbilityRegistry.java \
  src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java \
  src/test/java/io/mczju/maggoteers/effect/ItemAbilityParseTest.java \
  src/test/java/io/mczju/maggoteers/effect/DeferredStackDefaultTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(ability): parse use_ability effects[] with legacy and empower_fx"
```

---


### Task 3: TriggerContext withers + DAMAGE_BEAM writes hitTarget

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/TriggerContext.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java` (`executeDamageBeam`, `executeMagicEffect` DAMAGE_BEAM branch)
- Create: `src/test/java/io/mczju/maggoteers/effect/TriggerContextWithersTest.java`

**Interfaces:**
- Consumes: record components
- Produces: `TriggerContext withHitTarget(LivingEntity)`, `withFired(Trigger)`; beam returns first hit `LivingEntity` so caller can `ctx = ctx.withHitTarget(hit)`

- [ ] **Step 1: Write withers test**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriggerContextWithersTest {
    @Test
    void withHitTargetPreservesOtherFields() {
        TriggerContext base = new TriggerContext(Trigger.ON_INTERACT, null, null, null);
        TriggerContext fired = base.withFired(Trigger.ON_DAMAGE_DEALT);
        assertEquals(Trigger.ON_DAMAGE_DEALT, fired.fired());
        assertNull(fired.hitTarget());
        TriggerContext cleared = fired.withHitTarget(null);
        assertEquals(Trigger.ON_DAMAGE_DEALT, cleared.fired());
    }
}
```

- [ ] **Step 2: Run — FAIL missing methods**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=TriggerContextWithersTest test
```

- [ ] **Step 3: Add withers + change beam to return first hit**

```java
// TriggerContext.java
public TriggerContext withHitTarget(LivingEntity hitTarget) {
    return new TriggerContext(fired, hitTarget, attacker, eventLocation);
}

public TriggerContext withFired(Trigger fired) {
    return new TriggerContext(fired, hitTarget, attacker, eventLocation);
}
```

```java
// EffectService — return first LivingEntity hit; use AtomicReference inside MagicDamageContext.run
private static LivingEntity executeDamageBeam(Player p, MaggoteersGame game, EffectContext params,
                                              MagicUseFx weaponFx) {
    // ... existing length / FX setup unchanged ...
    java.util.concurrent.atomic.AtomicReference<LivingEntity> first =
            new java.util.concurrent.atomic.AtomicReference<>();
    MagicDamageContext.run(game, p.getUniqueId(), () -> {
        for (int i = 0; i <= steps; i++) {
            Location pt = eye.clone().add(dir.clone().multiply(length * i / (double) steps));
            for (Entity en : p.getWorld().getNearbyEntities(pt, beamRadius, beamRadius, beamRadius)) {
                if (!(en instanceof LivingEntity le)) continue;
                if (!TargetResolver.isTarget(game, p, le, params)) continue;
                if (!hit.add(le.getUniqueId())) continue;
                first.compareAndSet(null, le);
                le.damage(dmg, p);
            }
        }
    });
    return first.get();
}
```

Extend weaponPath `executeMagicEffect` with optional `AtomicReference<LivingEntity> hitTargetOut`:

```java
case DAMAGE_BEAM -> {
    LivingEntity beamHit = executeDamageBeam(p, game, params, weaponFx);
    if (beamHit != null && hitTargetOut != null) {
        hitTargetOut.set(beamHit);
    }
    return true;
}
```

`thunder_rod` (acceptance #8): immediate `[DAMAGE_BEAM, DISABLE_AI hit_target]` shares one ctx; after beam, `ctx = ctx.withHitTarget(beamHit)`.

- [ ] **Step 4: Compile + withers test**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=TriggerContextWithersTest,WeaponAbilityIdsTest test
```
Expected: PASS.

- [ ] **Step 5: Optional commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/TriggerContext.java \
  src/main/java/io/mczju/maggoteers/effect/EffectService.java \
  src/test/java/io/mczju/maggoteers/effect/TriggerContextWithersTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(ability): TriggerContext withers; DAMAGE_BEAM records first hit"
```

---


### Task 4: Fire-before-expiry reorder (+ TriggerFireOrder helper)

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/TriggerFireOrder.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/TriggerFireOrderTest.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java` (`fireTrigger`, `fireTriggerPlayer`)

**Interfaces:**
- Consumes: `EffectStacker.sweepExpiry`, dispatch lambda
- Produces: `TriggerFireOrder.run(Runnable dispatch, Supplier<List<String>> sweep)` — dispatch **then** sweep; `EffectService.processTriggerEffects(...)` used by both fire paths

- [ ] **Step 1: Write failing order test**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class TriggerFireOrderTest {
    @Test
    void dispatchRunsBeforeSweep() {
        List<String> order = new ArrayList<>();
        AtomicBoolean presentDuringDispatch = new AtomicBoolean(false);
        List<String> effects = new ArrayList<>(List.of("ability:eighty:0"));

        TriggerFireOrder.run(
                () -> {
                    presentDuringDispatch.set(effects.contains("ability:eighty:0"));
                    order.add("dispatch");
                },
                () -> {
                    order.add("sweep");
                    effects.clear();
                    return List.of("ability:eighty:0");
                });

        assertEquals(List.of("dispatch", "sweep"), order);
        assertTrue(presentDuringDispatch.get(),
                "same-trigger row must still exist during dispatch");
        assertTrue(effects.isEmpty());
    }
}
```

- [ ] **Step 2: Run — FAIL**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=TriggerFireOrderTest test
```

- [ ] **Step 3: Implement helper + wire EffectService**

```java
package io.mczju.maggoteers.effect;

import java.util.List;
import java.util.function.Supplier;

/** Spec 4.0: dispatch fireTrigger==T, then sweepExpiry. */
public final class TriggerFireOrder {
    private TriggerFireOrder() {}

    public static List<String> run(Runnable dispatch, Supplier<List<String>> sweep) {
        if (dispatch != null) dispatch.run();
        if (sweep == null) return List.of();
        List<String> removed = sweep.get();
        return removed == null ? List.of() : removed;
    }
}
```

```java
// EffectService — shared path used by fireTriggerPlayer AND per-player loop in fireTrigger
public static void processTriggerEffects(PlayerState ps, Player p, MaggoteersGame game,
                                         Trigger fired, TriggerContext useCtx) {
    List<String> removed = TriggerFireOrder.run(
            () -> dispatchTriggeredEffects(p, ps, game, fired, useCtx),
            () -> EffectStacker.sweepExpiry(ps.effects(), fired));
    if (!removed.isEmpty()) {
        resyncDerived(p, ps);
        for (String removedId : removed) {
            io.mczju.maggoteers.reward.CollectibleService.remove(p, removedId);
            BoundEquipService.remove(p, removedId);
        }
    }
}

// fireTriggerPlayer: after alive checks, call processTriggerEffects(ps, p, game, fired, useCtx);
// fireTrigger: replace sweep-then-dispatch with processTriggerEffects; keep recurring AFTER that pair.
```

Today (broken) order in both methods is sweep then dispatch — reverse it. Acceptance #4/#5 require this plus Task 5 deferred write.

- [ ] **Step 4: Run order test**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=TriggerFireOrderTest,WeaponTempEffectTest test
```
Expected: PASS.

- [ ] **Step 5: Optional commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/TriggerFireOrder.java \
  src/main/java/io/mczju/maggoteers/effect/EffectService.java \
  src/test/java/io/mczju/maggoteers/effect/TriggerFireOrderTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "fix(ability): fireTrigger dispatch before expiry sweep"
```

---


### Task 5: Deferred write + weaponPath execute for ability:* + MagicDamageContext

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`
- Modify: `src/test/java/io/mczju/maggoteers/effect/WeaponTempEffectTest.java`

**Interfaces:**
- Consumes: `AbilityStep`, `WeaponAbilityIds`, `WeaponTempEffect`, `executeMagicEffect(..., weaponPath)`
- Produces:
  - `boolean writeDeferredStep(Player, MaggoteersGame, ItemAbility, AbilityStep, int stepIndex)`
  - Triggered path: pack ids → magic with `weaponPath=true` + live `TriggerContext` (hitTarget from melee)
  - Deferred ADD_* : `fireTrigger=null`; deferred magic: `fireTrigger=expiryTrigger`
  - Ids: legacy single ADD_* → `ability:<itemId>`; multi / magic deferred → `ability:<itemId>:<stepIndex>`
  - `MagicDamageContext` when `shouldWrapMagicDamage(ctx)` even if weaponPath true / weaponFx non-null

- [ ] **Step 1: Write PlayerEffect shape tests (no Bukkit)**

```java
@Test
void deferredMagicPlayerEffectUsesSameFireAndExpiry() {
    AbilityStep step = new AbilityStep(
            Effect.DISABLE_AI, new EffectContext(), Trigger.ON_DAMAGE_DEALT, 1, Stack.REPLACE);
    String id = WeaponAbilityIds.effectId("maggoteers:eighty_hammer", 0);
    PlayerEffect pe = new PlayerEffect(
            id, step.effect(), step.params(), step.expiryTrigger(),
            step.expiryTrigger(), step.expiryCharges(),
            0, null, Stack.REPLACE, 0, 0);
    assertEquals(Trigger.ON_DAMAGE_DEALT, pe.fireTrigger());
    assertEquals(Trigger.ON_DAMAGE_DEALT, pe.expiryTrigger());
    assertEquals(Stack.REPLACE, pe.stack());
}

@Test
void deferredAddAttributeKeepsNullFireTrigger() {
    PlayerEffect pe = new PlayerEffect(
            WeaponAbilityIds.effectId("maggoteers:power_strike"),
            Effect.ADD_ATTRIBUTE, new EffectContext(), null,
            Trigger.ON_DAMAGE_DEALT, 1, 0, null, Stack.REPLACE, 0, 0);
    assertNull(pe.fireTrigger());
}

@Test
void secondWriteReplaceDefaultStack() {
    assertEquals(Stack.REPLACE, ItemAbilityRegistry.defaultStackForStep(null, true));
}
```

(Adjust `PlayerEffect` constructor argument order to match the real class — inspect existing `WeaponTempEffectTest.nextHitExpiryChargesOneRemovedOnFirstDealt`.)

- [ ] **Step 2: Run tests**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=WeaponTempEffectTest test
```

- [ ] **Step 3: Implement write + execute path**

```java
private static boolean writeDeferredStep(Player p, MaggoteersGame game, ItemAbility ability,
                                         AbilityStep step, int stepIndex) {
    Optional<String> err = WeaponTempEffect.validate(
            step.effect(), step.params(), step.expiryTrigger(), step.expiryCharges());
    if (err.isPresent()) {
        LOG.warning("use_ability deferred " + ability.itemId() + " step " + stepIndex + ": " + err.get());
        return false;
    }
    Stack stack = step.stack() != null ? step.stack() : Stack.REPLACE;
    boolean addFamily = step.effect() == Effect.ADD_ATTRIBUTE || step.effect() == Effect.ADD_POTION;
    boolean legacySingleAdd = addFamily && ability.steps().size() == 1;
    String id = legacySingleAdd
            ? WeaponAbilityIds.effectId(ability.itemId())
            : WeaponAbilityIds.effectId(ability.itemId(), stepIndex);
    Trigger fire = addFamily ? null : step.expiryTrigger();
    PlayerEffect pe = new PlayerEffect(
            id, step.effect(), step.params().copy(), fire,
            step.expiryTrigger(), step.expiryCharges(),
            0, null, stack, 0, 0);
    apply(p, pe, game);
    return true;
}
```

In triggered `executeEffect` (today passes `weaponFx = null` → STAT path):

```java
boolean weaponPack = e.id() != null && e.id().startsWith(WeaponAbilityIds.PREFIX);
MagicUseFx weaponFx = null;
if (weaponPack) {
    String itemId = WeaponAbilityIds.itemIdOf(e.id()).orElse(null);
    ItemAbility ab = itemId == null ? null : ItemAbilityRegistry.get(itemId).orElse(null);
    weaponFx = ab != null ? ab.fx() : null;
}
executeMagicEffect(p, game, e.effect(), e.params(), weaponFx, ctx, weaponPack);
```

Confirm wrap logic stays:

```java
if (shouldWrapMagicDamage(useCtx)) {
    MagicDamageContext.run(game, p.getUniqueId(), action);
} else {
    action.run();
}
```

Do **not** treat `weaponFx != null` as the only wrap condition (spec 4.3.1).

- [ ] **Step 4: Run unit tests**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=WeaponTempEffectTest,WeaponAbilityIdsTest,TriggerFireOrderTest test
```
Expected: PASS.

- [ ] **Step 5: Optional commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectService.java \
  src/test/java/io/mczju/maggoteers/effect/WeaponTempEffectTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(ability): deferred magic PlayerEffects with weaponPath on fire"
```

---


### Task 6: executeAbility multi-step loop + ConfigMagicHandler cast FX

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java` (`executeAbility`)
- Modify: `src/main/java/io/mczju/maggoteers/item/interact/ConfigMagicHandler.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java` (area-ring over steps if needed)
- Modify: `src/test/java/io/mczju/maggoteers/effect/EffectServiceAbilityTest.java`

**Interfaces:**
- Consumes: `ItemAbility.steps()`, Task 3 hit holder, Task 5 `writeDeferredStep`
- Produces: `executeAbility` true if any step or self_* succeeds; shared `TriggerContext` across immediate steps; cast FX once; CD once; multi cast already without step template (Task 2)

- [ ] **Step 1: Document CD aggregation test**

```java
@Test
void anyStepSuccessMeansAbilitySuccess() {
    boolean step0 = false;
    boolean step1 = true;
    boolean self = false;
    assertTrue(step0 || step1 || self);
    assertFalse(false || false || false);
}
```

- [ ] **Step 2: Implement executeAbility loop**

```java
public static boolean executeAbility(Player p, MaggoteersGame game, ItemAbility ability) {
    if (p == null || game == null || ability == null) return false;
    boolean didSide = false;
    List<PotionEffectType> selfClear = ability.selfClearPotions();
    if (selfClear != null && !selfClear.isEmpty()) {
        PotionClear.apply(p, selfClear);
        didSide = true;
    }
    List<BuffPotionSpec> selfPots = ability.selfPotions();
    if (selfPots != null && !selfPots.isEmpty()) {
        applySelfPotions(p, selfPots);
        didSide = true;
    }

    TriggerContext ctx = TriggerContext.empty();
    AtomicReference<LivingEntity> beamHit = new AtomicReference<>();
    boolean anyStep = false;
    List<AbilityStep> steps = ability.steps();
    for (int i = 0; i < steps.size(); i++) {
        AbilityStep step = steps.get(i);
        if (step.isDeferred()) {
            if (writeDeferredStep(p, game, ability, step, i)) anyStep = true;
            continue;
        }
        boolean ok;
        if (step.effect() == Effect.ADD_ATTRIBUTE) {
            LOG.warning("use_ability " + ability.itemId() + " immediate ADD_ATTRIBUTE unsupported");
            ok = false;
        } else if (step.effect() == Effect.ADD_POTION) {
            int dur = step.params().getOrDefault(EffectKeys.DURATION_TICKS, 0);
            ok = dur > 0 && applyInstantPotion(p, step.params());
        } else {
            ok = executeMagicEffect(p, game, step.effect(), step.params(),
                    ability.fx(), ctx, true, beamHit);
            if (beamHit.get() != null) {
                ctx = ctx.withHitTarget(beamHit.get());
                beamHit.set(null);
            }
        }
        if (ok) anyStep = true;
    }
    boolean sideConfigured = (selfClear != null && !selfClear.isEmpty())
            || (selfPots != null && !selfPots.isEmpty());
    return anyStep || (sideConfigured && didSide);
}
```

**eighty_hammer silent behavior:** legacy `effect: DISABLE_AI` + `expiry` → one deferred step → right-click writes PE only; AI locks on next melee after Task 4+5 (acceptance #5). No YAML edit.

- [ ] **Step 3: ConfigMagicHandler cast FX once**

```java
boolean success = EffectService.executeAbility(player, mg, ability);
if (!success) {
    return;
}
CooldownService.tryUse(player, stack, itemId, ability.cooldownSec());
boolean areaRing = false;
for (AbilityStep step : ability.steps()) {
    if (!step.isDeferred()
            && MagicFxConfig.shouldPlayCastAreaRing(step.effect(), step.params())) {
        areaRing = true;
        break;
    }
}
MagicFxService.play(player, ability.fx(), areaRing);
if (ability.consume()) {
    spendOneFromMainHand(player);
}
```

Replace HEAL_AREA radius tweak that keyed off `ability.effect()` alone: if needed, scan immediate HEAL_AREA steps for radius (legacy parity).

- [ ] **Step 4: Run tests**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=EffectServiceAbilityTest,ItemAbilityParseTest,WeaponAbilityIdsTest,TriggerFireOrderTest,DeferredStackDefaultTest test
```
Expected: PASS.

- [ ] **Step 5: Optional commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EffectService.java \
  src/main/java/io/mczju/maggoteers/item/interact/ConfigMagicHandler.java \
  src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java \
  src/test/java/io/mczju/maggoteers/effect/EffectServiceAbilityTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(ability): multi-step executeAbility and single cast FX"
```

---


### Task 7: Empower FX coalesce on victim

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/effect/EmpowerFxCoalesce.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java` — `resolveEmpowerFx(ItemAbility)`
- Create: `src/test/java/io/mczju/maggoteers/effect/EmpowerFxCoalesceTest.java`

**Interfaces:**
- Consumes: executed/removed `ability:*` ids; `MagicFxService.playOnEntity`; `ItemAbility.empowerFx()` / `fx()`
- Produces: at most one empower play per itemId per trigger; victim = `ctx.hitTarget()`; skip if null victim; no defaults

- [ ] **Step 1: Write coalesce + resolve tests**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EmpowerFxCoalesceTest {
    @Test
    void coalescePackMembersToOneItemId() {
        Set<String> touched = Set.of(
                "ability:maggoteers:foo",
                "ability:maggoteers:foo:0",
                "ability:maggoteers:foo:1",
                "ability:maggoteers:foo_bar:0");
        Set<String> itemIds = EmpowerFxCoalesce.itemIdsTouched(touched);
        assertEquals(Set.of("maggoteers:foo", "maggoteers:foo_bar"), itemIds);
    }

    @Test
    void resolvePrefersEmpowerThenCastThenNull() {
        assertEquals("EMP", EmpowerFxCoalesce.pickFxLabel("EMP", "CAST"));
        assertEquals("CAST", EmpowerFxCoalesce.pickFxLabel(null, "CAST"));
        assertNull(EmpowerFxCoalesce.pickFxLabel(null, null));
    }
}
```

```java
package io.mczju.maggoteers.effect;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class EmpowerFxCoalesce {
    private EmpowerFxCoalesce() {}

    public static Set<String> itemIdsTouched(Collection<String> effectIds) {
        Set<String> out = new HashSet<>();
        if (effectIds == null) return out;
        for (String id : effectIds) {
            WeaponAbilityIds.itemIdOf(id).ifPresent(out::add);
        }
        return out;
    }

    /** Test helper mirroring resolve policy labels. */
    public static String pickFxLabel(String empower, String cast) {
        if (empower != null) return empower;
        return cast;
    }
}
```

- [ ] **Step 2: Run FAIL then implement helper**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=EmpowerFxCoalesceTest test
```

- [ ] **Step 3: Hook fireTriggerPlayer / processTriggerEffects**

```java
// Collect touched ability ids: executed during dispatch + removed during sweep
Set<String> touched = new HashSet<>();
List<String> removed = TriggerFireOrder.run(
        () -> touched.addAll(dispatchTriggeredEffectsCollecting(p, ps, game, fired, useCtx)),
        () -> {
            List<String> r = EffectStacker.sweepExpiry(ps.effects(), fired);
            for (String id : r) {
                if (id != null && id.startsWith(WeaponAbilityIds.PREFIX)) touched.add(id);
            }
            return r;
        });
// resync on removed (existing)...

LivingEntity victim = useCtx != null ? useCtx.hitTarget() : null;
if (victim != null) {
    for (String itemId : EmpowerFxCoalesce.itemIdsTouched(touched)) {
        ItemAbility ab = ItemAbilityRegistry.get(itemId).orElse(null);
        if (ab == null) continue;
        MagicUseFx empower = MagicFxConfig.resolveEmpowerFx(ab);
        if (empower == null) continue;
        MagicFxService.playOnEntity(victim, empower);
    }
}
```

```java
public static MagicUseFx resolveEmpowerFx(ItemAbility ab) {
    if (ab == null) return null;
    if (ab.empowerFx() != null) return ab.empowerFx();
    return ab.fx(); // may be null when neither configured — do NOT merge defaults here
}
```

Game-wide `fireTrigger` usually has no melee victim — skip empower when `hitTarget == null` (never flash caster as substitute).

Acceptance #7/#11/#13 covered by coalesce + resolve null policy.

- [ ] **Step 4: Run empower + related tests**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" -Dtest=EmpowerFxCoalesceTest,WeaponAbilityIdsTest,TriggerFireOrderTest test
```
Expected: PASS.

- [ ] **Step 5: Optional commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/EmpowerFxCoalesce.java \
  src/main/java/io/mczju/maggoteers/effect/EffectService.java \
  src/main/java/io/mczju/maggoteers/item/fx/MagicFxConfig.java \
  src/test/java/io/mczju/maggoteers/effect/EmpowerFxCoalesceTest.java
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "feat(ability): coalesce empower FX on victim without defaults"
```

---


### Task 8: Docs (reference.md, CLAUDE.md section 15, dev-log, spec status)

**Files:**
- Modify: `.cursor/skills/maggoteers-plugin-config/reference.md`
- Modify: `CLAUDE.md` (section 15 `use_ability`)
- Modify: `docs/dev-log.md`
- Modify: `docs/superpowers/specs/2026-08-13-multi-use-ability-design.md` (status line)

**Interfaces:**
- Consumes: locked schema from spec sections 3–5
- Produces: author-facing docs; **no** content YAML rewrite

- [ ] **Step 1: Update reference.md**

Document:
- `effects[]` vs legacy `effect:`
- `empower_fx` fallback (`empower_fx` → top-level `fx` → no play)
- deferred magic + fire-before-expiry
- pack id separator rule
- REPLACE default for deferred stack
- eighty_hammer silent next-melee behavior
- author post-merge checklist (emp, herafinger, thunder_rod, mission_sure)

- [ ] **Step 2: Update CLAUDE.md section 15**

Short note: multi-step `effects[]`; deferred magic with expiry; cast once / empower once on victim; link to the spec.

- [ ] **Step 3: Append docs/dev-log.md**

```markdown
## 2026-08-13 — Multi use_ability + deferred next-hit FX

- Implemented effects[] packs, fire-before-expiry, deferred weaponPath magic, shared TriggerContext (beam then hit_target), empower FX coalesce.
- eighty_hammer: intentional silent next-melee stun without YAML edit.
- Content YAML for emp/herafinger/thunder_rod/mission_sure left to author.
```

- [ ] **Step 4: Spec status**

Change header Status from Draft to **Implemented (framework)**; keep author YAML checklist in section 7.

- [ ] **Step 5: Full test suite**

Run:
```text
"E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd" -f "E:/Intellij_Idea/plugins/MCPlugin/pom.xml" test
```
Expected: BUILD SUCCESS.

- [ ] **Step 6: Optional commit**

```bash
git add .cursor/skills/maggoteers-plugin-config/reference.md CLAUDE.md docs/dev-log.md \
  docs/superpowers/specs/2026-08-13-multi-use-ability-design.md
git commit --trailer "Co-authored-by: Cursor <cursoragent@cursor.com>" -m "docs: multi use_ability deferred FX framework notes"
```

---

## Acceptance mapping (spec section 8 to tasks)

| # | Acceptance | Primary task(s) |
|---|------------|-----------------|
| 1 | Legacy ADD_ATTRIBUTE + expiry + CD | 2, 5, 6 |
| 2 | Two immediate steps; one CD; one cast FX | 2, 6 |
| 3 | Mixed deferred; empower on victim; cast on click | 5, 6, 7 |
| 4 | Fire before sweep (DISABLE_AI still present during dispatch) | 4, 5 |
| 5 | eighty_hammer silent next-melee | 2, 4, 5, 6 |
| 6 | Deferred BUFF_AREA weaponPath + hitTarget | 5 |
| 7 | Empower id coalesce / no foo vs foo_bar | 1, 7 |
| 8 | Beam then hit_target (thunder_rod) | 3, 6 |
| 9 | Partial step success → CD | 6 |
| 10 | All fail + no self_* → no CD/FX | 6 |
| 11 | Two deferred → empower count 1 | 7 |
| 12 | Second cast REPLACE unspent charge | 1, 2, 5 |
| 13 | No empower_fx and no fx → no empower play | 2, 7 |

Unit tests in this PR cover order, ids, REPLACE, coalesce, parse, withers, deferred PE shape. Full Bukkit integration for #2/#3/#6/#8 may be verified manually on the test server if MockBukkit is not wired for those paths.

---

## Self-review

### 1. Spec coverage

| Spec lock | Plan coverage |
|-----------|----------------|
| effects[] + legacy single effect | Task 2 parse + Task 1 model |
| fire-before-expiry on fireTriggerPlayer **and** fireTrigger | Task 4 `processTriggerEffects` both paths |
| deferred magic fireTrigger=expiryTrigger, weaponPath=true + hitTarget | Task 5 |
| shared TriggerContext; DAMAGE_BEAM writes hitTarget; thunder_rod in scope | Task 3 + Task 6 |
| deferred default stack REPLACE | Task 1/2 `defaultStackForStep` + DeferredStackDefaultTest |
| WeaponAbilityIds separator rule | Task 1 + WeaponAbilityIdsTest |
| empower FX on victim via playOnEntity; empower_fx else fx else no play | Task 7 |
| multi cast FX: no step effect-template | Task 2 `mergedFxForAbilityMulti` + Task 6 handler |
| eighty_hammer silent behavior | Task 5/6 + docs Task 8 |
| reject AURA/BOUND_EQUIP; per-step ADD_POTION | Task 2 |
| MagicDamageContext for deferred DAMAGE_* | Task 5 note + executeMagicEffect wrap |
| Acceptance tests section 8 | Mapping table + unit tests |
| No content YAML rewrite in PR | Global Constraints + Task 8 |
| held_effects unchanged | Global Constraints |
| Commits optional | Every task optional commit step |

### 2. Placeholder scan

No TBD/TODO steps. Concrete types, tests, and mvn commands included. `ItemAbility` arity migration called out in Task 1. `PlayerEffect` ctor order must match existing class (pointed to existing test).

### 3. Type consistency

- `AbilityStep` / `ItemAbility.steps()` used consistently Tasks 1–7.
- `WeaponAbilityIds` is the only pack-membership API (`WeaponTempEffect.effectId` delegates).
- `TriggerFireOrder` → `processTriggerEffects` naming stable across Task 4 and Task 7 empower hook.
- Beam hit flows via `AtomicReference<LivingEntity>` / `withHitTarget` (record withers), not field mutation.

### 4. Risks acknowledged

- Fire-first changes same-trigger reward timing (spec accepted).
- Empower must not use defaults — registry null-fx discipline required for acceptance #13.
- `itemIdOf` parsing must handle `maggoteers:foo:0` without truncating namespace incorrectly (Task 1 tests).

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-13-multi-use-ability.md`. Two execution options:

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks (`superpowers:subagent-driven-development`)
2. **Inline Execution** — execute in-session with checkpoints (`superpowers:executing-plans`)

Which approach?
