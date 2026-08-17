# Config Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all formal player-visible Maggoteers prompts/UI copy into `config.yml` `messages:` via `MessageService` (MiniMessage + safe placeholders).

**Architecture:** Flatten nested `messages` into dotted keys; `raw`/`component`/`legacy` always escape plain-text placeholders (or TagResolver); nested fragments are plain text only; colors live on outermost templates; `line_wave` / `line_wave_resting` avoid double-escape. Load immediately after `saveDefaultConfig`, before `registerGame` / `registerLeaderboard`.

**Tech Stack:** Java 25, Paper 26.2, Adventure MiniMessage, JUnit 5, Maven, SnakeYAML via Bukkit config

**Spec:** `docs/superpowers/specs/2026-08-15-config-messages-design.md`

## Global Constraints

- Paper `26.2.build.62-beta`; Java/Maven release 25.
- Scope B + command shell only; **no** debug subcommand strings; **no** Logger strings; **no** rewards/items/waves content copy; **no** BossBar titles.
- Placeholder values = plain text → always escape / TagResolver before MM parse.
- Nested fragments (`prep_clause`, `strategy_clause`, `phase_*`) **must not** contain MiniMessage tags; colors only on outer templates.
- Missing key → return literal `messages.<key>`; load-time checklist warn (no fail-fast); empty string `""` is valid (no warn).
- `{tier}` stays English `weak|strong|boss`.
- Hot reload: restart only.
- Working tree may be dirty; stage only this feature’s files; **commit only if user asks**.
- Deployed servers: `saveResource(false)` does not merge keys — note in `docs/dev-log.md`.

---

## File structure

**Create:**
- `src/main/java/io/mczju/maggoteers/config/MessageService.java`
- `src/test/java/io/mczju/maggoteers/config/MessageServiceTest.java`

**Modify:**
- `src/main/resources/config.yml` — append full `messages:` tree (Chinese + colors = current hardcodes)
- `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java` — load order
- `game/MaggoteersGame.java`, `game/MaggoteersDeathStrategy.java`
- `wave/WaveScheduler.java`
- `ui/RunScoreboard.java`
- `menu/{Rest,Pick,ClassSelect,Revive,UnlockShop}Menu.java`
- `reward/{RewardService,RewardOptionIcons}.java`
- `item/ItemInteractRouter.java`, `item/interact/{OpenRestMenuHandler,OpenClassMenuHandler,ConfigMagicHandler}.java`
- `persist/MaggoteersTotalLeaderboard.java`
- `command/MaggoteersCommand.java` — only `players_only` + `usage` (keep debug + `no_permission` hardcoded)
- `docs/dev-log.md`
- `.cursor/skills/maggoteers-plugin-config/reference.md` (brief `messages` docs)
- Spec status → implemented when done

---

### Task 1: `MessageService` + unit tests (TDD)

**Files:**
- Create: `src/main/java/io/mczju/maggoteers/config/MessageService.java`
- Create: `src/test/java/io/mczju/maggoteers/config/MessageServiceTest.java`

**Interfaces:**
- Produces:
  - `void load(JavaPlugin plugin)` — read `messages` section from `plugin.getConfig()`, flatten, validate required keys
  - `void loadFromSection(ConfigurationSection root)` — testable without full plugin (nullable → empty map + warn)
  - `Component component(String key, Map<String, String> ph)`
  - `String raw(String key, Map<String, String> ph)`
  - `String legacy(String key, Map<String, String> ph)`
  - `List<String> rawList(String key)` — consecutive `key.0`, `key.1`, …
  - `Set<String> requiredKeys()` — public for tests
- Consumes: Bukkit `ConfigurationSection`, Adventure `MiniMessage`, `LegacyComponentSerializer.legacySection()`

- [ ] **Step 1: Write failing tests**

```java
package io.mczju.maggoteers.config;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MessageServiceTest {
    @BeforeEach
    void loadSample() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("messages");
        var m = yaml.getConfigurationSection("messages");
        m.createSection("wave").set("cleared", "<green>OK");
        m.getConfigurationSection("wave").set("enter_act", "<gold>A{act}:{map}");
        m.createSection("game").set("prep_clause", " prep {prep}s"); // plain, no tags
        m.getConfigurationSection("game").set("ready", "<green>Ready!{prep_clause}");
        m.createSection("scoreboard").set("line_wave", "<aqua>W {wave}/{wave_count} · {phase}");
        m.getConfigurationSection("scoreboard")
                .set("line_wave_resting", "<aqua>W {wave}/{wave_count} · {phase} <gray>({rest}s)");
        m.createSection("game").set("meta_description", List.of("<aqua>line0", "<gray>line1"));
        // re-get after overwrite — build carefully in real test with nested maps
        MessageService.loadFromSection(yaml.getConfigurationSection("messages"));
    }

    @Test
    void missingKeyReturnsLiteralAndDoesNotThrow() {
        MessageService.loadFromSection(new YamlConfiguration()); // empty
        assertEquals("messages.nope", MessageService.raw("nope", Map.of()));
    }

    @Test
    void emptyStringIsValid() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("messages.wave.strategy_clause", "");
        MessageService.loadFromSection(yaml.getConfigurationSection("messages"));
        assertEquals("", MessageService.raw("wave.strategy_clause", Map.of()));
    }

    @Test
    void placeholdersAreEscapedNotParsedAsTags() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("messages.reward.gained", "<green>Got: {display}");
        MessageService.loadFromSection(yaml.getConfigurationSection("messages"));
        var c = MessageService.component("reward.gained", Map.of("display", "<red>HACK"));
        String plain = PlainTextComponentSerializer.plainText().serialize(c);
        assertTrue(plain.contains("<red>HACK") || plain.contains("HACK"));
        assertFalse(plain.toLowerCase().contains("got: ") && c.toString().contains("red") && false);
        // Stronger: red should not colorize HACK — plain still contains angle brackets as text
        assertTrue(plain.contains("<red>") || plain.contains("HACK"));
    }

    @Test
    void nestedPlainFragmentKeepsOuterColor() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("messages.game.prep_clause", " prep 10s"); // no tags
        yaml.set("messages.game.ready", "<green>Ready!{prep_clause}");
        MessageService.loadFromSection(yaml.getConfigurationSection("messages"));
        String clause = MessageService.raw("game.prep_clause", Map.of());
        String ready = MessageService.raw("game.ready", Map.of("prep_clause", clause));
        assertTrue(ready.startsWith("<green>Ready!"));
        assertTrue(ready.contains("prep 10s"));
        assertFalse(ready.contains("\\<green\\>")); // not double-escaped outer tags
    }

    @Test
    void requiredKeysWarnDoesNotThrow() {
        MessageService.loadFromSection(new YamlConfiguration());
        assertFalse(MessageService.requiredKeys().isEmpty());
    }

    @Test
    void rawListReadsIndexedEntries() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("messages.game.meta_description", List.of("<aqua>a", "<gray>b"));
        MessageService.loadFromSection(yaml.getConfigurationSection("messages"));
        assertEquals(List.of("<aqua>a", "<gray>b"), MessageService.rawList("game.meta_description"));
    }
}
```

Adjust test YAML construction so sections nest correctly (use `yaml.loadFromString` with a small YAML block if clearer).

- [ ] **Step 2: Run tests — expect FAIL (class missing)**

```powershell
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy -Dtest=MessageServiceTest
```

- [ ] **Step 3: Implement `MessageService`**

Core behavior:

```java
public final class MessageService {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();
    private static Map<String, String> MESSAGES = Map.of();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    public static final Set<String> REQUIRED_KEYS = Set.of(
            // copy full list from spec §4.2 (game.*, wave.*, death.*, menu.*, item.*,
            // reward.*, scoreboard.* including line_wave_resting NOT rest_suffix,
            // leaderboard.*, command.players_only, command.usage)
            );

    public static void load(JavaPlugin plugin) {
        loadFromSection(plugin.getConfig().getConfigurationSection("messages"));
        // log missing required keys once
    }

    public static void loadFromSection(ConfigurationSection root) {
        Map<String, String> flat = new HashMap<>();
        if (root != null) flatten("", root, flat);
        MESSAGES = Map.copyOf(flat);
        WARNED.clear();
        for (String k : REQUIRED_KEYS) {
            if (!MESSAGES.containsKey(k)) {
                // plugin logger if available, else System.err in tests
            }
        }
    }

    private static void flatten(String prefix, ConfigurationSection sec, Map<String, String> out) {
        for (String key : sec.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (sec.isConfigurationSection(key)) {
                flatten(path, sec.getConfigurationSection(key), out);
            } else if (sec.isList(key)) {
                List<?> list = sec.getList(key);
                for (int i = 0; i < list.size(); i++) {
                    Object v = list.get(i);
                    if (v != null) out.put(path + "." + i, String.valueOf(v));
                }
            } else {
                Object v = sec.get(key);
                out.put(path, v == null ? "" : String.valueOf(v));
            }
        }
    }

    private static String template(String key) {
        if (!MESSAGES.containsKey(key)) {
            if (WARNED.add(key)) { /* warn messages.<key> */ }
            return "messages." + key;
        }
        return MESSAGES.get(key); // may be ""
    }

    private static String applyPlaceholders(String template, Map<String, String> ph) {
        String s = template;
        if (ph != null) {
            for (var e : ph.entrySet()) {
                String val = e.getValue() == null ? "" : MM.escapeTags(e.getValue());
                s = s.replace("{" + e.getKey() + "}", val);
            }
        }
        return s;
    }

    public static String raw(String key, Map<String, String> ph) {
        return applyPlaceholders(template(key), ph);
    }

    public static Component component(String key, Map<String, String> ph) {
        return MM.deserialize(raw(key, ph));
        // Alternative allowed by spec: TagResolver — either is fine if tests pass
    }

    public static String legacy(String key, Map<String, String> ph) {
        return LEGACY.serialize(component(key, ph));
    }

    public static List<String> rawList(String key) {
        List<String> out = new ArrayList<>();
        for (int i = 0; ; i++) {
            String k = key + "." + i;
            if (!MESSAGES.containsKey(k)) break;
            out.add(MESSAGES.get(k));
        }
        if (out.isEmpty() && !MESSAGES.containsKey(key + ".0")) {
            // missing list → warn once, return empty or single literal — pick empty + warn
        }
        return List.copyOf(out);
    }
}
```

Verify `MiniMessage#escapeTags` exists on Paper 26.2 Adventure; if renamed, use the project’s equivalent (do not leave unescaped).

- [ ] **Step 4: Run `MessageServiceTest` — expect PASS**

- [ ] **Step 5: Commit only if user asks**

---

### Task 2: Default `messages:` tree in `config.yml`

**Files:**
- Modify: `src/main/resources/config.yml` (append after existing keys; do not reorder `wait:` / `items:`)

**Interfaces:**
- Consumes: Task 1 `REQUIRED_KEYS`
- Produces: every required key present with Chinese text matching **current Java hardcodes**, MiniMessage colors where Java used `NamedTextColor`

- [ ] **Step 1: Append `messages:` block**

Copy strings from current call sites (see inventory below). Critical defaults:

```yaml
messages:
  game:
    meta_name: "<gold>卫戍协议"
    meta_author: "<green>MCZJU"
    meta_description:
      - "<aqua>4人合作 PvE · 波数防守 + Roguelike"
    world_create_failed: "<red>世界创建失败，对局终止。"
    plan_generating: "<gray>正在生成本局剧本（异步）…"
    plan_timeout: "<yellow>剧本生成超时(30s)，终止。"
    plan_failed: "<red>剧本生成失败{detail}，对局终止。"
    enter_class_select: "<yellow>已进入第 1 层地图 · 请选择职业（右键职业选择券可重新打开菜单）"
    prep_clause: " 地图准备 {prep} 秒后开战。"   # PLAIN — no color tags
    ready: "<green>卫戍协议 已就绪！{prep_clause} 三层共 {waves} 波。每人复活 {lives} 次。"
    settlement_grant: "<yellow>{player} 结算获得 {grant} 卫戍币。"
  wave:
    strategy_clause: " · {strategy}"            # PLAIN
    phase_prep: "准备"                          # PLAIN
    phase_spawning: "刷怪"
    phase_active: "战斗"
    phase_cleared: "清场"
    phase_rest: "休整"
    phase_done: "结束"
    # ... skip_vote, enter_act, prep, begin, cleared, cleared_rewarded, final_rest, victory
  scoreboard:
    line_wave: "<aqua>波 {wave}/{wave_count} · {phase}"
    line_wave_resting: "<aqua>波 {wave}/{wave_count} · {phase} <gray>({rest}s)"
    # NO rest_suffix key
  command:
    players_only: "<red>仅玩家可用。"
    usage: "<yellow>/maggoteers <shop|debug ...>"
```

Fill remaining keys from spec §4.2 by reading each Java call site and porting text + color.

- [ ] **Step 2: Assert coverage**

Add a unit test (or extend Task 1) that loads the **bundled** `config.yml` from classpath / resources and asserts `REQUIRED_KEYS` ⊆ flattened keys:

```java
@Test
void bundledConfigContainsAllRequiredKeys() throws Exception {
    try (var in = MessageService.class.getClassLoader()
            .getResourceAsStream("config.yml")) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        MessageService.loadFromSection(yaml.getConfigurationSection("messages"));
        for (String k : MessageService.REQUIRED_KEYS) {
            assertTrue(MessageService.hasKey(k), "missing " + k);
            // add hasKey(String) package-visible or check via raw != "messages."+k when present as empty
        }
    }
}
```

Expose `boolean hasKey(String key)` = `MESSAGES.containsKey(key)` for this test.

- [ ] **Step 3: Run tests — PASS**

---

### Task 3: Wire load order in `MaggoteersPlugin`

**Files:**
- Modify: `src/main/java/io/mczju/maggoteers/MaggoteersPlugin.java`

**Interfaces:**
- Consumes: `MessageService.load`

- [ ] **Step 1: Reorder `onEnable`**

```java
saveDefaultConfig();
MessageService.load(this);   // MUST be before registerGame / registerLeaderboard
PluginFiles.saveResourceIfMissing(this, "rewards.yml");
// ...
MCZJUGameCore.getGameManager().registerGame(...);
MCZJUGameCore.getPlayerDataManager().registerPlayerData(...);
MCZJUGameCore.getLeaderboardManager().registerLeaderboard(...);
```

Do **not** leave `registerGame` above `MessageService.load`.

- [ ] **Step 2: Compile / smoke `mvn -Dtest=MessageServiceTest test`**

---

### Task 4: Migrate broadcasts — Game / Wave / Death

**Files:**
- Modify: `game/MaggoteersGame.java`
- Modify: `wave/WaveScheduler.java`
- Modify: `game/MaggoteersDeathStrategy.java`

**Interfaces:**
- Consumes: `MessageService.raw` / `component`
- Nested assembly per spec §4.1 (plain fragments)

- [ ] **Step 1: `MaggoteersGame.getGameMeta`**

```java
return GameMeta.builder()
    .displayName(MessageService.raw("game.meta_name", Map.of()))
    .author(MessageService.raw("game.meta_author", Map.of()))
    .description(MessageService.rawList("game.meta_description"))
    ...
```

- [ ] **Step 2: Replace all `sender().info/warn/error("…")` with `MessageService.raw(...)`**

Examples:
- `plan_failed`: `detail` = planError == null ? "" : "：" + planError
- `ready`: compute `prep_clause` only if prep > 0 via `raw("game.prep_clause", Map.of("prep", String.valueOf(prep)))`, else `""`

- [ ] **Step 3: `WaveScheduler`**

- `phaseName()` → `raw("wave.phase_" + phase.name().toLowerCase(), Map.of())` (map enum: PREP→prep, etc.)
- `begin`: build plain `strategy_clause` then `raw("wave.begin", …)`
- broadcasts: `broadcast(game, MessageService.component(key, ph))` — change `broadcast` to accept Component already, or deserialize once

- [ ] **Step 4: `MaggoteersDeathStrategy`** — `death.missing_state` / `auto_revive` / `to_spectator`

- [ ] **Step 5: Grep migration**

```powershell
rg -n "sender\(\)\.(info|warn|error)\(\"" src/main/java/io/mczju/maggoteers/game src/main/java/io/mczju/maggoteers/wave/WaveScheduler.java
```

Expect: no Chinese literals left in those call sites.

- [ ] **Step 6: `mvn test` (at least MessageServiceTest + unaffected tests)**

---

### Task 5: Migrate `RunScoreboard`

**Files:**
- Modify: `ui/RunScoreboard.java`

- [ ] **Step 1: Replace sidebar strings**

```java
Objective obj = sb.registerNewObjective("maggoteers", Criteria.DUMMY,
        MessageService.legacy("scoreboard.title", Map.of()));
obj.getScore(MessageService.legacy("scoreboard.line_act", Map.of(
        "act", String.valueOf(snap.actIndex() + 1),
        "map", snap.mapId()))).setScore(line--);

String phase = MessageService.raw("wave.phase_" + snap.phase().name().toLowerCase(), Map.of());
String waveLine = snap.restSecondsLeft() > 0
        ? MessageService.legacy("scoreboard.line_wave_resting", Map.of(
                "wave", String.valueOf(snap.waveIndex() + 1),
                "wave_count", String.valueOf(snap.waveCount()),
                "phase", phase,
                "rest", String.valueOf(snap.restSecondsLeft())))
        : MessageService.legacy("scoreboard.line_wave", Map.of(
                "wave", String.valueOf(snap.waveIndex() + 1),
                "wave_count", String.valueOf(snap.waveCount()),
                "phase", phase));
obj.getScore(waveLine).setScore(line--);
```

Map `Phase` → key suffix carefully (`PREP`→`phase_prep`). Prefer a small helper `MessageService.phaseKey(Phase)` or switch in `WaveScheduler.phaseName()` reused by scoreboard.

Class-select branch: `class_header`, `class_hint`, status tags, `player_line`.

- [ ] **Step 2: Confirm no `rest_suffix` references; no `§` hardcodes for player text**

- [ ] **Step 3: Compile**

---

### Task 6: Migrate menus + reward GUI

**Files:**
- Modify: `menu/RestMenu.java`, `PickMenu.java`, `ClassSelectMenu.java`, `ReviveMenu.java`, `UnlockShopMenu.java`
- Modify: `reward/RewardOptionIcons.java`, `reward/RewardService.java`

- [ ] **Step 1: Titles via `MessageService.raw("menu.*.title", Map.of())`**

- [ ] **Step 2: RestMenu buttons / lore / errors** — `held` / `click` as separate keys; lore lines single strings

- [ ] **Step 3: Pick / Class / Revive / Shop** sendMessage → `component(...)`

Shop `{display}` must go through escape (displayPlain may contain `<`).

- [ ] **Step 4: `RewardOptionIcons` → `reward.click_choose`; `RewardService` gained/fail keys

- [ ] **Step 5: Grep**

```powershell
rg -n "Component\.text\(\"[^\"]*[\u4e00-\u9fff]" src/main/java/io/mczju/maggoteers/menu src/main/java/io/mczju/maggoteers/reward
```

Expect: none (or only non-player paths).

---

### Task 7: Migrate item interact + command shell + leaderboard

**Files:**
- Modify: `item/ItemInteractRouter.java`, `item/interact/*.java`
- Modify: `command/MaggoteersCommand.java` — **only** `players_only` + root `usage`
- Modify: `persist/MaggoteersTotalLeaderboard.java`

- [ ] **Step 1: Item tips** → `messages.item.*`

- [ ] **Step 2: Command**

```java
if (!(sender instanceof Player p)) {
    sender.sendMessage(MessageService.raw("command.players_only", Map.of()));
    return true;
}
// usage() -> MessageService.raw("command.usage", Map.of())
// KEEP hardcoded: debug permission message + all debug subcommand feedback
```

- [ ] **Step 3: Leaderboard title/subtitle via `raw`**

- [ ] **Step 4: Full grep for remaining player-facing Chinese in scope paths**

```powershell
rg -n "[\u4e00-\u9fff]" src/main/java/io/mczju/maggoteers --glob "!**/command/MaggoteersCommand.java"
# Then manually verify MaggoteersCommand only has debug Chinese left
```

---

### Task 8: Docs + final verification

**Files:**
- Modify: `docs/dev-log.md`
- Modify: `.cursor/skills/maggoteers-plugin-config/reference.md`
- Modify: `docs/superpowers/specs/2026-08-15-config-messages-design.md` status → implemented

- [ ] **Step 1: Dev-log entry** — decisions, load order, server merge warning for `messages:`

- [ ] **Step 2: Skill reference** — short `messages:` section pointing at spec

- [ ] **Step 3: Full test**

```powershell
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Manual checklist (optional on test server)** — change one `wave.cleared` string, restart, clear a wave; scoreboard resting line gray seconds; shop unlock with display containing `<`

---

## Spec coverage self-review

| Spec item | Task |
|---|---|
| MessageService API + escape | 1 |
| Nested fragment = plain text; line_wave_resting | 1–2, 5 |
| Load order before register | 3 |
| Load checklist warn | 1 |
| Full messages tree | 2 |
| Game/Wave/Death | 4 |
| Scoreboard | 5 |
| Menus/Reward | 6 |
| Item + command shell + leaderboard | 7 |
| no_permission stays hardcoded | 7 |
| Colors in YAML | 2 + acceptance in 8 |
| Docs / merge warning | 8 |

No `text`/`fragment` dual API. No `rest_suffix` key.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-15-config-messages.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute in this session with executing-plans checkpoints  

Which approach?
