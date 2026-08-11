# BOUND_EQUIP Protection Chestplate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `Effect.BOUND_EQUIP` so a STAT reward can grant and upgrade a bound zero-armor chestplate (Protection II to XVI across 8 levels) with slot locking, conflict destroy, resync, and GUI preview.

**Architecture:** `PlayerState` holds an `UPGRADE_LEVEL` `PlayerEffect`. After merge, explicitly call `BoundEquipService.sync` (never `applyDerived`). View = PDC-tagged chest item from `params.items_by_level`. Extend inventory guards for unequip/swap/hotbar/drag. Load validator enforces schema + at most one global `slot: CHEST` BOUND_EQUIP.

**Tech Stack:** Paper 26.2, Java 25, Maven, JUnit 5. Prefer pure helpers for params/resolve/lock classification; Bukkit equip paths wired without Mockito when possible.

**Spec:** `docs/superpowers/specs/2026-08-11-bound-equip-protection-design.md` (v1.1)

## Global Constraints

- Paper **26.2**; JDK **25**; test command:
  `E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd -f E:/Intellij_Idea/plugins/MCPlugin/pom.xml test`
  (fallback Maven: `E:/Intellij_Idea/IntelliJ IDEA 2025.1.1.1/plugins/maven/lib/maven3/bin/mvn.cmd`)
- Spec is source of truth for progression, toughness cancels, lock intercepts, orphan strip.
- **Do not** route equip through `EffectService.applyDerived`.
- **Do not** add `collectibles.yml` mapping for this reward.
- **Do not** implement auto-hide of other chestplate rewards (content convention).
- v1 slot: **CHEST only**.
- UTF-8 for docs/YAML. Display: prefer Chinese `保护胸甲` (not bare `保护+1`).
- Netherite toughness cancel follows mob template (`-1`), not necessarily vanilla full 3.

## File map

| File | Role |
|------|------|
| `effect/Effect.java` | Add `BOUND_EQUIP` |
| `effect/EffectKeys.java` | `SLOT`, `ITEMS_BY_LEVEL` |
| `effect/EffectParamsParser.java` | Parse `slot`, `items_by_level` |
| `effect/BoundEquipParams.java` | **Create** — parse/validate/resolve item id |
| `effect/BoundEquipService.java` | **Create** — sync / resync / remove / orphan strip |
| `item/ItemKind.java` | Add `BOUND_EQUIP("bound_equip")` |
| `item/RunItemTags.java` | `setBoundEquipPdc`, `isBoundEquip`, `isBoundEquipFor` |
| `listener/BoundEquipLockListener.java` | Stricter lock intercepts (spec §5.3) |
| `effect/EffectService.java` | Explicit sync after apply; resync + remove hooks |
| `reward/RewardLoadValidator.java` | BOUND_EQUIP schema + single CHEST |
| `reward/RewardService.java` | Cross-option CHEST uniqueness during load |
| `reward/RewardOptionIcons.java` | BOUND_EQUIP preview branch |
| `MaggoteersPlugin.java` | Register lock listener |
| `items/maggoteers.yml` | Eight `prot_chest_l1`…`l8` items |
| `rewards.yml` | One STAT option |
| `.cursor/skills/maggoteers-plugin-config/reference.md` | Effect table row |
| `docs/dev-log.md` | Short entry |
| Tests | `BoundEquipParamsTest`, validator cases, service logic tests |

---

### Task 1: Effect enum + params keys + BoundEquipParams

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/Effect.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectKeys.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectParamsParser.java`
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectContext.java` (deep-copy `ITEMS_BY_LEVEL` like `POTIONS`)
- Create: `src/main/java/io/mczju/maggoteers/effect/BoundEquipParams.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/BoundEquipParamsTest.java`

**Interfaces:**
- Consumes: `EffectContext`, `EffectKeys`
- Produces:
  - `Effect.BOUND_EQUIP`
  - `EffectKeys.SLOT` → `EffectKey<String>`
  - `EffectKeys.ITEMS_BY_LEVEL` → `EffectKey<Map<Integer,String>>`
  - `BoundEquipParams.validate(EffectContext params, int upgradeMax)` → `Optional<String>`
  - `BoundEquipParams.itemIdForLevel(EffectContext params, int level)` → `Optional<String>`
  - `BoundEquipParams.previewLevel(Integer ownedLevelOrNull, int upgradeMax)` → `int`
  - `BoundEquipParams.SLOT_CHEST = "CHEST"`

- [ ] **Step 1: Write the failing test**

Create `BoundEquipParamsTest.java`:

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BoundEquipParamsTest {

    private static EffectContext full8() {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.SLOT, "CHEST");
        Map<Integer, String> m = new HashMap<>();
        for (int i = 1; i <= 8; i++) {
            m.put(i, "maggoteers:prot_chest_l" + i);
        }
        p.put(EffectKeys.ITEMS_BY_LEVEL, Map.copyOf(m));
        return p;
    }

    @Test
    void acceptsCompleteMap() {
        assertTrue(BoundEquipParams.validate(full8(), 8).isEmpty());
    }

    @Test
    void rejectsMissingLevel() {
        EffectContext p = full8();
        var map = new HashMap<>(p.get(EffectKeys.ITEMS_BY_LEVEL));
        map.remove(3);
        p.put(EffectKeys.ITEMS_BY_LEVEL, map);
        Optional<String> err = BoundEquipParams.validate(p, 8);
        assertTrue(err.isPresent());
        assertTrue(err.get().contains("3") || err.get().toLowerCase().contains("level"));
    }

    @Test
    void rejectsNonChestSlot() {
        EffectContext p = full8();
        p.put(EffectKeys.SLOT, "HEAD");
        assertTrue(BoundEquipParams.validate(p, 8).isPresent());
    }

    @Test
    void resolveLevel() {
        assertEquals("maggoteers:prot_chest_l4",
                BoundEquipParams.itemIdForLevel(full8(), 4).orElseThrow());
    }

    @Test
    void previewLevelRules() {
        assertEquals(1, BoundEquipParams.previewLevel(null, 8));
        assertEquals(2, BoundEquipParams.previewLevel(1, 8));
        assertEquals(8, BoundEquipParams.previewLevel(7, 8));
        assertEquals(8, BoundEquipParams.previewLevel(8, 8));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
`E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd -f E:/Intellij_Idea/plugins/MCPlugin/pom.xml -Dtest=BoundEquipParamsTest test`

Expected: FAIL (missing class / enum constant).

- [ ] **Step 3: Minimal implementation**

Add to `Effect.java`:
```java
    /** Bound equipment view; params: slot, items_by_level. Permanent only. */
    BOUND_EQUIP,
```

Add to `EffectKeys.java`:
```java
    public static final EffectKey<String> SLOT = new EffectKey<>("slot");
    public static final EffectKey<java.util.Map<Integer, String>> ITEMS_BY_LEVEL =
            new EffectKey<>("items_by_level");
```

In `EffectParamsParser` switch:
```java
                case "slot" -> ctx.put(EffectKeys.SLOT, String.valueOf(v).toUpperCase(Locale.ROOT));
                case "items_by_level" -> ctx.put(EffectKeys.ITEMS_BY_LEVEL,
                        parseItemsByLevel(sec.getConfigurationSection(k)));
```

```java
    private static Map<Integer, String> parseItemsByLevel(ConfigurationSection sec) {
        Map<Integer, String> out = new HashMap<>();
        if (sec == null) return Map.of();
        for (String k : sec.getKeys(false)) {
            try {
                out.put(Integer.parseInt(k), sec.getString(k));
            } catch (NumberFormatException ignored) { }
        }
        return Map.copyOf(out);
    }
```

Create `BoundEquipParams.java`:
```java
package io.mczju.maggoteers.effect;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class BoundEquipParams {
    public static final String SLOT_CHEST = "CHEST";

    private BoundEquipParams() {}

    public static Optional<String> validate(EffectContext params, int upgradeMax) {
        if (params == null) return Optional.of("BOUND_EQUIP missing params");
        String slot = params.get(EffectKeys.SLOT);
        if (slot == null || !SLOT_CHEST.equals(slot.toUpperCase(Locale.ROOT))) {
            return Optional.of("BOUND_EQUIP slot must be CHEST (v1)");
        }
        if (upgradeMax <= 0) return Optional.of("BOUND_EQUIP requires upgrade_max > 0");
        Map<Integer, String> byLevel = params.get(EffectKeys.ITEMS_BY_LEVEL);
        if (byLevel == null || byLevel.isEmpty()) {
            return Optional.of("BOUND_EQUIP missing items_by_level");
        }
        for (int i = 1; i <= upgradeMax; i++) {
            String id = byLevel.get(i);
            if (id == null || id.isBlank()) {
                return Optional.of("BOUND_EQUIP missing items_by_level." + i);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> itemIdForLevel(EffectContext params, int level) {
        if (params == null || level < 1) return Optional.empty();
        Map<Integer, String> byLevel = params.get(EffectKeys.ITEMS_BY_LEVEL);
        if (byLevel == null) return Optional.empty();
        String id = byLevel.get(level);
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    public static int previewLevel(Integer ownedLevelOrNull, int upgradeMax) {
        int max = Math.max(1, upgradeMax);
        if (ownedLevelOrNull == null || ownedLevelOrNull < 1) return 1;
        return Math.min(ownedLevelOrNull + 1, max);
    }
}
```

In `EffectContext.copyDeep()`, copy `ITEMS_BY_LEVEL` with `Map.copyOf` when present (same idea as `POTIONS`).

- [ ] **Step 4: Run tests — expect PASS**

Same mvn command as Step 2.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/effect/Effect.java \
  src/main/java/io/mczju/maggoteers/effect/EffectKeys.java \
  src/main/java/io/mczju/maggoteers/effect/EffectParamsParser.java \
  src/main/java/io/mczju/maggoteers/effect/EffectContext.java \
  src/main/java/io/mczju/maggoteers/effect/BoundEquipParams.java \
  src/test/java/io/mczju/maggoteers/effect/BoundEquipParamsTest.java
git commit -m "feat(effect): add BOUND_EQUIP enum and params parsing"
```

---

### Task 2: RewardLoadValidator (schema + single CHEST)

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardLoadValidator.java`
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardService.java` (load loop uniqueness)
- Modify: `src/test/java/io/mczju/maggoteers/reward/RewardLoadValidationTest.java`

**Interfaces:**
- Consumes: `BoundEquipParams.validate`, `Effect.BOUND_EQUIP`, `Stack.UPGRADE_LEVEL`
- Produces: per-option skip reasons; `chestBoundEquipConflict(List<RewardOption> already, RewardOption candidate)` → `Optional<String>`

- [ ] **Step 1: Write failing tests**

Append tests (adapt `RewardOption` constructor to match existing helpers in this file):

```java
    @Test
    void rejectsBoundEquipWithTrigger() {
        EffectContext p = boundEquipParams(8);
        RewardOption opt = /* STAT BOUND_EQUIP with Trigger.ON_WAVE_CLEAR, UPGRADE_LEVEL, upgradeMax=8 */;
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void rejectsBoundEquipWithoutUpgradeStack() {
        EffectContext p = boundEquipParams(8);
        RewardOption opt = /* BOUND_EQUIP with Stack.IGNORE */;
        assertTrue(RewardLoadValidator.validateStatOption(opt).isPresent());
    }

    @Test
    void acceptsBoundEquipOk() {
        EffectContext p = boundEquipParams(8);
        RewardOption opt = /* BOUND_EQUIP UPGRADE_LEVEL 8, no trigger */;
        assertTrue(RewardLoadValidator.validateStatOption(opt).isEmpty());
    }

    @Test
    void secondChestBoundEquipRejectedByUniquenessHelper() {
        RewardOption a = /* id a */;
        RewardOption b = /* id b */;
        assertTrue(RewardLoadValidator.chestBoundEquipConflict(List.of(a), b).isPresent());
        assertTrue(RewardLoadValidator.chestBoundEquipConflict(List.of(), a).isEmpty());
    }

    private static EffectContext boundEquipParams(int max) {
        EffectContext p = new EffectContext();
        p.put(EffectKeys.SLOT, "CHEST");
        Map<Integer, String> m = new HashMap<>();
        for (int i = 1; i <= max; i++) m.put(i, "maggoteers:prot_chest_l" + i);
        p.put(EffectKeys.ITEMS_BY_LEVEL, m);
        return p;
    }
```

- [ ] **Step 2: Run — expect FAIL**

`mvn -Dtest=RewardLoadValidationTest test`

- [ ] **Step 3: Implement**

In `validateStatOption` (mirror AURA permanent rules):

```java
        if (opt.effect() == Effect.BOUND_EQUIP) {
            if (fire != null || opt.expiryTrigger() != null) {
                return Optional.of("BOUND_EQUIP reward must not have trigger or expiry");
            }
            if (opt.stack() != Stack.UPGRADE_LEVEL) {
                return Optional.of("BOUND_EQUIP requires stack UPGRADE_LEVEL");
            }
            int upgradeMax = opt.upgradeMax();
            if (upgradeMax <= 0) {
                return Optional.of("BOUND_EQUIP requires upgrade_max > 0");
            }
            return BoundEquipParams.validate(opt.params(), upgradeMax)
                    .map(msg -> "BOUND_EQUIP " + msg);
        }
```

```java
    public static Optional<String> chestBoundEquipConflict(
            List<RewardOption> alreadyAccepted, RewardOption candidate) {
        if (candidate == null || candidate.effect() != Effect.BOUND_EQUIP) return Optional.empty();
        EffectContext params = candidate.params();
        String slot = params == null ? null : params.get(EffectKeys.SLOT);
        if (slot == null || !BoundEquipParams.SLOT_CHEST.equalsIgnoreCase(slot)) {
            return Optional.empty();
        }
        boolean exists = alreadyAccepted.stream().anyMatch(o ->
                o.effect() == Effect.BOUND_EQUIP
                        && o.params() != null
                        && BoundEquipParams.SLOT_CHEST.equalsIgnoreCase(
                                String.valueOf(o.params().get(EffectKeys.SLOT))));
        return exists
                ? Optional.of("duplicate BOUND_EQUIP slot CHEST: " + candidate.id())
                : Optional.empty();
    }
```

In `RewardService` load: keep `List<RewardOption> acceptedChestBound`; after per-option `validateStatOption` passes, call `chestBoundEquipConflict` and skip+warn on conflict.

Also validate ItemService.hasItem for each items_by_level value when plugin instance is available (same style as SUMMON entity check).

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/mczju/maggoteers/reward/RewardLoadValidator.java \
  src/main/java/io/mczju/maggoteers/reward/RewardService.java \
  src/test/java/io/mczju/maggoteers/reward/RewardLoadValidationTest.java
git commit -m "feat(rewards): validate BOUND_EQUIP schema and single CHEST binding"
```

---

### Task 3: PDC tags + BoundEquipService

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemKind.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/RunItemTags.java`
- Modify: `src/main/java/io/mczju/maggoteers/item/ItemService.java` (treat `BOUND_EQUIP` like `COLLECTIBLE`/`RUN_GEAR` for interact — no use_ability)
- Create: `src/main/java/io/mczju/maggoteers/effect/BoundEquipService.java`
- Create: `src/test/java/io/mczju/maggoteers/effect/BoundEquipServiceLogicTest.java`

**Interfaces:**
- Consumes: `BoundEquipParams`, `ItemService.createItem`, `PlayerEffect`, `PlayerStateManager`
- Produces:
  - `ItemKind.BOUND_EQUIP`
  - `RunItemTags.setBoundEquipPdc(ItemMeta, String rewardId, int level)`
  - `RunItemTags.isBoundEquip(ItemStack)` / `isBoundEquipFor(ItemStack, String)`
  - `BoundEquipService.sync(Player, PlayerEffect)`
  - `BoundEquipService.resync(Player, MaggoteersGame)`
  - `BoundEquipService.remove(Player, String rewardId)`
  - `BoundEquipService.isConflictToDestroy(boolean occupied, boolean sameRewardBound)` → `boolean`

- [ ] **Step 1: Failing pure test**

```java
package io.mczju.maggoteers.effect;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundEquipServiceLogicTest {
    @Test
    void conflictDestroyRules() {
        assertTrue(BoundEquipService.isConflictToDestroy(true, false));
        assertFalse(BoundEquipService.isConflictToDestroy(true, true));
        assertFalse(BoundEquipService.isConflictToDestroy(false, false));
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Implement**

`ItemKind`:
```java
    BOUND_EQUIP("bound_equip"),
```

`RunItemTags`:
```java
    public static void setBoundEquipPdc(ItemMeta meta, String rewardId, int level) {
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(MaggoteersPlugin.getInstance(), "kind"),
                PersistentDataType.STRING, ItemKind.BOUND_EQUIP.pdcValue());
        pdc.set(rewardIdKey(), PersistentDataType.STRING, rewardId);
        pdc.set(rewardLevelKey(), PersistentDataType.INTEGER, level);
    }

    public static boolean isBoundEquip(ItemStack stack) {
        return ItemService.readKind(stack) == ItemKind.BOUND_EQUIP;
    }

    public static boolean isBoundEquipFor(ItemStack stack, String rewardId) {
        return isBoundEquip(stack) && readRewardId(stack).filter(rewardId::equals).isPresent();
    }
```

`BoundEquipService` behavior:
1. `sync`: if chest occupied and not `isBoundEquipFor(chest, pe.id())` → `setChestplate(null)` (destroy). Remove inventory orphans for `pe.id()`. Create item from `itemIdForLevel`, stamp PDC, `setChestplate`.
2. `resync`: for each `BOUND_EQUIP` in state → `sync`; then strip any `bound_equip` whose `reward_id` not in keep set (chest + inventory).
3. `remove(rewardId)`: clear matching chest + inventory stacks.

```java
    static boolean isConflictToDestroy(boolean occupied, boolean sameRewardBound) {
        return occupied && !sameRewardBound;
    }
```

- [ ] **Step 4: Run targeted tests PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(effect): BoundEquipService sync/resync/remove with PDC tags"
```

---

### Task 4: Wire EffectService apply/resync/remove (not applyDerived)

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/effect/EffectService.java`
- Modify: `src/main/java/io/mczju/maggoteers/game/MaggoteersDeathStrategy.java` (if it calls Collectible resync directly — add BoundEquip)
- Modify: `src/main/java/io/mczju/maggoteers/state/PlayerStateManager.java` (same)

**Interfaces:**
- After merge in `apply`, if merged effect is `BOUND_EQUIP`, call `BoundEquipService.sync(p, mergedPe)`.
- `resync(Player)` calls `BoundEquipService.resync` alongside collectibles.
- On removed effect ids (collectible remove hooks), also `BoundEquipService.remove`.

- [ ] **Step 1: Patch `EffectService.apply`**

Do **not** add `BOUND_EQUIP` to `applyDerived`. After merge:

```java
        PlayerEffect mergedPe = st.effects().stream()
                .filter(e -> e.id().equals(incoming.id())).findFirst().orElse(null);
        if (mergedPe != null && mergedPe.effect() == Effect.BOUND_EQUIP) {
            BoundEquipService.sync(p, mergedPe);
        } else if (incoming.effect() == Effect.AURA && incoming.fireTrigger() == null) {
            AuraService.refresh(game);
        } else if (incoming.isPermanent() && incoming.effect() == Effect.ADD_ATTRIBUTE) {
            resyncDerived(p, st);
        } else if (incoming.isPermanent() && incoming.effect() != Effect.AURA) {
            applyDerived(p, incoming);
        }
```

- [ ] **Step 2: Patch `resync` and remove hooks**

```java
            CollectibleService.resync(p, game);
            BoundEquipService.resync(p, game);
```

Wherever `CollectibleService.remove(p, removedId)` runs after sweep, also:
```java
            BoundEquipService.remove(p, removedId);
```

- [ ] **Step 3: Compile/test**

`mvn -Dtest=BoundEquipParamsTest,RewardLoadValidationTest,BoundEquipServiceLogicTest test`

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(effect): wire BoundEquipService into apply/resync/remove paths"
```

---

### Task 5: Slot lock listener (spec §5.3)

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/listener/BoundEquipLockListener.java`
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`

**Interfaces:**
- Cancel in-run actions that move `kind=bound_equip` or change the chest armor slot holding it.
- Keep `RunItemGuardListener` for drop/foreign-inventory (bound pieces remain `isRunItem`).

- [ ] **Step 1: Implement `BoundEquipLockListener`**

Handle at least:
- `InventoryClickEvent`: `PICKUP_*`, `PLACE_*`, `SWAP_WITH_CURSOR`, `HOTBAR_SWAP`, `HOTBAR_MOVE_AND_READD`, `MOVE_TO_OTHER_INVENTORY`, drop-from-slot actions when current/cursor is bound or slot is chest armor.
- `InventoryDragEvent`: cancel if new items include bound stacks or raw slots include chest armor slot.

Detect chest slot via `InventoryType.SlotType.ARMOR` + `EquipmentSlot.CHEST` / raw slot comment verified for Paper 26 player inventory view.

Allow open inventory / hover (no cancel when action does not move the piece).

- [ ] **Step 2: Register in `onEnable`** next to `RunItemGuardListener`.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(item): lock bound chestplate against unequip and inventory moves"
```

---

### Task 6: GUI preview + content YAML + docs

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/reward/RewardOptionIcons.java`
- Modify: `src/main/resources/items/maggoteers.yml`
- Modify: `src/main/resources/rewards.yml`
- Modify: `.cursor/skills/maggoteers-plugin-config/reference.md`
- Modify: `docs/dev-log.md`

**Interfaces:**
- STAT branch: if `opt.effect() == BOUND_EQUIP`, preview via `BoundEquipParams.previewLevel` + `ItemService.createItem` (never only `CollectibleService.preview`).

- [ ] **Step 1: GUI branch**

```java
            case STAT -> {
                if (opt.effect() == Effect.BOUND_EQUIP) {
                    yield boundEquipPreview(opt, viewer, game).orElse(fallbackPaper(opt));
                }
                // existing CollectibleService.preview path
            }
```

```java
    private static Optional<ItemStack> boundEquipPreview(
            RewardOption opt, Player viewer, MaggoteersGame game) {
        int owned = 0;
        int upgradeMax = Math.max(1, opt.upgradeMax());
        if (viewer != null && game != null) {
            PlayerState ps = PlayerStateManager.get(game, viewer.getUniqueId());
            if (ps != null) {
                owned = ps.effects().stream().filter(e -> e.id().equals(opt.id()))
                        .mapToInt(PlayerEffect::level).max().orElse(0);
            }
        }
        Integer ownedOrNull = owned > 0 ? owned : null;
        int level = BoundEquipParams.previewLevel(ownedOrNull, upgradeMax);
        return BoundEquipParams.itemIdForLevel(opt.params(), level)
                .flatMap(id -> ItemService.createItem(id, 1));
    }
```

- [ ] **Step 2: Eight items** (`maggoteers:prot_chest_l1` … `l8`)

| id suffix | material | protection | armor | toughness |
|-----------|----------|------------|-------|-----------|
| l1 | LEATHER_CHESTPLATE | 2 | -3 | — |
| l2 | GOLDEN_CHESTPLATE | 4 | -5 | — |
| l3 | GOLDEN_CHESTPLATE | 6 | -5 | — |
| l4 | IRON_CHESTPLATE | 8 | -6 | — |
| l5 | IRON_CHESTPLATE | 10 | -6 | — |
| l6 | DIAMOND_CHESTPLATE | 12 | -8 | -2 |
| l7 | DIAMOND_CHESTPLATE | 14 | -8 | -2 |
| l8 | NETHERITE_CHESTPLATE | 16 | -8 | -1 |

Each: `unbreakable: true`; enchants `minecraft:protection: N` + `minecraft:unbreaking: 10`; modifiers `ADD_NUMBER` slot `chest`.

- [ ] **Step 3: `rewards.yml` STAT** (pick pool at implement time; document in commit — e.g. `act1_strong` or `act1_boss`)

```yaml
      - id: prot_chest
        display: "<aqua>保护胸甲"
        description: "<gray>绑定胸甲：保护 II→XVI（每升 1 阶 +2），全程 0 护甲/韧性"
        category: STAT
        effect: BOUND_EQUIP
        params:
          slot: CHEST
          items_by_level:
            1: maggoteers:prot_chest_l1
            2: maggoteers:prot_chest_l2
            3: maggoteers:prot_chest_l3
            4: maggoteers:prot_chest_l4
            5: maggoteers:prot_chest_l5
            6: maggoteers:prot_chest_l6
            7: maggoteers:prot_chest_l7
            8: maggoteers:prot_chest_l8
        unique: true
        stack: UPGRADE_LEVEL
        upgrade_max: 8
```

No `collectibles.yml` entry. Note in reference: `unique: true` does not block UPGRADE redraws.

- [ ] **Step 4: Docs** — reference.md Effect row; `docs/dev-log.md` entry.

- [ ] **Step 5: Full test + commit**

```bash
E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd -f E:/Intellij_Idea/plugins/MCPlugin/pom.xml test
git commit -m "feat(content): prot_chest BOUND_EQUIP reward and GUI preview"
```

---

### Task 7: In-game smoke (deploy)

**Files:** none (ops)

- [ ] **Step 1:** `.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SyncAssets`

- [ ] **Step 2:** Restart Paper at `E:/MCpaper`. Join Maggoteers and obtain/upgrade `prot_chest`.

- [ ] **Step 3: Checklist**
  - First grant: leather Prot II, 0 armor, unbreakable + Unbreaking 10
  - Existing chest destroyed
  - Cannot unequip / hotbar swap / drag
  - Upgrade to L2: gold Prot IV
  - Death/revive restores chest
  - GUI shows next-level preview
  - At L8 option leaves pool

- [ ] **Step 4:** Log issues in `docs/dev-log.md` if any; fix forward.

---

## Spec coverage (self-review)

| Spec requirement | Task |
|------------------|------|
| BOUND_EQUIP enum + params | T1 |
| Progression / toughness cancels in items | T6 |
| Load validate + single CHEST | T2 |
| sync destroy conflict + equip | T3 |
| Explicit EffectService wiring (not applyDerived) | T4 |
| Lock intercepts §5.3 | T5 |
| resync + orphan strip + remove | T3–T4 |
| GUI preview without collectibles | T6 |
| unique vs UPGRADE_LEVEL note | T6 docs |
| No other-chestplate mutex | out of scope (honored) |
| Smoke | T7 |

## Consistency check

- Names: `BoundEquipParams.validate` / `itemIdForLevel` / `previewLevel`; `BoundEquipService.sync` / `resync` / `remove` / `isConflictToDestroy`.
- PDC kind: `bound_equip` via `ItemKind.BOUND_EQUIP`.
- No TBD placeholders left.
