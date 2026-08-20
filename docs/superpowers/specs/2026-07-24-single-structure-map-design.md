# 单结构 NBT 地图加载设计

**日期：** 2026-07-24  
**状态：** 已批准并实现（Plan 10）

---

## 目标

将每层地图从 **4 象限 NBT**（`nw/sw/ne/se.nbt` + 固定 32 格偏移）改为 **单个结构文件** `structure.nbt`，仍按 Act 分目录、仍懒粘贴进层。

## 决策

| # | 决策 | 取值 |
|---|---|---|
| 1 | 范围 | 每张图一个 `structure.nbt`（非三层共用） |
| 2 | 粘贴对齐 | 结构导出**原点角**对齐 `act_origins` 层原点；结构往 +X/+Y/+Z 展开 |
| 3 | 文件名 | 固定 `structure.nbt`；**硬切换**，不再识别四象限文件 |
| 4 | 缺文件 | `fallback=true` 时仍铺 **64×64 玻璃平台**（与现行为一致） |
| 5 | 实现策略 | **最小改动**：只改 `MapRepository` + `StructurePaster`；调用方签名不变 |

铁律不变：绝对坐标不入配置；`points.yml` 相对坐标运行时 `act_origins + 相对`。

## 目录约定

```
plugins/Maggoteers/maps/
  actN/<mapId>/
    structure.nbt       # 唯一结构（结构方块导出）
    points.yml          # playerSpawn + spawnPoints（相对层原点）
    special_waves.yml   # 可选，不变
```

jar 内示例地图可不带默认 nbt；运维把导出文件放进对应目录即可。

## 组件改动

### `MapRepository.MapEntry`

- 去掉 `Set<String> nbtFiles` 与 `hasNbt(String quad)`。
- 改为 `boolean hasStructure`（`load` 时 `new File(mapDir, "structure.nbt").isFile()`）。

### `StructurePaster.pasteAct`

1. 若存在 `structure.nbt`：`Bukkit.getStructureManager().loadStructure(file)` → `place(world, BlockVector(ox, oy, oz), true, NONE, NONE, 0, 1.0f, Random)`。
2. 否则且 `fallback`：`buildPlatform`（64×64 玻璃，逻辑保持）。
3. 删除四象限循环与 `offX`/`offZ`。

### 调用方

`ActSpawnHelper`、`WaveScheduler` 继续调 `pasteAct(...)`；无需改懒粘贴时机或 `act_origins`。

## 坐标注意

粘贴后结构占据层原点的 **正 X/Z 象限一侧**（原点角对齐）。现有示例 `points.yml` 含负 X/Z，是为居中玻璃平台写的；放入真实 `structure.nbt` 后须按结构原点重写相对坐标，使点落在结构包围盒内。

## 文档

同步更新 `CLAUDE.md` §5.2（及代码注释中「4 象限」表述）为单文件约定。

## 验收

1. 无 `structure.nbt` → 玻璃平台，可开局/进层。
2. 放入 `structure.nbt` → 该层只粘贴一次，原点对齐层原点。
3. 出生点/刷怪点仍正确为 `原点 + points.yml 相对值`。
4. `mvn test` / `mvn package` 通过（若有相关单测则更新期望）。

## 不在范围

- 四象限过渡兼容
- 可配置文件名 / `paste_offset`
- 按结构尺寸自适应兜底平台
- 改懒粘贴策略或 `act_origins`
- 为仓库提交示例大型 `.nbt` 二进制（由运维放置）
