# Plan：设计冗余重构（2026-08-17）

> 来源：2026-08-17 四线审计。目标是消除「同一件事多处实现 / 一个类职责过重 / 同型样板复制粘贴」的冗余，降低改动漏改风险。
> 原则：**行为不变**的重构优先；先抽公共、再迁移调用、最后删旧实现；每步 `mvn test` 全绿。

## 1. 贴图/传送逻辑重复（最高优先，改动漏改风险）

- `WaveScheduler.pasteActForCursor`（`WaveScheduler.java:253-271`）与 `ActSpawnHelper.pasteAndTeleport`（`ActSpawnHelper.java:14-34`）**几乎逐行重复**：解析 `act_origins`、`findMap` 过滤 `mapId`、`StructurePaster.pasteAct`、`getPlayers().forEach(teleport + setFallDistance(0))`。
- `findMap`（`WaveScheduler.java:414-417`）与 `ActSpawnHelper.java:23-24` 各实现一遍「按 act+mapId 找 MapEntry」。
- 层原点解析 `WaveScheduler.pasteActForCursor` 手工 `getIntegerList` + 默认 64，而 `Origins.parse`（`util/Origins.java`，`RunPlanner.java:204` 已用）正是同一件事。

**做法**：
1. `MapRepository` 下沉 `find(act, mapId)`。
2. `ActSpawnHelper.pasteAndTeleport` 改用 `Origins.parse`。
3. `WaveScheduler.pasteActForCursor` 委托 `ActSpawnHelper`（或删掉让 `enterAct`/`debugSeekWave` 直接调 `ActSpawnHelper`）。

## 2. EffectService god-class 拆分（872 行）

现状一个类同时承担：merge/apply、resync/strip、trigger 分发 + sweep、全部 Effect 执行（beam/buff area/disable ai/summon/grant item/heal/grant attribute）、派生视图、药水刷新、empower FX、grant attribute。

- `executeMagicEffect`（`EffectService.java:511-587`）与 `executeEffect`（`EffectService.java:464-497`）两套 switch 有部分重复（GRANT_ITEM/SUMMON/DAMAGE_*/BUFF_AREA/DISABLE_AI 双份）。
- 建议：按 Effect 拆 `executeDamageBeam/executeBuffArea/executeDisableAi`（现为 40-90 行私有方法）为独立策略类 `effect/exec/*`，`EffectService` 只做分发 + 生命周期。

**注意**：这是较大重构，需先补足单元测试锁定当前行为（`EffectServiceAbilityTest` 等已存在，扩展覆盖），再逐步迁移。

## 3. 解析器重复

- `EffectParamsParser.parse`（`EffectParamsParser.java:23-81`）与 `RewardOption.parseParams`（`RewardOption.java:135-199`）的 `attr/potion/amp/count/duration_ticks/potions/clear_potions/mark_fx/carrier_fx/fx/grant` 解析**几乎逐条重复**。
- FX 解析三套：`MagicFxBuilder.putFxEntry/fromContext`（`MagicFxBuilder.java:27-76`）、`MagicFxConfig.parseSection`（`MagicFxConfig.java:259-305`）、`EffectParamsParser.parseFxContext`。
- `grant` 嵌套块解析在 `EffectParamsParser.parseGrantBlock`（`EffectParamsParser.java:85-118`）与 `RewardOption.parseGrantBlock`（`RewardOption.java:218-248`）重复。

**做法**：抽单一「param key → EffectContext」映射表供三处复用；先锁定现有单测（`ItemAbilityParseTest`/`RewardLoadValidationTest` 等），再替换。

## 4. Registry / items 扫描同型样板

- `ItemAbilityRegistry`（use_ability）、`WeaponHeldRegistry`（held_effects）、`MagicFxConfig`（use_fx）、`ItemService`（parseYamlToItems）各自**独立扫一遍 `items/*.yml`**，`load/loadItemsDir/parseYaml/normalizeItemId` 样板几乎逐行相同。
- `ItemAbilityRegistry`/`WeaponHeldRegistry`/`SummonRegistry`/`MobAiLockRegistry`/`AuraService` 都是「static IdentityHashMap 按 game 索引 + per-game clear」同型。

**做法**：
1. 抽「items/*.yml 目录加载 + id 归一化」共享工具（一次扫描产出结构化 map，各 registry 按需订阅自己的 section）。
2. 抽通用 per-game registry 基类（`Map<AbstractGame, T> + clear(game)`），各 registry 继承。

## 5. 波次数据模型冗余

- `SpawnStep/PassengerSpawn/DeathSpawn` 三 record 字段高度重复（`type, hp/dmg/speed/drop/scale/followRange, affixes, potions, infernal, equipment, name, bossBar`），差异仅 `count`。
- `SpawnStep` 有 6 个构造器、`PassengerSpawn`/`DeathSpawn` 各 4-5 个，多数无调用者（与死代码 plan §2 有交集，先删死构造器）。
- `MobSpawnProfile` 只是为 `onDeath`+`bossBar` 两字段做的 6 字段转换层（其中 4 字段死）。

**做法**：先配合死代码 plan 删 `dropMult`/死构造器；再评估 `MobSpawnProfile` 精简为 `record MobSpawnProfile(List<DeathSpawn> onDeath, boolean bossBar)` 或并入 runtime 的两个 Map。record 不便继承，若抽公共基类收益有限则只做字段精简、不强抽。

## 6. 执行顺序

1. §1 贴图/传送去重（低风险、高收益）。
2. §5 波次数据模型精简（配合死代码 plan）。
3. §4 Registry 基类 + items 扫描共享（中风险）。
4. §3 解析器统一（中风险）。
5. §2 EffectService 拆分（高风险，放最后、需测试兜底）。

每步：`mvn test` 全绿 + 更新 `docs/dev-log.md`。全程「行为不变」；任何行为差异需在 commit message 说明理由。
