# InfernalMobs 波次接入设计

**日期：** 2026-07-26  
**状态：** 设计已批准，等待实现计划  
**范围：** 只修改 Maggoteers，不修改 MCZJUinfernalMobs 源码

## 1. 问题

Maggoteers 当前通过 `affixes.yml` 的 `on:` 字段和自身伤害监听器实现战斗触发词缀。内容维护者无法在 `waves.yml` 中为波次实体精确指定 MCZJUinfernalMobs（下文简称 IM）技能。

本次接入必须满足：

1. 允许在波次主体、乘客和亡语生成物上配置精确的 IM 技能列表。
2. 后续内容调整只需维护 `waves.yml`。
3. IM 缺失或不兼容时，波次仍可继续运行。
4. 不修改 IM 源码。
5. 立即删除 Maggoteers 原有的 `on:` 战斗词缀实现，且不自动迁移现有波次引用。

第 5 项的已接受后果是：旧战斗词缀效果将消失，直到内容维护者手工添加对应的 `infernal:` 配置。

## 2. 约束与已确定决策

- 保留 Maggoteers 的出生型词缀、属性倍率、标记、免疫和掉落倍率。
- 原生 `affixes:` 与 IM 的 `infernal.affixes` 使用两个独立命名空间。
- 使用 IM 的精确列表入口 `mechanizeWithAffixes`，不使用命令、自然生成监听器或“必选词缀加随机补全”入口。
- 通过反射适配器访问 IM，因为 IM 目前没有公开机械化 API，且其 `mobFactory` 为私有字段。
- 通过 `softdepend` 将 IM 作为可选运行时集成。
- 无效或不可用的 IM 行为只跳过并输出去重警告。
- 首版拒绝会替换实体或生成未追踪怪物的技能：`morph`、`mama`、`mounted`、`vexsummoner`、`ghost`。
- 本版本不实现实体后继者或召唤物的追踪协调。
- 所有由 Maggoteers 创建的 IM 怪物在 IM 处理死亡事件前注销，以禁用 IM 死亡阶段及其战利品系统。

## 3. 配置格式

每种会生成怪物的节点都可以包含可选的 `infernal` 配置块：

```yaml
steps:
  - point: 1
    type: ZOMBIE
    count: 2
    coeff: { hp: 1.5, dmg: 1.2 }
    affixes: [greedy]
    infernal:
      level: 3
      affixes: [poisonous, sprint]
    passengers:
      - type: SKELETON
        count: 1
        infernal:
          level: 1
          affixes: [archer]
    on_death:
      - type: SILVERFISH
        count: 2
        infernal:
          level: 2
          affixes: [berserk]
```

规则如下：

- `infernal.level` 为 1 到 100 的整数。
- `infernal.affixes` 为明确列出的 IM 技能 ID 列表。
- 乘客和亡语生成物永不继承父实体的 IM 配置。
- 缺少 `infernal` 时，该实体不会由本桥接层机械化。
- 原生 `affixes:` 仍只解析 `affixes.yml` 中的 ID。
- IM ID 永不传入 `AffixService`。
- 只想添加 IM 技能而不额外提高基础等级倍率时，使用 `level: 1`。
- Maggoteers 的生命值系数与 IM 等级缩放相乘。

## 4. 组件

### 4.1 `InfernalCfg`

不可变配置值，包含：

- `int level`
- `List<String> affixes`

它随配置 DTO 和运行时刷怪档案传递，不持有 Bukkit 或 IM 对象。

### 4.2 `MobYamlParser`

共享解析器负责读取以下位置的 `infernal`：

- 顶层刷怪步骤；
- 嵌套乘客；
- 嵌套亡语生成物。

解析器校验基本数据形状和等级范围，但不加载 IM 类。

### 4.3 `InfernalMobsBridge`

由 Maggoteers 自己维护的适配器，负责：

1. 在插件依赖加载顺序确定后定位已启用的 IM 插件。
2. 使用 IM 插件类加载器查找其私有 `mobFactory`。
3. 缓存精确的 `mechanizeWithAffixes` 调用入口。
4. 在可访问 IM 技能注册表时，校验配置的技能 ID。
5. 移除未知和明确禁用的技能 ID。
6. 对已经创建的 `LivingEntity` 调用 IM。
7. 在受追踪 IM 怪物死亡时，通过公开的 `CombatService.unregisterMob` 提前注销其 IM 状态。

所有反射细节都封装在该类中。配置解析和波次代码只依赖 Maggoteers 内部的小型接口，不直接依赖反射 API。

## 5. 刷怪数据流

对于主体、乘客或亡语生成物：

1. Maggoteers 创建 Bukkit 实体。
2. Maggoteers 应用人数缩放、`coeff` 和原生出生型词缀。
3. 桥接层使用配置等级和过滤后的精确技能列表调用 IM。
4. Maggoteers 最后应用显式 `equipment`，确保波次配置覆盖 IM 自动装备。
5. `WaveEngine` 追踪未改变的最终 UUID。

桥接调用必须在最终完成追踪前执行，但桥接失败不得移除或替换已生成实体。

不得执行 IM 命令，也不得随机补全技能列表。

### 5.1 截断 IM 死亡阶段

Maggoteers 在 `EntityDeathEvent` 的 `LOWEST` 优先级检查实体是否同时满足以下条件：

- 由 `WaveEngine` 追踪；
- 已通过本桥接层机械化。

满足条件时，桥接层在 IM 的 `NORMAL` 死亡监听器运行前调用 `CombatService.unregisterMob`。随后仍由 Maggoteers 正常处理清波、亡语生成和自身奖励。

提前注销会有意禁用该实体的全部 IM 死亡阶段行为：

- 等级战利品、特殊战利品和保底奖励；
- 战利品关联命令与广播；
- IM 击杀统计和死亡消息；
- IM 死亡型技能。

不能只依赖 `EntityDeathEvent#getDrops().clear()`：IM 的三类奖励通过 `World#dropItemNaturally` 直接生成物品实体，不在事件掉落列表中。提前注销是无需修改 IM 且不会误删附近其他物品的确定性方案。

## 6. 失败与兼容行为

| 情况 | 行为 |
|---|---|
| IM 缺失或未启用 | 跳过全部机械化；只警告一次 |
| 反射契约不可用 | 本次服务器会话停用桥接；只警告一次 |
| 未知技能 ID | 移除该 ID；每个 ID 只警告一次 |
| 禁用技能 ID | 移除该 ID；每个 ID 只警告一次 |
| 过滤后列表为空 | 保留普通 Maggoteers 实体 |
| 单次机械化调用抛出异常 | 保留并追踪实体；输出受限警告 |

首版禁用以下五个 ID：

- `morph`：替换被追踪实体的 UUID。
- `mama`：创建未追踪的子怪物。
- `mounted`：创建不属于 Maggoteers 乘客模型的 IM 坐骑。
- `vexsummoner`：创建未追踪的恼鬼。
- `ghost`：创建未追踪的死亡后继实体。

所有警告都必须去重，避免一个错误的大型波次刷屏服务器日志。

## 7. 删除原生战斗词缀

从 `affixes.yml` 删除以下 12 个战斗触发词缀定义：

- `toxic`
- `poisonous`
- `vampiric`
- `lifesteal`
- `sticky`
- `quicksand`
- `frostgrasp`
- `squid`
- `truck`
- `hidesuwa`
- `teleport`
- `cataclysm`

从 `waves.yml` 的所有原生 `affixes:` 列表中移除这些 ID，不自动添加 IM 替代项。

删除以下代码：

- `AffixTrigger`；
- `AffixCombatService`；
- `CombatAffixListener`；
- 对应测试和监听器注册；
- `Affix.on` 解析；
- 仅供战斗词缀使用的解析辅助方法；
- 刷怪和规划阶段用于排除战斗触发药水的分支。

简化 `Affix`，使所有剩余药水配置均为出生时效果。

保留以下能力：

- `Affix` 和 `AffixService`；
- 原生属性合成；
- 出生药水和视觉效果；
- `infected` 等标记；
- 免疫类条目；
- `greedy` 和 `fortuned`；
- 通用波次实体与档案追踪。

同步更新项目规范、配置编写参考和开发日志。旧词缀触发重构设计文档保留为历史记录，并标记为已被本设计取代。

## 8. 运行约束

- 桥接层不使用 IM 的自然生成监听器。
- 不要把 `CUSTOM` 加入 IM 自动生成原因，否则 Maggoteers 实体可能被机械化两次。
- IM 的非活跃怪物清理仍会处理已注册的炼狱怪物。测试服应关闭该清理，或将超时提高到足以覆盖长时间波次战斗，避免怪物被移除后造成错误清波。
- 后续 IM 更新可能重命名私有字段或方法。启动探测失败时必须降级为普通 Maggoteers 怪物，不能破坏游戏启动。

## 9. 测试

### 9.1 自动测试

- 解析主体、乘客和亡语生成物上的 `infernal`。
- 验证父实体与嵌套实体之间不会继承配置。
- 校验等级边界。
- 过滤未知 ID 和全部五个禁用 ID。
- 覆盖插件缺失、反射签名不兼容、调用异常和警告去重。
- 验证受追踪 IM 怪物在 IM 的死亡监听器前完成注销。
- 验证桥接层收到的是过滤后的精确 ID 列表，且没有随机添加项。
- 验证 `Affix` 不再包含触发语义。
- 扫描默认 `waves.yml`，确认不存在已删除的原生 ID。
- 运行所有现有规划、属性合成和波次测试。

### 9.2 测试服验收

- 主体只获得配置中指定的 IM 技能。
- 乘客和亡语生成物分别获得各自独立的技能列表。
- IM 缺失或技能 ID 错误时，波次仍能推进。
- 禁用技能被跳过，且每种问题只记录一次。
- 击杀本插件生成的 IM 怪物时，不产生 IM 普通、特殊或保底战利品，也不执行 IM 死亡奖励命令、广播、统计或死亡技能。
- `level: 1` 使用 IM 基础等级倍率。
- 更高 IM 等级与 Maggoteers 系数相乘。
- 波次显式装备最终覆盖 IM 自动装备。
- 主体、骑乘组合、乘客和亡语生成物死亡后都不会卡住调度器。

## 10. 验收标准

1. 内容维护者可通过 `waves.yml` 为三种怪物生成节点精确指定受支持的 IM 技能。
2. 不修改任何 MCZJUinfernalMobs 源文件。
3. 桥接层不随机添加 IM 技能。
4. IM 缺失或不兼容时，降级为普通且仍受追踪的怪物。
5. 会替换实体或生成召唤物的技能被安全拒绝。
6. 旧 `on:` 解析器、监听器、服务、枚举、配置条目和波次引用全部移除。
7. 原生出生型词缀和倍率词缀继续工作。
8. 本插件生成的 IM 怪物不会进入 IM 死亡奖励、统计或死亡技能流程。
9. 自动测试和 Maven 构建通过。

## 11. 取代关系

本设计取代 `2026-07-26-affix-trigger-refactor-design.md` 中描述的运行时架构。该文档继续作为历史记录保留，但其中的 `on:` 触发实现将被有意退役。