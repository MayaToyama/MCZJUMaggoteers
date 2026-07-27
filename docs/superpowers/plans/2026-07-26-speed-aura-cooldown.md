# Speed / Aura FX / Item Cooldown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or subagent-driven-development.

**Goal:** Fix solo mob hyper-speed, throttle AURA carrier particles via config, add ItemCreator `useCooldown` on three weapons.

**Architecture:** Narrow `MountedSquadRegistry` to passenger steps only; single `AuraService.refresh` path with per-preset interval before `MagicFxPresets.play`; YAML-only native CD display.

**Tech Stack:** Paper 26.2, Java 25, Maven.

## Global Constraints

- Do not use `HumanEntity.setCooldown(Material)` for weapon CD (D2).
- Handler `COOLDOWN_SEC` unchanged in v1.

---

### Task 1: Mob registry scope

**Files:** `MobFactory.java` — remove `else { MountedSquadRegistry.register(mount, mount); }`.

### Task 2: Aura refresh + throttle

**Files:** `EffectListener.java`, `AuraService.java`, `config.yml`

### Task 3: Item useCooldown

**Files:** `items/maggoteers.yml`

**Verify:** `mvn test package`
