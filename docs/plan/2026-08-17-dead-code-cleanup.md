# Plan：死代码清理（2026-08-17）

> 来源：2026-08-17 四线审计。目标是把「定义了但从未被消费」的方法/字段/枚举/配置项安全删除，避免误删仍在用的扩展点。
> 铁律：**删前必须 grep 全文（含 src/test）确认为 0 引用**；删后 `mvn test` 全绿。任何「看起来没用但注释声称是扩展点/预留」的项，先与需求方确认再删。

## 0. 安全原则（每一步都必须遵守）

1. 删除前对每个符号执行 `grep -rn "<符号名>" src/`，确认只有定义处命中、无任何调用（含测试）。
2. 分小批删除 + 每批编译，不要一次性删几十处导致定位困难。
3. 只删「死」的（0 引用）；「半死」的（如 `RewardOption.unique` 仅用于加载告警）单独标注，不在本 plan 内贸然删。
4. 删除 record 字段 / 构造器参数时，顺带更新所有构造调用点与工厂方法，保证编译通过。
5. 本机 WSL 仅 JDK 21 无法编译（依赖是 Java 25 字节码）；用 Windows IntelliJ `mvn test` 作为最终验收。

## 1. R1：drop× 货币掉落死代码链（已确认删）

`drop×` 掉落倍率整条链路只写不读（`CurrencyService` 类不存在，货币仅来自清波 `clearReward`），属「不适用于多人合作」的死代码。

删除范围（自底向上）：

| 文件 | 删除内容 |
|---|---|
| `plan/Compose.java` | `drop *= a.drop()`（~L25）与 `drop * snap.currencyDrop()`（~L30）的倍率计算，`drop` 参数从 compose 产物移除 |
| `plan/RunPlanner.java` | 不再把 `ms.drop()/pms.drop()/dms.drop()` 传入 `SpawnStep/PassengerSpawn/DeathSpawn`（~L111/154/184） |
| `wave/MobSpawnProfile.java` | 移除 `dropMult` record 字段及 `fromStep/fromPassenger/fromDeathSpawn` 对应参数 |
| `wave/SpawnStep.java` | 移除 `dropMult` 参数（6 个构造器全部更新） |
| `wave/PassengerSpawn.java` | 移除 `dropMult` 参数 |
| `wave/DeathSpawn.java` | 移除 `dropMult` 参数 |
| `config/Affix.java` | 移除 `drop` 字段（若 `AffixService`/`Compose` 不再引用） |
| `config/ScalingConfig.java` | 移除 `currencyDrop` 字段与 `scaling.currency_drop` 读取 |
| `config.yml` | 删除 `scaling.currency_drop` 键 |
| `affixes.yml` | 删除 `greedy` 词缀（唯一用 `drop` 的） |
| `MobFactory.java` | 确认无 `drop`/`dropMult` 消费后保持不动 |

**验证**：`grep -rn "dropMult\|\.drop()\|currencyDrop\|currency_drop\|greedy" src/` 全部清空后再删；`RunPlannerTest`/`ComposeTest` 若有断言掉落倍率需同步改。

## 2. 战斗/波次模块死代码

| 符号 | 位置 | 说明 |
|---|---|---|
| `MobFactory.spawn(Location, SpawnStep)` | `MobFactory.java:69-71` | 无调用 |
| `MobFactory.spawnStepGroup`（2 参 @Deprecated 重载） | `MobFactory.java:62-67` | 仅被自身 3 参委托 |
| `WaveEngine.entityStep(UUID)` | `WaveEngine.java:65-69` | 无调用；连带 `WaveRuntime.mobSteps` 整 Map 只写不读 |
| `WaveRuntime.mobSteps` | `WaveRuntime.java:21` | 只 `put/remove`，从无 `get` |
| `MountedSquadRegistry.clearAll()` | `MountedSquadRegistry.java:26-28` | 无调用 |
| `WaveScheduler.pause/resume` | `WaveScheduler.java:231-239` | 无调用（暂停/恢复未接任何命令/流程） |
| `WaveScheduler.isPrepPhase` | `WaveScheduler.java:189-192` | 无调用 |
| `WaveRuntime.cleared` | `WaveRuntime.java:27` + `WaveScheduler.java:293` | 只写不读，防重复已由相位机保证 |
| `Phase.CLEARED` | `WaveScheduler.java:339` | 置位后立即转 REST，`tick` 无 `case CLEARED` |
| `MobSpawnProfile` 的 `affixes/infernal/name` | `MobSpawnProfile.java:7-12` | 从未被读（`onDeath`/`bossBar` 有用，保留） |
| `DeathSpawn.count` 恒为 1 | `RunPlanner.java:182` | 靠外部 for 展开，字段死重量 |
| `MapRepository.pickRandom` + 字段 `RNG` | `MapRepository.java:102-105,20` | 非确定性残留，RunPlanner 已走 `SeededRng` |
| `WavesConfig.getStrategy/getPool` | `WavesConfig.java:117,119` | 统一走 `definitions()` |

## 3. 效果/物品模块死代码

| 符号 | 位置 |
|---|---|
| `EffectService.dispatchTriggeredEffects` | `EffectService.java:293-296`（仅 `...Collecting` 被用） |
| `EffectService.executeEffect`（2 参重载） | `EffectService.java:460-462` |
| `EmpowerFxCoalesce.pickFxLabel`（@Deprecated） | `EmpowerFxCoalesce.java:51-57` |
| `MagicFxConfig.mergedFxForAbility`（@Deprecated） | `MagicFxConfig.java:171-175` |
| `BeamLogic.sampleSteps` | `BeamLogic.java:15-17` |
| `AttributeModifierKeys.matchesManagedEffect` | `AttributeModifierKeys.java:100-103` |
| `MagicFxPresets.followMarkAbove` + `EffectKeys.MARK_DURATION_SEC` | `MagicFxPresets.java:43-59`、`EffectKeys.java:41` |
| `MagicFxConfig.hasExplicitBinding/boundWeaponIds` | `MagicFxConfig.java:229,233` |
| `TriggerContext.withAttacker` | `TriggerContext.java:42-44` |
| `WeaponAbilityIds.isPackMember` | `WeaponAbilityIds.java:19-24` |
| `WeaponTempEffect.EFFECT_ID_PREFIX/effectId` | `WeaponTempEffect.java:9,13-15` |
| `ItemAbility` 访问器（primaryStep/effect/params/expiryTrigger/expiryCharges/stack/isLegacySingleStep） | `ItemAbility.java:25-56` |
| `ItemAbilityRegistry.snapshot` | `ItemAbilityRegistry.java:56-58` |
| `SummonExecutor.applyProjectile` 相同 if/else | `SummonExecutor.java:92-97`（`projectile_toward` 解析了却无差异，合并分支或删 toward） |
| `ItemKind.fromPdc`（精确匹配重载） | `ItemKind.java:23-29` |
| `ItemInteractRouter.registerHandler` + `WEAPON_HANDLERS` | `ItemInteractRouter.java:96-98,57`（扩展点 API，0 调用，运行时恒空；保留但标 deprecated，或删） |
| `Fake255Rules` | **不是死代码**，仍在服役（硬失败校验），勿删 |

## 4. 奖励模块死代码

| 符号 | 位置 | 说明 |
|---|---|---|
| `RewardPool.currency` + `currencyKind()/currencyItemKind()` | `RewardPool.java:18-25` | 币种实际由 `RestMenu` 硬编码 |
| `UpgradeLevelCaps.defaultCap()` | `UpgradeLevelCaps.java:16` | `resolve` 直读字段，不调它 |
| `collectibles.yml` 的 `a3b_atk300/mag300/hp40/spd25` | `collectibles.yml:105-112` | 无对应奖励，映射复用 act2 护符 |
| `RewardOption.unique` | `RewardService.java:82-85` | **半死**：仅加载告警，不参与运行判定；暂不删 |

## 5. 其他

| 符号 | 位置 |
|---|---|
| `ItemService` 的 `itemId/deliver/countCurrency/spendOne`（实为私有助手） | `ItemService.java:197/212/224/230/246` — 收紧为 private |
| `ParticleEffects.playAreaRing`（4 参重载） | `ParticleEffects.java:29-31` |
| `GameRegistries.PARTICLE_ALLOWLIST` 回退遍历不可达 | `GameRegistries.java:115-134` — 需确认是否 Paper 粒子重命名预留 |

## 6. 执行顺序与验收

1. 先做 §1（R1 drop× 链），单独一个提交，`grep` 清空 + `mvn test`。
2. 再按 §2→§3→§4 分批，每批「删 → `grep` 复核 0 引用 → `mvn test`」。
3. §5 的可见性收紧与 allowlist 确认放最后。
4. 全部完成后：`mvn test` 全绿；`grep` 复核无残留引用；更新 `docs/dev-log.md` 记一条。

> 任何一条 grep 出非零引用、或对「是不是扩展点」有疑虑，**停下确认**，不要删。
