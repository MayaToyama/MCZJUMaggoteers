# AURA 团队/对敌光环 — 实现计划（2026-07-24）

Spec: `docs/superpowers/specs/2026-07-24-team-aura-effect-design.md`

## Done in this pass

1. `Effect.AURA` + `AuraParams` + `EffectKeys` 扩展
2. `AuraService.refresh`（1s tick，友/敌 grant，HEAL 脉冲，出圈剥离）
3. `RewardOption` 解析 grant/targets/expiry；`RewardService` 写入 expiry
4. `EffectService`：AURA 不直接 applyDerived；`ON_INTERACT` 激活光环
5. 示例奖励：`a1s_war_banner` / `a1s_medic_field` / `a1s_miasma`
6. `AuraParamsTest`

## Verify

- `mvn test`
- 局内选医疗力场/战旗/瘴气，双人站位测叠加与出圈

## Follow-ups

- `debug aura` 命令
- 怪物药水出圈 `removePotionEffect`
- `CLAUDE.md` §10.2 补 AURA 一行
