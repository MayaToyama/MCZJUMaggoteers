---
name: maggoteers-build-deploy
description: >-
  Build and deploy The Maggoteers plugin using IntelliJ bundled Maven (JDK 25),
  run tests, package JAR, and copy artifacts to the local Paper test server
  (E:/MCpaper). Use when the user asks to mvn test/package, deploy to test
  server, sync to MCpaper, or build after Java/config changes.
---

# Maggoteers 构建与测试服部署

## 何时用

- 改完 Java 或需要验证单测 → **test**
- 要上测试服 → **test + package + 拷贝**
- 用户说「拷到测试服」「部署 MCpaper」「用 IntelliJ 的 mvn」

配置 YAML 内容规范见 [maggoteers-plugin-config](../maggoteers-plugin-config/SKILL.md)。路径与命令细节见 [reference.md](reference.md)。

## 默认流程（Agent 必须实际执行）

1. **测试**（改逻辑后必跑；仅纯文档可跳过）  
2. **打包**（`-DskipTests` 仅当刚跑过全量 test）  
3. **部署**（jar + 常用配置，见 reference）  
4. **提醒用户重启 Paper**（换 jar 必须重启；`reload` 不可靠）

## 一键脚本（推荐）

在项目根目录执行 PowerShell：

```powershell
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1
```

可选参数：

```powershell
# 只测不部署
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SkipDeploy

# 部署并同步 maps/（大资源，按需；items/*.yml 已默认同步）
.cursor/skills/maggoteers-build-deploy/scripts/build-and-deploy.ps1 -SyncAssets
```

## 手动命令（脚本失败时）

见 [reference.md](reference.md) 中的 `MVN_CMD`、测试服路径与同步文件列表。

## 部署后

- 测试服：`E:/MCpaper`（WSL：`/mnt/e/MCpaper`）
- 依赖：同目录需 **MCZJUGameCore 1.0.7**、**MCZJUItemCreator**、Paper **26.2**
- Smoke 入口：`/mgc join maggoteers`

## 失败处理

| 现象 | 处理 |
|------|------|
| `mvn` 找不到 | 用 reference 里的 IntelliJ `mvn.cmd` 绝对路径，勿假设 PATH 有 mvn |
| 依赖解析失败（ItemCreator） | 本机需能解析 `pom.xml` 的 jitpack/paper 仓库；在 IntelliJ Maven 面板试一次 Reload |
| 测试服目录不存在 | 确认 `E:/MCpaper/plugins` 存在或改 `reference.md` / 脚本变量 |
