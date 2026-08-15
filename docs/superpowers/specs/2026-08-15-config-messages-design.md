# 正式可见提示文案外置到 config.yml

**日期：** 2026-08-15  
**状态：** 已实现  
**范围：** Maggoteers 正式游戏中玩家可见的系统提示 / GUI / 记分板 / 局外商店与排行榜 / GameMeta / `/maggoteers` 非 debug 命令壳  
**非范围：** `/maggoteers debug …` 子命令反馈、服务端 Logger、内容 YAML（rewards/items/waves/affixes/collectibles）、Boss 血条标题（怪 `name:`）、`plugin.yml` 权限描述

## 1. 目标

所有**正式游戏时玩家能看到**的提示与界面文案，统一由 `config.yml` 的 `messages:` 树管理。

## 2. 已确定决策

| 项 | 决策 |
|---|---|
| 范围 | **B + 命令壳**：局内系统提示 + 局外商店/排行榜/GameMeta + `/maggoteers` 非 debug（`messages.command.*`）；**不含** debug 子命令 |
| 格式 | MiniMessage + `{snake_case}` 占位 |
| 占位安全 | 动态**纯文本**占位值一律 escape / TagResolver（见 §3.1）。嵌套片段不走「已渲染 MM 再 escape」；见 §4.1 |
| 条件片段 | **仅 Java 组装**；Service **不做** key 嵌套解析。**嵌套片段禁止含 MiniMessage 标签**（颜色/样式只写最外层模板）；见 §3.1 / §4.1 |
| 缺 key | 不回退中文默认；返回 `messages.<dotted.key>`；**用时** dedupe warn；**load 时**对照内置清单一次性 warn 全部缺失（不 fail-fast） |
| 空串 | key **存在**且值为 `""` 合法，**不** warning（条件片段常用） |
| 存放 | `config.yml` 嵌套 `messages:`；不得破坏现有 `items:` / `wait:` 等结构 |
| `{tier}` | **故意保持**英文 id（`weak`/`strong`/`boss`），与现网一致；不外置 tier 显示名 |
| 内容 YAML | 不迁入 `messages` |
| 热更 | **仅插件重启生效** |

## 3. 架构

### 3.1 `MessageService`

- 包：`io.mczju.maggoteers.config.MessageService`。
- `messages` 递归拍平为 `Map<String, String>`（点分 key）。
- YAML list：`game.meta_description.0`… + `rawList("game.meta_description")`。
- 非字符串叶子：视为缺失 + warning。
- **空字符串**：已定义 key 值为空串 → 正常返回空串，**不**走缺 key 路径。

#### 占位与 MiniMessage 安全（硬约束）

「先替换 `{name}`，再 MM deserialize」**仅当占位值已转义/安全插入时**才成立。

**必须**采用以下等价策略之一（实现任选，行为一致）：

1. **推荐**：`component()` 用 Adventure `TagResolver`（或先 deserialize 模板再 `append(Component.text(value))`），占位值以**纯文本 Component**插入；或
2. 字符串路径：替换前对每个值做 MiniMessage **tag escape**（`escapeTags` 或等价），再代入模板，再 deserialize。

`raw()` **同样必须**对占位值 escape 后再替换：调用方交给 MGC `sender().info/warn/error` 时会**再走一遍 MM**。`{display}` / `{detail}` / `{map}` / `displayPlain` 可能含 `<` / `>`，不转义会变标签或解析失败。
涉及：`reward.gained`、`game.plan_failed`、商店 `already_owned` / `unlocked` 等。

**禁止**：未转义的动态串直接拼进待 MM 解析的字符串。
Service **不**把占位值当成另一个 messages key做嵌套解析。

#### 嵌套片段（与「一律 escape」的关系）

Java 可以把子模板渲染结果塞进外层 `{prep_clause}` / `{strategy_clause}` / `{phase}` 等。
若子串已含 MiniMessage 标签，外层 `raw()`/`legacy()` 再 escape 一次，标签会变成字面量（记分板 `rest_suffix` 带颜色时必现）。

**本 spec 采用（禁止嵌套 MM 标签）**：

- 嵌套片段（`prep_clause` / `strategy_clause` / `phase_*` 等被塞进外层的值）**只允许纯文本**
  （可含已 escape 的动态值如数字、地图 id）；**不得**含 `<…>` MiniMessage 标签。
- **颜色与样式只写在最外层模板**（如 `game.ready`、`wave.begin`、`scoreboard.line_wave*`）。
- **不**引入 `text` vs `fragment` 两类占位 API（避免误用）。
- **不**采用「先拼 MM 片段再整串 escape」；记分板也不再 `legacy(rest_suffix)` 拼进 `phase`。

#### API

| 方法 | 用途 |
|---|---|
| `Component component(String key, Map<String, String> ph)` | 聊天、GUI lore |
| `String raw(String key, Map<String, String> ph)` | escape 后替换的 MM 源串（`getTitle()`、`sender()`） |
| `String legacy(String key, Map<String, String> ph)` | → legacy section（记分板 `Objective#getScore(String)`） |
| `List<String> rawList(String key)` | GameMeta description 等 |

### 3.2 加载硬顺序

`onEnable` **必须**：

1. `saveDefaultConfig()` / 读入主 config
2. **`MessageService.load(plugin)`**（含 §3.3 清单校验）
3. 之后才 `registerGame` / `registerLeaderboard`

否则首屏 `getGameMeta()` / 排行榜 `getTitle()` 可能早于 load，玩家看到 `messages.game.meta_name`。

### 3.3 load 期 key 清单校验

内置（代码常量或对照 jar 内默认 config 解析）**完整 key 集合**。`load` 后：

- 对**缺失**的每个 key：一次性全部 `warning`（可汇总），**不** fail-fast。
- 与「用时 dedupe warning」并存：load 已告警的 key，用时可跳过或再 warn（实现任选，须在代码/测试中一致）。
- 验收：load 期缺 key **必有**日志。

### 3.4 调用约定

- 范围文案全走 `MessageService`；Java **不得**保留同义中文 fallback。
- jar 内 `config.yml` 完整 `messages`（中文值=当前硬编码；**颜色写进 YAML**，不再依赖 `NamedTextColor`）。
- 条件片段（`prep_clause` / `strategy_clause`）：**仅 Java** 先渲染子 key，结果放进外层 `ph`。

### 3.5 已有服上 config

`saveResource(false)` 不合并缺 key。升级须手工合并 `messages:`，或备份后删配置重放默认。

## 4. config.yml 分组

- `messages.game` / `wave` / `death` / `menu.*` / `item` / `reward` / `scoreboard` / `leaderboard`
- `messages.command` — `/maggoteers` 非 debug 壳
- `death.missing_state`：**玩家可见**（属 `death`）；与 Logger 文案分离
- `menu.*.*_lore`：**单行 string**；`held`/`click` 另拼；不暗示 list

```yaml
messages:
  wave:
    cleared: "<green>✔ 本波清除！"
    enter_act: "<gold>▶ 进入第 {act} 层 · {map}"
  command:
    players_only: "<red>仅玩家可用。"
    usage: "<yellow>/maggoteers <shop|debug ...>"
```

### 4.1 条件片段与记分板组合（仅 Java）

**规则**：Service 不嵌套解析 key；嵌套片段为**纯文本**（无 MM 标签）；颜色在最外层；同一条消息**只 legacy/解析一次**（禁止 `raw` 与 `legacy` 互拼混用 `§` 与 MM）。

**开局 ready / 开波 begin**：

```text
// prep_clause、strategy_clause 配置值不含 <color> 等标签
prep_clause = prep > 0 ? raw("game.prep_clause", {prep}) : ""
ready = raw("game.ready", {prep_clause, waves, lives, ...})
// game.ready 自带颜色；prep_clause 仅为纯文本插入

strategy_clause = strategyId blank ? "" : raw("wave.strategy_clause", {strategy})
begin = raw("wave.begin", {wave, wave_count, tier, strategy_clause})
```

**记分板战斗侧栏**（两键，避免把带色 `rest_suffix` 塞进 `{phase}`）：

```text
phase = raw("wave.phase_<enum>")   // 纯中文：「休整」等，无颜色标签
if restSec > 0:
  line = legacy("scoreboard.line_wave_resting", {wave, wave_count, phase, rest})
else:
  line = legacy("scoreboard.line_wave", {wave, wave_count, phase})
```

默认 config 示件：

```yaml
scoreboard:
  line_wave: "<aqua>波 {wave}/{wave_count} · {phase}"
  line_wave_resting: "<aqua>波 {wave}/{wave_count} · {phase} <gray>({rest}s)"
```

选职业侧栏：`class_header` / `class_hint` / `class_chosen|pending` + `player_line`，不经 phase。

### 4.2 完整 key 清单

**game:** `meta_name` `meta_author` `meta_description`（list）`world_create_failed` `plan_generating` `plan_timeout` `plan_failed` `enter_class_select` `ready` `prep_clause` `settlement_grant`

**wave:** `skip_vote` `skip_passed` `enter_act` `prep` `begin` `strategy_clause` `cleared` `cleared_rewarded` `final_rest` `victory` `phase_prep` `phase_spawning` `phase_active` `phase_cleared` `phase_rest` `phase_done`

**death:** `missing_state` `auto_revive` `to_spectator`

**menu.rest:** `title` `normal_name` `normal_lore` `boss_name` `boss_lore` `skip_name` `skip_lore` `held` `click` `pool_missing` `no_offers` `not_enough_currency`

**menu.pick:** `title` `charge_error` `apply_failed`

**menu.class:** `title` `need_ticket` `chosen`

**menu.revive:** `title` `no_coin` `not_needed` `revived` `add_life` `head_down` `head_alive`

**menu.shop:** `title` `already_owned` `not_enough` `unlocked` `lore_owned` `lore_cost`

**item:** `rest_only` `shop_emerald_rest_only` `class_unavailable` `class_already` `ability_cooldown` `heal_full` `heal_amount`

**reward:** `gained` `apply_no_state` `apply_bad_bundle` `apply_failed` `apply_error` `click_choose`

**scoreboard:** `title` `line_act` `line_wave` `line_wave_resting` `line_mobs` `separator` `status_fight` `status_spec` `player_line` `class_header` `class_hint` `class_chosen` `class_pending`

**leaderboard:** `title` `subtitle`

**command:** `players_only` `usage`

> `debug()` 权限门的无权限提示仍**硬编码**（属 debug 门控，不进 `messages.command`）；仅 `/maggoteers` 无参 usage、非玩家提示等壳文案外置。

### 4.3 常用占位

`player` `actor` `target` `name` `display` `remaining` `act` `map` `wave` `wave_count` `tier` `strategy` `strategy_clause` `prep` `prep_clause` `voted` `total` `mobs` `phase` `rest` `revives` `status` `grant` `cost` `count` `pool` `id` `hearts` `lives` `waves` `detail`

## 5. 迁移文件清单

| 文件 | 动作 |
|---|---|
| `config.yml` | 追加完整 `messages:`（中文+颜色=现网观感；勿打乱现有顶层键） |
| 新建 `MessageService` + 单测 | 加载、escape/TagResolver、空串、缺 key、list、legacy、清单校验 |
| `MaggoteersPlugin` | **硬顺序** load → register |
| `MaggoteersGame` / `WaveScheduler` / `MaggoteersDeathStrategy` / `RunScoreboard` | 按上文 |
| Menus / Reward* / Item handlers | title/lore/sendMessage |
| `MaggoteersTotalLeaderboard` | title/subtitle |
| `MaggoteersCommand` | 非 debug 壳 → `messages.command.*` |
| `docs/dev-log.md` + plugin-config reference | 记录/文档 |

## 6. 验收

1. 范围内无玩家可见中文/符号硬编码（emoji/颜色标签仅在 config 值）。
2. 改 `config.yml` 一条，**重启**后局内反映。
3. 删某 key → 玩家看到 `messages.<key>`；**load 日志**列出该缺失。
4. 占位正确：`{player}` `{act}` `{wave}` `{voted}`。
5. **占位安全**：`{display}`/`{detail}` 含 `<red>x` 或裸 `<>` 时不破坏整句（单测）。
5b. **嵌套片段**：`prep_clause` / `phase_*` 默认值无 `<…>` 标签；`line_wave_resting` 外层带色正常（单测或配置审计）。
6. **颜色**：抽查 `death.to_spectator`、`reward.apply_*` 等原 `NamedTextColor` 提示，迁后仍带对应 MiniMessage 颜色。
7. 记分板休整期：走 `line_wave_resting`，`({rest}s)` 为灰色；`phase_*` 无颜色标签；不出现字面量 `<gray>`。
8. `getGameMeta()`/排行榜标题在首次展示前已 load。
9. 单测：拍平、escape/TagResolver、空串、缺 key、list、legacy、清单校验。
10. `mvn test` 通过。

## 7. 决策日志

- B+命令壳；debug/Logger/内容 YAML/BossBar 排除。
- 占位必须 escape 或 TagResolver（防 `raw`→`sender()` 二次 MM）。
- 条件片段仅 Java；**嵌套片段无 MM 标签**；记分板用 `line_wave` / `line_wave_resting`。
- `command.no_permission` **不外置**（属 debug 门控硬编码）。
- load 硬顺序 + 清单 warn。
- `{tier}` 保持英文 id；热更=仅重启。

## 8. Spec 自检（本修订）

- [x] 占位 MM 二次解析：escape/TagResolver
- [x] 条件片段职责在 Java；嵌套片段禁 MM 标签；`line_wave_resting` 替代 rest_suffix 拼接
- [x] `no_permission` 留在 debug 门控硬编码
- [x] load 硬顺序
- [x] load 清单校验
- [x] command 壳纳入
- [x] 记分板组合伪代码
- [x] 颜色进 YAML + 验收
- [x] `{tier}` 声明；空串合法；menu lore 单行；热更仅重启

