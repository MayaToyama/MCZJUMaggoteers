# Maggoteers 构建 / 部署参考

## IntelliJ 自带 Maven

优先 **2025.3.2**，不存在则用 **2025.1.1.1**：

| 版本 | `mvn.cmd` |
|------|-----------|
| 2025.3.2 | `E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd` |
| 2025.1.1.1 | `E:/Intellij_Idea/IntelliJ IDEA 2025.1.1.1/plugins/maven/lib/maven3/bin/mvn.cmd` |

JDK：项目 **25**（`.idea/misc.xml` → `project-jdk-name="25"`）。Maven 继承 IDEA 工程 JDK 即可。

## 项目与产物

| 项 | 路径 |
|----|------|
| 项目根 | `E:/Intellij_Idea/plugins/MCPlugin` |
| 输出 jar | `target/Maggoteers-0.1.0-SNAPSHOT.jar`（版本以 `pom.xml` 为准） |

## 测试服

| 项 | 路径 |
|----|------|
| 服务器根 | `E:/MCpaper` |
| 插件 jar | `E:/MCpaper/plugins/Maggoteers-0.1.0-SNAPSHOT.jar` |
| 插件数据 | `E:/MCpaper/plugins/Maggoteers/` |

### 默认同步的配置（每次部署）

从 `src/main/resources/` 复制到 `plugins/Maggoteers/`：

- `config.yml`
- `waves.yml`
- `rewards.yml`
- `affixes.yml`
- `collectibles.yml`
- **`items/*.yml`**（含 `items/maggoteers.yml`——沉默/净化等武器配置；**必须**与 jar 同发，否则服上旧假 amp-255 会被硬失败拦下）

**不**默认覆盖：`maps/`（体积大；服务器上可能已有定制）。需要时用 `-SyncAssets` 或手动复制。

> jar 内 `saveResource(..., false)` 行为不变：服上已存在的 YAML **不会被 jar 启动覆盖**；显式复制才会更新。换药水清除/免疫相关逻辑时，至少确认服上 `rewards.yml` 与 `items/maggoteers.yml` 已同步。

## PowerShell 手动命令

```powershell
$root = "E:/Intellij_Idea/plugins/MCPlugin"
$mvn  = "E:/Intellij_Idea/IntelliJ IDEA 2025.3.2/plugins/maven/lib/maven3/bin/mvn.cmd"
Set-Location $root
& $mvn test
& $mvn package -DskipTests
Copy-Item "$root/target/Maggoteers-0.1.0-SNAPSHOT.jar" "E:/MCpaper/plugins/" -Force
$data = "E:/MCpaper/plugins/Maggoteers"
foreach ($f in "config.yml","waves.yml","rewards.yml","affixes.yml","collectibles.yml") {
  Copy-Item "$root/src/main/resources/$f" "$data/$f" -Force
}
New-Item -ItemType Directory -Force -Path "$data/items" | Out-Null
Copy-Item "$root/src/main/resources/items/*.yml" "$data/items/" -Force
```

## WSL 构建（备选）

仅当 Windows 上 Maven 不可用时；需本机 WSL 已装 Maven 且能解析依赖：

```bash
cd /mnt/e/Intellij_Idea/plugins/MCPlugin && mvn test && mvn package -DskipTests
```

部署 jar 仍建议在 Windows 侧 `Copy-Item` 到 `/mnt/e/MCpaper/plugins/`。
