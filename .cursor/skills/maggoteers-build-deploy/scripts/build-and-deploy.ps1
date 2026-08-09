# Maggoteers: IntelliJ Maven test + package + deploy to E:\MCpaper
param(
    [switch]$SkipDeploy,
    [switch]$SyncAssets
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
if (-not (Test-Path (Join-Path $ProjectRoot "pom.xml"))) {
    $ProjectRoot = "E:\Intellij_Idea\plugins\MCPlugin"
}

$MvnCandidates = @(
    "E:\Intellij_Idea\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd",
    "E:\Intellij_Idea\IntelliJ IDEA 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
)
$Mvn = $MvnCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $Mvn) {
    Write-Error "IntelliJ mvn.cmd not found. See .cursor/skills/maggoteers-build-deploy/reference.md"
}

# JDK 25 required; prefer Minecraft runtime when current Java is not 25.
$McJdk = Join-Path $env:APPDATA ".minecraft\runtime\java-runtime-epsilon"
$needsMcJdk = $false
if (Test-Path "$McJdk\bin\javac.exe") {
    if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
        $needsMcJdk = $true
    } else {
        $ver = cmd /c "`"$env:JAVA_HOME\bin\java.exe`" -version 2>&1"
        if ($ver -notmatch 'version "25') { $needsMcJdk = $true }
    }
}
if ($needsMcJdk) {
    $env:JAVA_HOME = $McJdk
    $env:PATH = "$McJdk\bin;$env:PATH"
    Write-Host "JAVA_HOME -> $McJdk (Minecraft runtime JDK 25)"
}

$ServerPlugins = "E:\MCpaper\plugins"
$ServerData    = Join-Path $ServerPlugins "Maggoteers"
$ResRoot       = Join-Path $ProjectRoot "src\main\resources"

Push-Location $ProjectRoot
try {
    Write-Host "==> mvn test"
    & $Mvn test
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "==> mvn package -DskipTests"
    & $Mvn -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $jar = Get-ChildItem -Path (Join-Path $ProjectRoot "target") -Filter "Maggoteers-*.jar" |
        Where-Object { $_.Name -notmatch "original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) { Write-Error "No target/Maggoteers-*.jar found" }

    Write-Host ("Built: {0} size={1}" -f $jar.FullName, $jar.Length)

    if ($SkipDeploy) {
        Write-Host "SkipDeploy: not copying to test server."
        exit 0
    }

    if (-not (Test-Path $ServerPlugins)) {
        Write-Error "Test server missing: $ServerPlugins"
    }

    Copy-Item $jar.FullName (Join-Path $ServerPlugins $jar.Name) -Force
    Write-Host ("JAR -> {0}" -f (Join-Path $ServerPlugins $jar.Name))

    New-Item -ItemType Directory -Force -Path $ServerData | Out-Null
    foreach ($f in @("config.yml", "waves.yml", "rewards.yml", "affixes.yml", "collectibles.yml")) {
        $src = Join-Path $ResRoot $f
        if (Test-Path $src) {
            Copy-Item $src (Join-Path $ServerData $f) -Force
            Write-Host "Synced $f"
        }
    }

    $itemsDest = Join-Path $ServerData "items"
    New-Item -ItemType Directory -Force -Path $itemsDest | Out-Null
    $itemsSrc = Join-Path $ResRoot "items"
    if (Test-Path $itemsSrc) {
        Get-ChildItem $itemsSrc -File -Filter "*.yml" | ForEach-Object {
            Copy-Item $_.FullName (Join-Path $itemsDest $_.Name) -Force
        }
        Write-Host "Synced items/*.yml"
    }

    if ($SyncAssets) {
        $mapsSrc = Join-Path $ResRoot "maps"
        $mapsDest = Join-Path $ServerData "maps"
        if (Test-Path $mapsSrc) {
            if (Test-Path $mapsDest) { Remove-Item $mapsDest -Recurse -Force }
            Copy-Item $mapsSrc $mapsDest -Recurse -Force
            Write-Host "Synced maps/"
        }
    }

    Write-Host "Done. Restart Paper on E:\MCpaper before smoke test."
}
finally {
    Pop-Location
}
