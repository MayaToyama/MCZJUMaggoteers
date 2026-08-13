# 卫戍协议（Maggoteers）测试服上线前审阅

| 项 | 内容 |
|---|---|
| **日期** | 2026-08-12 |
| **性质** | 内容基本完成后、补齐物品风味描述并开启测试服前的全仓只读审阅 |
| **范围** | 生命周期 / 波次 / 效果战斗 / 奖励菜单 / 配置内容 / 构建部署 / 测试覆盖 |
| **对照** | `CLAUDE.md`、`docs/dev-log.md`、源码、`src/main/resources/`、测试服 `E:\MCpaper\plugins\Maggoteers\` |
| **构建证据** | `mvn test` → **261 通过 / 0 失败 / 5 跳过**；`Maggoteers-0.1.0-SNAPSHOT.jar` 可打包成功 |
| **结论一句话** | **架构成熟，适合内部测试服；不建议视为正式可发布版本。上线前应先修 Critical，并强制同步测试服 YAML。** |

---

## 0. 总览

### 0.1 成熟度评价

| 维度 | 评级 | 说明 |
|------|------|------|
| 架构与扩展点 | A | Trigger/Effect、配置驱动武器、RunPlanner 种子化清晰 |
| 波次与世界 | A− | 防卡波、懒粘贴、孤儿世界清理到位；终局分支有坑 |
| 效果 / 战斗 | B+ | 体系完整；魔攻模板泄漏、MagicDamageContext 嵌套需修 |
| 奖励 / 休整 UX | B− | 3 选 1 可用；扣费时机、投票、Boss 池粘滞未完工 |
| 配置内容完整度 | B+（约 85%） | 波/物/奖励大体齐；`act3_boss` 过薄、商店无商品 |
| 测试与运维 | B− | 单测多但缺集成测；服上 YAML 会漂移 |

### 0.2 处置优先级约定

| 级别 | 含义 | 建议 |
|------|------|------|
| **Critical** | 阻塞可靠开测 / 必现严重故障 / 经济或稳定性硬伤 | **上线前必须修或书面接受** |
| **Important** | 违背设计契约、多局干扰、明显玩法缺口 | 首轮 QA 前尽量修；可带风险记录 |
| **Minor** | 文档偏差、体验粗糙、低概率债 | 记 backlog |
| **手测** | 代码已改、需 Paper 实机确认 | 进服 checklist |

---

## 1. Critical（上线前应处理）

### C1 — 触发型 MAGIC_DAMAGE 模板被永久计入法伤倍率

| 项 | 内容 |
|---|---|
| **文件** | `src/main/java/io/mczju/maggoteers/effect/PlayerCombatStats.java` L28–34 |
| **触发** | 持有带 `trigger` 的 `ADD_ATTRIBUTE` + `MAGIC_DAMAGE` STAT（如波清/击杀叠魔攻）但尚未触发 grant |
| **原因** | `magicDamagePercentContribution` 未排除 `fireTrigger != null` 的模板层；与 2026-08-06 设计「仅计常驻 grant」不符 |
| **影响** | 法伤虚高；触发后再叠一层更失衡 |
| **建议** | 只累计 `fireTrigger == null` 的效果（或仅 `:grant` 层） |

### C2 — MagicDamageContext 嵌套 run 提前清除标记

| 项 | 内容 |
|---|---|
| **文件** | `src/main/java/io/mczju/maggoteers/effect/MagicDamageContext.java` L34–40 |
| **触发** | AOE 击杀 → 同步 `ON_KILL` 再进 `DAMAGE_AREA`/`SUMMON` → 内层 `finally` 清掉同 key 标记 |
| **原因** | `HashSet` 单 key add/remove，无引用计数 |
| **影响** | 外层后续命中失去 DEALT/TAKEN 抑制；极端配置可连环或递归（与 Guardian 尖刺修复相关） |
| **建议** | 引用计数或栈式 mark |

### C3 — 世界创建失败 / RunPlanner 超时不结束对局

| 项 | 内容 |
|---|---|
| **文件** | `src/main/java/io/mczju/maggoteers/game/MaggoteersGame.java` L110–112、L136–141 |
| **触发** | `createWorld()` 返回 null；或异步 plan 超过 30s |
| **原因** | 失败/超时仅消息，无 `fail()`/`endGame()`；对比 `plannedActs==null` 路径（L147–149）正确调用了 `fail()` |
| **影响** | 玩家已 `switchProfile` 清背包，房间被占，对局悬空 |
| **建议** | 两处均 `fail()` 并确保 cleanup |

### C4 — 3 选 1 扣费与 apply 非原子

| 项 | 内容 |
|---|---|
| **文件** | `menu/RestMenu.java` L67–76；`menu/PickMenu.java` L45–51 |
| **触发** | 扣币后关界面 / `RewardService.apply` 失败（满级、BOUND_EQUIP 冲突、物品缺失等） |
| **原因** | RestMenu 打开 Pick 前 `spendOneKind`；失败无退款 |
| **影响** | 休整货币白丢 |
| **建议** | 成功 apply 后再扣，或失败退还 |

### C5 — 最终 Boss 波跳过 clearReward / ON_WAVE_CLEAR

| 项 | 内容 |
|---|---|
| **文件** | `wave/WaveScheduler.java` L287–296 |
| **触发** | Act3 最后一波清空 → 直接 `win()` |
| **原因** | 提前 `return`，跳过 L298–318 发放与触发 |
| **影响** | 终局 Boss 通关奖励不发；依赖 `ON_WAVE_CLEAR` 的 STAT 不触发；召唤清理也跳过 |
| **状态** | dev-log 2026-07-04 已知 gap，**仍存在** |
| **建议** | 先 grant + fire + summon cleanup，再 `win()` |

---

## 2. Important（高风险 / 契约缺口）

| ID | 主题 | 位置 | 说明 |
|---|---|---|---|
| I1 | Boss 池粘滞未实现 | `RestMenu.java` L40–41；`RewardService.poolForWave` | 始终用当前层 `actN_boss`，违反 CLAUDE §9.2 |
| I2 | 休整跳过/延长无全员投票 | `RestMenu.java` L49–53；无 extend UI | 任一人可 `skipRest`；`rest.extend_*` 配置未接线，违反 §9.3 |
| I3 | 失败结算波数可能少 1 | `WaveScheduler.progress` L91–96；`Settlement` | REST 时 `waveIndex` 为 0-based 当前波索引，非「已清波数」 |
| I4 | 多局：`MobAiLockRegistry.restoreAll` | `MaggoteersGame.cleanupRun` L245；`MobAiLockRegistry` L122–129 | 全局 Map，一局结束可能恢复另一局 NoAI |
| I5 | 多局：`WaveEngine.stop` 清全部 MountedSquad | `WaveEngine.java` L141–143 | 无 game 过滤 |
| I6 | 绿宝石可非休整期开升级菜单 | `ItemInteractRouter.java` L65–66 | 货币有 REST 门控；SHOP_EMERALD 无 |
| I7 | 武器 CD 跨局残留 | `CooldownService.clear` 存在；cleanup 未调用 | 同 UUID 连开两局可继承 CD |
| I8 | `onDisable` 不清理活跃对局/世界 | `MaggoteersPlugin.java` L81–88 | 依赖下次 orphan 扫描 |
| I9 | Act1 结构双粘贴 | `beginClassSelect` + `WaveScheduler.enterAct(0)` | 同 origin 粘两次，主线程双倍卡顿 |
| I10 | 商店无商品 | `rewards.yml` **0** 条 `requires_unlock` | `/maggoteers shop` 空；账户货币无消费出口 |
| I11 | 商店无二次确认 | `UnlockShopMenu.java` | CLAUDE §13.3 AlertMenu 未做 |
| I12 | 武器右键 DAMAGE_AREA 不包 MagicDamageContext | `EffectService` vs BEAM 路径 | 可与 C2 叠加放大连锁 |
| I13 | 空目标仍计成功并扣 CD | `ConfigMagicHandler` / `executeMagicEffect` | 右键空挥浪费 CD |
| I14 | 测试服 YAML 漂移 | 源码 vs `E:\MCpaper\plugins\Maggoteers\` | **`rewards.yml` / `items/maggoteers.yml` 哈希不一致**；其余主要 YAML 一致。`saveResourceIfMissing` **不覆盖**已有文件 |

---

## 3. Minor / 内容缺口

| ID | 主题 | 说明 |
|---|---|---|
| M1 | `act3_boss` 仅 1 选项 | `rewards.yml` L2405–2413 仅 `w_revive_stone`；且终局不进休整 → 池近乎死内容 |
| M2 | Act3 `weak: 1` | `config.yml` L69；需确认是否有意缩短 |
| M3 | mushrooms 无生效 special_waves | 全注释占位 |
| M4 | `prot_chest` 无护符映射 | `_collectible_coverage.py`：STAT 127，缺映射 **1**（BOUND_EQUIP，可接受为故意不发护符） |
| M5 | 逐怪 `dropMult` 未消费 | 经济仅靠 `clearReward`；与 CLAUDE §7.2 字面偏差 |
| M6 | RunPlanner 不在 `onGameInit` | 在建世界后异步；文档偏差，非崩溃 |
| M7 | 假 amp-255「硬失败」表述过强 | 实际为 skip option / skip ability + 日志，插件不停 |
| M8 | 计分板每秒 `newScoreboard` | 长局 GC 压力 |
| M9 | `run_items.drop_protection: false` | 默认可丢局内物 |
| M10 | 版本 `0.1.0-SNAPSHOT` | 非正式发布语义 |
| M11 | debug `give`/`coin` 局外可用 | 需 `maggoteers.admin`（默认 op）；生产需约束 |
| M12 | abort/cancel 结算 0 | `outcome` 仍 IN_PROGRESS → `Settlement` 给 0；需确认设计 |
| M13 | 通关时间榜 / PoolBuilder 类名 | 未实现或文档过时；解锁已由 draw 过滤替代 |
| M14 | DISABLE_AI held_effects | Phase 2 未接线（已知） |
| M15 | MAGIC_DAMAGE 无计分板展示 | 内容可读性债 |

---

## 4. 配置与内容完整度快照（2026-08-12）

| 项 | 状态 |
|---|---|
| strategies / 池引用 / 原生 affix | 闭合；未知 affix **0** |
| rewards 池 | 10 池 / 约 200+ 选项；**act3_boss 过薄** |
| items | 大量定义；约 45 个 `use_ability`；假 255 **0** |
| collectibles | 111 映射；直接 STAT 缺 1（`prot_chest`） |
| maps | 6 图 + `structure.nbt`；mushrooms special_waves 空 |
| InfernalMobs | waves 约 **95** 处 `infernal:`；无 IM 时降级普通怪 |
| 局外解锁商品 | **0** |

**护符脚本**：`python scripts/_collectible_coverage.py` → `STAT_without_mapping 1`（`prot_chest`）。

**测试服哈希对照**（源码 `src/main/resources` vs `E:\MCpaper\plugins\Maggoteers`）：

| 文件 | 一致 |
|---|---|
| `collectibles.yml` | 是 |
| `waves.yml` | 是 |
| `affixes.yml` | 是 |
| `items/collectibles.yml` | 是 |
| `rewards.yml` | 否 |
| `items/maggoteers.yml` | 否 |

---

## 5. 做得好的地方

1. RunPlanner 纯函数 + 种子确定性；异步不碰 Bukkit API。
2. 世界创建 / 粘贴 / 刷怪主线程边界正确；目录删除异步。
3. WaveEngine 5-tick 扫描 + 苦力怕/史莱姆/骑乘专项，卡波风险可控。
4. PlayerState 为效果真相源；死亡复活 resync；GRANT/REVOKE 双 pass。
5. MagicDamageContext 同 tick 抑制 DEALT/TAKEN（Guardian 修复方向正确，需嵌套加固）。
6. RewardLoadValidator / Fake255 / WeaponEffectValidator 加载期拦坏配置。
7. InfernalMobs 反射桥优雅降级 + 死亡前 unregister。
8. cleanupRun 子系统 teardown 较完整；孤儿世界 onEnable 清理。
9. Settlement / 排行榜 / 房间 default.json / MGC 注册契约基本齐。
10. 单测面广（261），远高于一般小游戏插件。

---

## 6. 测试服准入门槛

### 6.1 硬门槛（建议全部满足再开多人测）

1. **修 Critical C1–C5**（或书面接受 C5 终局不发 clearReward）。
2. **同步** 测试服 `rewards.yml`、`items/maggoteers.yml`（及有改动的其余 YAML），**重启 Paper**（勿依赖 `/reload`）。
3. **依赖就位**：Paper 26.2、MGC 1.0.7、ItemCreator 1.1.0、`rooms/maggoteers/default.json`；测 IM 波需 InfernalMobs。
4. **`mvn test` 0 failures**（当前已满足；immunity 相关 5 项 skipped 需知悉）。
5. **Smoke**（至少 1 人，建议 4 人）：
   - `/mgc join maggoteers` → 职业 → 清 ≥2 波 → 休整 3 选 1
   - 死亡自动复活 / 全员观察者失败结算
   - 通关或 debug 跳终局 → WIN 结算持久化
6. **近期修复回归**：
   - Guardian / 刺猬胸甲不崩服
   - clear_potions / immunity
   - 触及不随 resync 膨胀
   - BOUND_EQUIP 绑定与槽位锁（若测该奖励）

### 6.2 软门槛（可记已知限制）

- 商店无商品；无休整投票/延长；Boss 池无粘滞。
- Act3 weak=1、`act3_boss` 过薄。
- OP 可用 debug give/coin。
- 版本仍为 SNAPSHOT。

### 6.3 建议验收命令

```
/mgc join maggoteers
/maggoteers debug plan
/maggoteers debug state
/maggoteers shop
```

（`debug give` / `debug coin` 仅 QA 环境。）

---

## 7. 建议修复顺序（不实施，仅排期）

| 批次 | 项 | 目的 |
|------|-----|------|
| **A** | C1 魔攻过滤、C2 引用计数、C3 fail 路径、C4 扣费原子、C5 终局发放 | 稳定与经济正确 |
| **B** | I4/I5 多局隔离、I6 绿宝石门控、I7 CD clear、部署同步 YAML | 测服可并行 / 配置可信 |
| **C** | I1 Boss 粘滞、I2 投票+延长、I10 解锁商品、扩充 `act3_boss` | 对齐 CLAUDE 玩法契约 |
| **D** | 集成测 / 免疫 CI、内容 polish、release 版本号 | 正式发布准备 |

---

## 8. 手测清单（从 dev-log 未关闭项汇总）

### P0

- [ ] 世界创建失败 / plan 超时 → 应 `endGame`、回大厅、无悬空 GUI（修 C3 后验）
- [ ] Act3 最终 Boss → clearReward + ON_WAVE_CLEAR（修 C5 后验）
- [ ] 3 选 1 apply 失败 / 关窗 → 货币不丢（修 C4 后验）
- [ ] 全员倒下 → fail 结算 → 世界卸载 → 无 `maggoteers_*` 残留

### P1

- [ ] 完整 3 Act / 中途 fail 结算金额
- [ ] 苦力怕 / 史莱姆 / 乘客骑乘：无永久卡波
- [ ] 职业超时自动选；进 Act2/3 传送与召唤清场
- [ ] 复活币救回 / 最后幸存者倒下 fail
- [ ] 绿宝石战斗期是否仍能开升级菜单（I6）

### P2（近期修复）

- [ ] Guardian 尖刺 + 刺猬胸甲不崩服
- [ ] 沉默 / 净化 / 免疫样例
- [ ] 触及不膨胀
- [ ] DISABLE_AI / held_effects 换手丢弃 / BOUND_EQUIP
- [ ] IM 波 mechanize + 死亡无 IM 掉落（若启用）
- [ ] 触发型魔攻倍率与 baseline 对比（验 C1）

### P3 运维

- [ ] jar + YAML 同步后重启；启动日志无大量 rewards skip
- [ ] ItemCreator 缺失时 warn 不崩
- [ ] `maggoteers_total` 排行榜写入
- [ ] 崩服重启孤儿世界清理

---

## 9. 审阅元数据

| 项 | 值 |
|---|---|
| 审阅类型 | 只读；**未改业务代码**（本文档除外） |
| 方法 | 四路并行审计（生命周期 / 效果 / 配置 / 发布）+ 主控交叉核验源码与脚本 |
| 相关 skill | minecraft-plugin-dev；项目内 maggoteers-plugin-config / maggoteers-build-deploy |
| 后续 | 若开始修 Critical，建议另开 plan（`docs/superpowers/plans/`）按批次 A→D 执行 |

---

<!-- 新审阅按日期追加同目录；关闭项时在本表勾选或追加「已关闭」段 -->