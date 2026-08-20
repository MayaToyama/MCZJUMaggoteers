# QA 发现清单 #2 — 2026-07-04（首轮修复后的第二轮 QA）

> 部署 `bb51b6f`（商店修正后）QA 发现。5 项：2 严重 bug + 2 体验 + 1 待确认/普通。
> 每条含根因 + 修法 + 文件位置。

---

## 🔴 #1 药水效果非永久（常驻药水 30s 后消失，仅复活时刷新）

**现象**：可升级的药水效果（抗性/力量等常驻型）实际只持续 30s，到期就没了；只有玩家死亡复活时（`resync`）才被补回。

**根因**：`effect/EffectService.applyPotionPermanent` 用 `new PotionEffect(type, 20*30, amp, ...)` —— **30s 固定时长**，不是永久。设计 §10.3 本意是"常驻药水由 ON_TICK_1S 定期刷新续期"，但 Plan 5 只在 `fireTrigger(ON_TICK_1S)` 里处理触发型/recurring，**从没刷新常驻药水**。属性 modifier 会持久（挂在 AttributeInstance 上），但药水有时长、到期消失。

**修法**：在 `EffectListener` 的 1s 心跳里（`startTick` 的 lambda，fire ON_TICK_1S 之后），对每个冒险模式玩家**重新施加全部常驻 ADD_POTION 效果**（刷新 30s，永不到期）。新增 `EffectService.refreshPermanentPotions(Player)`（读 PlayerState.effects() 里 `isPermanent() && effect()==ADD_POTION` 的，逐个 `addPotionEffect`），心跳每秒（或每 5s）调一次。与 #3 饥饿值合并在同一心跳。

**文件**：`effect/EffectService.java`（加 `refreshPermanentPotions`）+ `effect/EffectListener.java`（心跳调它）。

---

## 🔴 #2 进入房间等待时持有上一局的物品栏

**现象**：上一局结束后，玩家进入新一局的**等待阶段**时，背包还是上一局游戏的物品。

**根因**：`game/MaggoteersGame.cleanupRun()`（onGameEnd/Abort/Cancel 调）只重置了 GameMode（Bug3 修），**没有切回大厅 profile / 清背包**。MGC 的 `switchProfile(gameId)` 在开局时保存大厅背包、给干净游戏背包；对局结束若不 `switchProfile(null)` 恢复大厅 profile，玩家就停留在游戏 profile（带游戏物品）→ 下一局等待时仍显示。

**修法**：在 `cleanupRun` 对每个玩家恢复 profile + 清状态：
```java
for (var pe : getPlayers()) {
    pe.switchProfile(null);          // 恢复大厅 profile（背包回到开局前）
    var pl = pe.player();
    pl.setGameMode(SURVIVAL);         // 已有（Bug3）
    pl.getInventory().clear();        // 兜底清游戏残留
    pl.setHealth(20); pl.setFoodLevel(20);  // 复位
    // EffectService.removeAll(pl) 已有
}
```
（`switchProfile(null)` 是 MGC 恢复大厅 profile 的方式——前代 VampireSurvivor 在 onGameEnd 用此。若 MGC 的 `endGame` 已自动切，则只清背包兜底；实测以 `switchProfile(null)` 为准。）

**文件**：`game/MaggoteersGame.java`（`cleanupRun`）。

---

## 🟠 #3 饥饿值会消耗（需完全排除饥饿影响：始终满 + 不回血）

**现象**：玩家一段时间后饥饿、无法奔跑。游戏里不应受饥饿影响。

**根因**：世界 GameRule 只设了 `NATURAL_REGENERATION=false`（已阻止饱食回血），但**没禁饥饿消耗**（无对应 gamerule）。玩家 food 随时间下降。

**修法**：在 1s 心跳里（与 #1 同一处）对每个冒险模式玩家强制 `setFoodLevel(20)` + `setSaturation(5f)`（或更高，保证不降）。配合 `NATURAL_REGENERATION=false`，饱食满但不回血 —— 满足"始终为满 + 无法回血"。可加 `setExhaustion(0)` 防饱和掉食物。

**文件**：`effect/EffectListener.java`（心跳，与 #1 合并）—— 或新建一个 `GameplayTickListener` 专管"非效果"的每秒维护（饥饿/药水刷新），与 `EffectListener`（效果触发）解耦更干净。建议后者。

---

## ❓ #4 魔法测试剑奖励只给普通测试之剑

**现象**：3 选 1 抽到"魔法测试剑"（`maggoteers:test_blade`），只得到一把普通铁剑，没有魔法效果。**未知是否为 bug**。

**根因**：**不是 bug，是"还没实现"**。`test_blade` 是 WEAPON 类奖励，`RewardService.apply` 的 WEAPON 分支只 `give(item)` —— 给物品本身。物品要"魔法"，需按 §15 在 `ItemInteractRouter.registerHandler("maggoteers:test_blade", handler)` 注册一个 handler（右键触发挥砍/范围伤害/粒子等）。当前**没有任何武器 handler 注册**，所以它是把普通剑。这是"可扩展地基"待用的状态。

**修法（建议，演示地基可用 + 顺带测 #5 粒子）**：实现一个示例魔法武器 handler —— 右键 `test_blade` → 在前方/周围放一个 `DAMAGE_AREA`（复用 `EffectService` 或直接 `damage` 近敌）+ 播放粒子环（#5 的粒子方法）+ `CooldownService.tryUse` 门禁。在 `MaggoteersPlugin.onEnable`（ItemService init 之后）`registerHandler("maggoteers:test_blade", new TestBladeHandler())`。这样"魔法测试剑"右键就有范围斩效果，演示整条武器链路。

**文件**：新 `item/interact/TestBladeHandler.java`（或 `item/handler/`）+ `MaggoteersPlugin.onEnable`（注册）。

---

## 🟠 #5 范围打击/爆炸缺粒子效果（需实现层加一个粒子方法）

**现象**：`DAMAGE_AREA` 效果和爆炸没有视觉反馈，玩家看不出范围。

**根因**：`EffectService.executeEffect` 的 DAMAGE_AREA 分支只 `getNearbyEntities` + `damage`，**无粒子**；MobDeathListener 的爆炸也没粒子。

**修法**：新增粒子工具方法，供范围效果调用：
```java
// util/ParticleEffects.java
public static void playAreaRing(org.bukkit.World w, org.bukkit.Location center, double radius,
                                org.bukkit.Particle particle, int count) {
    // 在 center 周围 radius 半径画一圈粒子（水平圆/球面采样），给玩家范围提示
    for (int i = 0; i < count; i++) {
        double angle = 2 * Math.PI * i / count;
        double x = center.getX() + radius * Math.cos(angle);
        double z = center.getZ() + radius * Math.sin(angle);
        w.spawnParticle(particle, x, center.getY() + 0.5, z, 1);
    }
    // 可选：再画几层不同 y 形成球面
}
```
- `EffectService.executeEffect` DAMAGE_AREA 分支：damage 前/后调 `ParticleEffects.playAreaRing(p.getWorld(), p.getLocation(), radius, Particle.FLAME, 24)`。
- MobDeathListener 爆炸（或 creeper 自爆处）：调一个爆炸粒子（`Particle.EXPLOSION_LARGE` 已由原版爆炸自带，可跳过；重点是 DAMAGE_AREA 的范围提示）。

**文件**：新 `util/ParticleEffects.java` + `effect/EffectService.java`（DAMAGE_AREA 分支调用）+ 视情况 `listener/MobDeathListener.java`。

---

## 修法优先级
1. 🔴 #1 药水永久（tick 刷新）—— 体验最重，常驻 buff 失效等于强化系统失效。
2. 🔴 #2 profile/背包（cleanupRun switchProfile(null)）—— 每局都踩。
3. 🟠 #3 饥饿（tick 维持满）—— 与 #1 同心跳，一起做。
4. 🟠 #5 粒子工具 + DAMAGE_AREA —— 实现层基建。
5. ❓ #4 示例魔法武器 —— 用 #5 的粒子演示地基（可放最后，或跳过只澄清）。
