# 卫戍协议内部测试版 `1.0.0-rc.1`

面向内部签收（发布清单 **A**）。**正式玩法全开**，同时默认打开 debug，方便测奖励/商店/跳波。

## 依赖（缺一不可）

| 组件 | 版本 |
|------|------|
| Paper | **26.2** |
| MCZJUGameCore | **1.0.7** |
| MCZJUItemCreator | **1.1.0** |
| InfernalMobs | 可选 softdepend |

**禁止 `/reload`**，换 jar / 大改 YAML 后请重启 Paper。

## 安装

1. 放入 `plugins/Maggoteers-1.0.0-rc.1.jar`（可删旧 `Maggoteers-*-SNAPSHOT.jar`，避免双 jar）。
2. 同步配置到 `plugins/Maggoteers/`（至少）：
   - `config.yml`（含 `debug.enabled: true`）
   - `rewards.yml` / `waves.yml` / `affixes.yml` / `collectibles.yml`
   - `items/*.yml`
   - `maps/`（若服上无完整地图）
3. 确认 `plugins/MCZJUGameCore/rooms/maggoteers/default.json` 存在。
4. 重启 Paper；控制台应出现：
   - `Maggoteers (卫戍协议) enabled`
   - `内部测试版：debug.enabled=true`
   - `ItemAbilityRegistry: loaded 44 use_ability`（无武器 skip WARN）

## 测试版 vs 正式服（同一套代码）

| | 内部 RC（当前默认） | 正式服（签收通过后） |
|--|--|--|
| `debug.enabled` | `true` | **`false`** |
| `/maggoteers debug` | 任意有 `maggoteers.play` 的玩家 | 仅 `maggoteers.admin`（OP） |
| 玩法 / 奖励 / 商店 / 三层 / 结算 | **全部启用** | 同左 |
| `wait.min_players` | `1`（可单人测） | 按需改 `2`–`4` |

## Debug 速查（内部）

```
/maggoteers shop
/maggoteers debug give <奖励id|物品id> [数量]
/maggoteers debug coin [数量]
/maggoteers debug balance [数量]
/maggoteers debug unlock <id|all>
/maggoteers debug jumpto <层1-3> <波1-n>
/maggoteers debug jump [±n]
/maggoteers debug spawnmob <实体> [词缀…]
/maggoteers debug state|plan|wave|act|effects
```

奖励发放须**在局内**；账户币/解锁局外也可用。

## 签收清单 A（通过后再做正式 `1.0.0`）

- [ ] 1 人：join → 职业 → Act1–3 → 终局休整能开 Boss 池 → WIN
- [ ] 失败路径：全员观察者 → FAIL 结算有币
- [ ] 重启后余额 / unlocks 仍在
- [ ] `/maggoteers shop` 有商品；`balance` + 购买后局内可抽到解锁武器
- [ ] 启动无武器 ability skip；无孤儿世界堆积
- [ ] （可选）2–4 人：GUI、跳过休整投票、复活币

签收通过 → 将 `debug.enabled` 改为 `false` → 版本改为 `1.0.0` → 正式发布。