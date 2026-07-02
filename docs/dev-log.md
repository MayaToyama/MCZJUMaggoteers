# 开发日志 / 决策记录（dev-log）

> 本文件按时间倒序追加。每条记录：**日期 → 做了什么 → 决策与原因 → 遗留问题**。
> 目的：让后续接手的 AI agent 读懂"为什么是这样设计的"，而不仅仅是"现在长什么样"（现状看 `CLAUDE.md`）。

---

## 2026-07-02 — 第三轮接入契约复核（§G 整改）

### 做了什么
- 处理核对清单第三轮（对照 1.0.5 clone + 整改后全文）。结论：**无 Blocker，可进 writing-plans**。整改 §G1–G4。

### 决策与原因（源码实证 G1/G2）
- **G1 房间实例**：`registerGame` → `loadGameRoom`（`DefaultGameManager:43`），房间 JSON 在 `plugins/MCZJUGameCore/rooms/maggoteers/*.json`；**无 READY 房间则 `/mgc join` 失败**。→ onEnable 先释放默认 `default.json` 再 registerGame（或运维 `/mgcop room create maggoteers default`）。room 是调度壳、可复用；每局仍自建虚空世界。
- **G2 giveItem 路由**：`PlayerExt.giveItem(String)` 走 MGC ItemManager（**非** ItemCreator）；ItemCreator 物品必须 `ItemService.createItem(id)` + `giveItem(ItemStack)`（`PlayerExt:111`）。
- **G3 刷怪点 schema**：points.yml 示例补 `boss`；RunPlanner 校验 `steps[].point` 编号在该图 points.yml 存在。
- **G4 建世界归属**：唯一在 `onGameStart` 就绪门闩内（主线程 `createWorld` + 粘贴 Act1）；`onGameInit` 只异步 RunPlanner + NBT 预读、**不建世界**——避免双触发。
- 新增 CLAUDE.md §16.1 首测部署清单（MGC 1.0.4→1.0.5、`rooms/maggoteers/default`、资产、ItemCreator）。

### 遗留 / 进 plan
- §G 部署项（MGC 升级、room 创建）作为 plan 阶段 0。
- ItemCreator 版本号、净化技巧（D1）实现期核实/实测。

---

## 2026-07-02 — 第二轮接入契约复核（§E 整改）

### 做了什么
- 处理核对清单第二轮（对照 GitHub 1.0.5 源码 + 测试服 1.0.4 jar），整改 §E1–E8 文档遗漏。

### 决策与原因（源码实证）
- **E1 持久化路径**：`JsonPlayerData.getFilePath()` = `<MGC数据目录>/player_data/<gameId>/<uuid>.json` → 实际 `plugins/MCZJUGameCore/player_data/maggoteers/<uuid>.json`（**不在本插件目录**）。`dev-advanced.md` 的 `player/...` 已过时。
- **E2 落盘周期**：`MCZJUGameCore.java:73 startAutoSave(20*60*30L)` = **30 分钟**（非 5 分钟）+ 退出 `savePlayerDataAsync` + 关服 `saveAllPlayerData`。
- **E3 版本**：测试服 jar=**1.0.4 已含** PlayerData API（非"缺 API 编译不过"）；设计基线 1.0.5，建议升级对齐。本地 clone=1.0.0 过时（无 PlayerData、Menu API 也不同）。
- **E4 Menu（1.0.5）**：子类 `public XxxMenu(Player, Object...){ super(player,args); }` + 重写 `getTitle/getRows/getPermission/setup`。
- **E5 排行榜**：`getLeaderboardManager().registerLeaderboard(id, Class)` + `PlayerDataLeaderboard` 四抽象方法（`getTitle/getSubtitle/getPlayerDataClass/getFieldName`）。
- **E6/E7/E8**：§2.4 交叉引用（→§13.1/§13.5）、spec 与 CLAUDE.md 同步、RunPlan 去除 `World` 引用（异步线程不持有 World，主线程绑定再构造 Location）——均已改。

### 遗留
- ItemCreator jitpack 版本号、净化技巧（D1）实现期核实/实测。

---

## 2026-07-02 — 接入契约复核与整改（review）

### 做了什么
- 处理 `docs/review/2026-07-02-接入契约核对清单.md`（一份逐条对照真实源码的自检清单）。
- 重新核对 GitHub 最新版 MGC 源码；需求方裁定**以 GitHub 最新版为准**（测试服旧版过时）。

### 决策与原因
- 清单 **§A / §B1 / §B2 / §B5 证伪**——它们基于过时的本地源码（无 PlayerData 系统）；GitHub 最新版有完整 `JsonPlayerData`/`getData`/`resetState`/`PlayerDataLeaderboard`，前代 `build.gradle` 也实证了 jitpack groupId `com.github.mczju-ops`。CLAUDE.md 这几处原正确，不整改。
- 清单 **§B3 / §B4 成立**（版本无关的设计疏漏）：`createWorld()` 必须主线程；`onGameStart` 返回 void 不能阻塞 → 引入"就绪门闩"（`runTaskTimer` 轮询）。
- 清单 **§D1–D8 全部成立**，已作为规则/风险写入 CLAUDE.md：CD 走 PDC 时间戳表（不用 `setCooldown(Material)`）、3 选 1 不足灰显不补凑、胜负 `outcome` 标志、属性多层唯一 `NamespacedKey`、补怪走种子 RNG、触发被动默认 `IGNORE`、净化"0s 255 级"技巧待实测。
- **新增要害**：测试服必须升级到 GitHub 最新版 MGC，否则持久化整章失效（写入 CLAUDE.md §1.2 版本基线 + §18）。

### 遗留
- ItemCreator jitpack 版本号待核实。
- 净化技巧实现期实测。

---

## 2026-07-02 — 立项与设计定稿（brainstorm 阶段）

### 做了什么
- 与需求方逐段确认了 The Maggoteers（卫戍协议）的完整设计，产出 `CLAUDE.md`（项目圣经）与 `docs/spec/2026-07-02-maggoteers-design.md`（设计叙事）。
- 阅读并借鉴了三个相关项目：
  - `MCZJUGameCore`（依赖框架）——确认接入契约（`AbstractGame`/`JsonGameRoom`/`JsonPlayerData`/`PlayerExt`/排行榜/Menu）。
  - `MCZJUItemCreator`（物品 API）——`ServicesManager` 取 API；`createItem`/`parseYamlToItems`。
  - `MCZJUvampireSurvivor`（前代同款原型）——直接借鉴**已验证**的世界/虚空/结构粘贴/波次引擎/死亡策略/缩放。
  - `MCZJUMagicItems`（效果范式）——借鉴"触发→分发→效果"PDC 范式。

### 关键决策与原因
- **不引入 WorldEdit/MythicMobs/InfernalMobs**：核心诉求是"纯配置、便于管理"，第三方怪物插件 API 不可控。精英怪=自实现配置词缀层。
- **账户货币自建**：发现 MGC 的 `ScoreManager`/`HistoryScoreManager` 是**空壳**，无可用积分系统。
- **世界=每局新建虚空世界**：多房间天然并行；结构粘贴用 Paper `Structure` API（不依赖 WE），方法已被 VampireSurvivor 验证。
- **波次时序=单一可暂停状态机**：休整期要暂停、跳过/失败要统一取消，链式 task 做不到。
- **效果生命周期=事件到期，非挂钟计时**（需求方关键修正）：限时道具限的是"波次/层级/下次攻击/下次复活"等事件，且效果必须**跨死亡复活存活**——原版药水 infinite 时长做不到。故 PlayerState 为真相源、复活后 resync，删除前代 `BuffInstance` 实时计时。
- **魔法武器 v1 不做框架**：需求方澄清——ItemCreator 出带 PDC 物品、本插件写识别+触发类即可，无需通用接口。留作"PDC 路由"文档化扩展路径。
- **排行榜积分 = `totalEarned`**：免单独计分系统。
- **Boss 池粘滞**：= 最近击杀 Boss 所在层，杀掉本层 Boss 才更新（需求方精化）。
- **缩放开局锁定 + 不允许中途加入**：简化预生成与调试。

### 遗留 / 待实现期核实
- ItemCreator 的 jitpack 版本号。
- `parseYamlToItems` 与 ItemCreator 原 `items/` 加载路径的特性等价性（用一个复杂武器验证）。
- 尚未写任何代码；下一步进入 **writing-plans**，产出 `docs/plan/` 分阶段实现计划。

---

<!-- 新条目按此格式继续追加在最上方（日期倒序） -->
