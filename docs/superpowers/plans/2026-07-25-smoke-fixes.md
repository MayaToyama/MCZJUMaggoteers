# Smoke Fixes + Mounted Step Implementation Plan

> **For agentic workers:** Implement task-by-task per spec `docs/superpowers/specs/2026-07-25-smoke-fixes-design.md`.

**Goal:** Fix smoke issues (AURA, FX, night vision, invisible marker) and deliver config-driven mounted steps with nested passengers + controller AI.

**Architecture:** Recursive `PassengerCfg`/`PassengerSpawn`; delayed mount tree; `MountedSquadRegistry` synced in WaveEngine 5-tick loop; `AttributeModifierKeys` for Paper 26.2.

**Tech Stack:** Paper 26.2, Java 25, Maven.

## Global Constraints

- Paper API 26.2; no mount type whitelist in Java.
- Nested passengers max depth 8.
- Camel/CAMEL_HUSK direct passenger limit 2; horse/strider 1.

---

### Task 1: AttributeModifierKeys + AURA
- Create `effect/AttributeModifierKeys.java`
- Update `AuraService`, `EffectService`

### Task 2: Recursive passenger model + WavesConfig
- `PassengerCfg`, `PassengerSpawn`, `parsePassengers`, `RunPlanner`

### Task 3: MobFactory mount tree + visuals + MountSetup
- Delayed mount, `MountPassengerLimits`, invisible bottle

### Task 4: MountedSquad + WaveEngine tracking/AI
- `MountedSquadRegistry`, `MountedSquadAiService`, `ControllerResolver`
- `WaveEngine.spawnStep` two-phase track

### Task 5: waves.yml + config + game start night vision + spiral FX

### Task 6: mvn package + dev-log
