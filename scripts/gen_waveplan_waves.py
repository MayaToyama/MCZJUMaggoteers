#!/usr/bin/env python3
"""Generate src/main/resources/waves.yml from waveplan spec."""
from __future__ import annotations
import importlib.util
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/waves.yml"

NORMAL = "clearReward: [ { item: maggoteers:currency_normal, amount: 2 } ]"
STRONG = "clearReward: [ { item: maggoteers:currency_normal, amount: 4 } ]"
BOSS = "clearReward: [ { item: maggoteers:currency_boss, amount: 1 } ]"

HEADER = textwrap.dedent("""\
    # waveplan implementation (2026-07-26). Single source for strategies and pools.
    # delay = step pre-wait seconds; coeff may include hp/dmg/speed/scale/follow_range
    # Optional IM affixes (steps / passengers / on_death, independent, no inheritance):
    # infernal:
    #   level: 1
    #   affixes: [poisonous, sprint]
    # Disabled IM skills: morph, mama, mounted, vexsummoner, ghost
    # IM missing/unknown/disabled skills: warning once, entity stays vanilla.
    pools:
      act1:
        weak:
          - { strategy: w1_bug_buddies, weight: 2 }
          - { strategy: w1_patrol_riders, weight: 2 }
          - { strategy: w1_accident, weight: 2 }
          - { strategy: w1_symbiosis, weight: 2 }
          - { strategy: w1_symptom, weight: 2 }
          - { strategy: w1_herd, weight: 2 }
        strong:
          - { strategy: s1_resort_ghosts, weight: 2 }
          - { strategy: s1_low_altitude, weight: 2 }
          - { strategy: s1_vent_breeze, weight: 2 }
        boss:
          - { strategy: b1_colossus, weight: 2 }
          - { strategy: b1_eternal_rest, weight: 2 }
          - { strategy: b1_bandit_chief, weight: 2 }
      act2:
        weak:
          - { strategy: w2_merchant_convoy, weight: 2 }
          - { strategy: w2_endless_spear, weight: 2 }
          - { strategy: w2_fireworks, weight: 2 }
          - { strategy: w2_lord_legacy, weight: 2 }
          - { strategy: w2_nest, weight: 2 }
          - { strategy: w2_territory, weight: 2 }
        strong:
          - { strategy: s2_ice_shadows, weight: 2 }
          - { strategy: s2_migration, weight: 2 }
          - { strategy: s2_forbidden, weight: 2 }
          - { strategy: s2_chessboard, weight: 2 }
          - { strategy: s2_crimson_tunnel, weight: 2 }
          - { strategy: s2_controversy, weight: 2 }
        boss:
          - { strategy: b2_silent_guard, weight: 2 }
          - { strategy: b2_breath, weight: 2 }
          - { strategy: b2_frost_farewell, weight: 2 }
      act3:
        weak: []
        strong:
          - { strategy: s3_surprise_factory, weight: 2 }
          - { strategy: s3_across_fire, weight: 2 }
          - { strategy: s3_out_of_control, weight: 2 }
          - { strategy: s3_chaos_facade, weight: 2 }
          - { strategy: s3_keep_out, weight: 2 }
          - { strategy: s3_fire_water, weight: 2 }
          - { strategy: s3_institution, weight: 2 }
          - { strategy: s3_witch_pact, weight: 2 }
          - { strategy: s3_rhine_guard, weight: 2 }
        boss:
          - { strategy: b3_into_eternal, weight: 2 }
          - { strategy: b3_sentinel, weight: 2 }
          - { strategy: b3_human_radiance, weight: 2 }

    strategies:
""")

def camel_squad(point: str, delay: int = 0) -> str:
    return f"""      - point: "{point}"
        type: CAMEL_HUSK
        count: 1
        delay: {delay}
        passengers:
          - type: ZOMBIE
            count: 1
            affixes: [plastic]
            equipment:
              - {{ slot: HEAD, item: maggoteers:mob_iron_helmet_ub10 }}
              - {{ slot: HAND, item: maggoteers:mob_iron_spear }}
          - type: SKELETON
            count: 1
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_bow_power1 }}"""

def strider_stack(zombie: bool, delay: int, z_coeff: str = "", sk_coeff: str = "") -> str:
    inner = "ZOMBIE" if zombie else "SKELETON"
    eq = "mob_gold_spear" if zombie else "mob_bow"
    inner_line = f"""              - type: {inner}
                count: 1
                coeff: {{ dmg: 2.0{z_coeff} }}
                equipment:
                  - {{ slot: HAND, item: maggoteers:{eq} }}"""
    return f"""      - point: "1"
        type: STRIDER
        count: 2
        coeff: {{ speed: 2.0 }}
        delay: {delay}
        passengers:
          - type: STRIDER
            count: 1
            passengers:
{inner_line}"""

def slime_step(point: str, delay: int, on_death_yaml: str) -> str:
    return f"""      - point: "{point}"
        type: SLIME
        count: 4
        affixes: [infected]
        delay: {delay}
        on_death:
{on_death_yaml}"""

BLIND_VARIANTS = [
    "          - { type: ZOMBIE, count: 1, coeff: { speed: 2.0, dmg: 2.0 } }",
    "          - { type: SKELETON, count: 1, coeff: { hp: 3.0 } }",
    "          - { type: CREEPER, count: 1, coeff: { hp: 20.0, speed: 2.0 } }",
    "          - { type: SPIDER, count: 1, coeff: { scale: 0.4, hp: 1.5 } }",
    "          - { type: WITHER_SKELETON, count: 1 }",
    "          - { type: ZOMBIFIED_PIGLIN, count: 1, coeff: { hp: 2.0 }, equipment: [ { slot: HAND, item: maggoteers:mob_gold_spear } ] }",
    "          - { type: ENDERMITE, count: 5 }",
    "          - { type: CAVE_SPIDER, count: 2 }",
    "          - { type: ZOMBIFIED_PIGLIN, count: 1 }",
    "          - { type: BOGGED, count: 1 }",
    "          - { type: STRAY, count: 1, coeff: { speed: 2.0 }, equipment: [ { slot: HAND, item: maggoteers:mob_bow_punch2 } ] }",
    "          - { type: HUSK, count: 1, coeff: { hp: 5.0, speed: 0.8, dmg: 2.0 } }",
    "          - { type: DROWNED, count: 1, equipment: [ { slot: HAND, item: maggoteers:mob_trident } ] }",
]

RADIANCE_VARIANTS = [
    "          - { type: ZOMBIE, count: 1, coeff: { speed: 2.0, dmg: 4.0, hp: 5.0 } }",
    "          - { type: SKELETON, count: 1, coeff: { hp: 3.0 }, equipment: [ { slot: HAND, item: maggoteers:mob_bow_flame_punch } ] }",
    "          - { type: CREEPER, count: 1, coeff: { hp: 20.0, speed: 2.0 }, affixes: [blinding] }",
    "          - { type: SPIDER, count: 1, coeff: { scale: 0.8, hp: 2.0 } }",
    "          - { type: WITHER_SKELETON, count: 1, coeff: { hp: 4.0 } }",
    "          - { type: ZOMBIFIED_PIGLIN, count: 1, coeff: { hp: 2.0, speed: 1.5 }, equipment: [ { slot: HAND, item: maggoteers:mob_gold_spear } ] }",
    "          - { type: ENDERMITE, count: 5 }",
    "          - { type: CAVE_SPIDER, count: 2 }",
    "          - { type: ZOGLIN, count: 1 }",
    "          - { type: BOGGED, count: 1, coeff: { hp: 2.0, speed: 1.5 } }",
    "          - { type: STRAY, count: 1, coeff: { speed: 2.0 }, affixes: [antifreeze], equipment: [ { slot: HAND, item: maggoteers:mob_bow_punch2 } ] }",
    "          - { type: HUSK, count: 1, coeff: { hp: 10.0, speed: 0.8, dmg: 5.0 } }",
    "          - { type: DROWNED, count: 1, coeff: { hp: 4.0 }, equipment: [ { slot: HAND, item: maggoteers:mob_trident } ] }",
]

def blind_box_steps() -> str:
    lines = []
    vi = 0
    for pt in "12345":
        for _ in range(2):  # 2 batches x 4 = 8 per point... need 10 per point
            lines.append(slime_step(pt, 1 if lines else 0, BLIND_VARIANTS[vi % 13]))
            vi += 1
        lines.append(slime_step(pt, 1, BLIND_VARIANTS[vi % 13]))
        vi += 1
    return "\n".join(lines)

def radiance_point_steps(point: str) -> str:
    lines = []
    for batch in range(5):
        v = RADIANCE_VARIANTS[(int(point) + batch) % 13]
        delay = 0 if batch == 0 else 20
        lines.append(f"""      - point: "{point}"
        type: SLIME
        count: 10
        affixes: [infected]
        delay: {delay}
        on_death:
{v}""")
    return "\n".join(lines)

STRATEGIES = {}

# %% Act 1 weak %%
STRATEGIES["w1_bug_buddies"] = f"""  w1_bug_buddies:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: SPIDER, count: 4, delay: 0 }}
      - {{ point: "1", type: ENDERMITE, count: 6, delay: 2 }}
      - {{ point: "2", type: SPIDER, count: 4, delay: 0 }}
      - {{ point: "2", type: CAVE_SPIDER, count: 6, delay: 2 }}
      - {{ point: "5", type: SKELETON, count: 3, affixes: [plastic], delay: 1 }}
"""

STRATEGIES["w1_patrol_riders"] = f"""  w1_patrol_riders:
    repeat: 1
    {NORMAL}
    steps:
      - point: "1"
        type: SPIDER
        count: 2
        delay: 0
        coeff: {{ speed: 1.5 }}
        passengers:
          - {{ type: SKELETON, count: 1 }}
      - point: "3"
        type: SPIDER
        count: 2
        delay: 10
        coeff: {{ speed: 1.5 }}
        passengers:
          - {{ type: SKELETON, count: 1 }}
      - point: "5"
        type: SPIDER
        count: 2
        delay: 10
        coeff: {{ speed: 1.5 }}
        passengers:
          - {{ type: SKELETON, count: 1 }}
"""

STRATEGIES["w1_accident"] = f"""  w1_accident:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: SKELETON, count: 5, delay: 0 }}
      - {{ point: "2", type: SKELETON, count: 1, delay: 0 }}
      - {{ point: "2", type: SKELETON, count: 4, delay: 2 }}
      - {{ point: "5", type: SKELETON, count: 1, delay: 0 }}
      - {{ point: "5", type: SKELETON, count: 4, delay: 4 }}
"""

STRATEGIES["w1_symbiosis"] = f"""  w1_symbiosis:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "3", type: ENDERMITE, count: 10, delay: 0 }}
      - {{ point: "4", type: ENDERMITE, count: 10, delay: 2 }}
      - {{ point: boss, type: VINDICATOR, count: 1, coeff: {{ hp: 2.0 }}, delay: 0 }}
"""

STRATEGIES["w1_symptom"] = f"""  w1_symptom:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: ZOMBIE, count: 4, delay: 0 }}
      - {{ point: "2", type: ZOMBIE, count: 4, delay: 2 }}
      - point: "3"
        type: SLIME
        count: 4
        affixes: [infected]
        delay: 0
        on_death:
          - {{ type: ENDERMITE, count: 3 }}
      - point: "4"
        type: SLIME
        count: 4
        affixes: [infected]
        delay: 4
        on_death:
          - {{ type: ENDERMITE, count: 3 }}
"""

STRATEGIES["w1_herd"] = f"""  w1_herd:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: ZOMBIE, count: 8, delay: 0 }}
      - {{ point: "2", type: ZOMBIE, count: 8, delay: 2 }}
      - {{ point: "3", type: ZOMBIE, count: 8, delay: 2 }}
      - {{ point: "4", type: ZOMBIE, count: 8, delay: 2 }}
      - {{ point: "5", type: ZOMBIE, count: 8, coeff: {{ speed: 1.2 }}, delay: 2 }}
"""

# Act1 strong
STRATEGIES["s1_resort_ghosts"] = f"""  s1_resort_ghosts:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: ZOMBIE
        count: 4
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_iron_spear }}
      - point: "2"
        type: ZOMBIE
        count: 4
        delay: 5
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_iron_spear }}
      - point: "3"
        type: ZOMBIFIED_PIGLIN
        count: 4
        delay: 1
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_gold_spear }}
"""

STRATEGIES["s1_low_altitude"] = f"""  s1_low_altitude:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: STRIDER
        count: 2
        coeff: {{ speed: 2.0 }}
        delay: 0
        passengers:
          - type: STRIDER
            count: 1
            passengers:
              - type: ZOMBIE
                count: 1
                coeff: {{ dmg: 2.0 }}
                equipment:
                  - {{ slot: HAND, item: maggoteers:mob_gold_spear }}
      - point: "2"
        type: STRIDER
        count: 2
        coeff: {{ speed: 2.0 }}
        delay: 10
        passengers:
          - type: STRIDER
            count: 1
            passengers:
              - type: ZOMBIE
                count: 1
                coeff: {{ dmg: 2.0 }}
                equipment:
                  - {{ slot: HAND, item: maggoteers:mob_gold_spear }}
      - point: "5"
        type: STRIDER
        count: 2
        coeff: {{ speed: 2.0 }}
        delay: 10
        passengers:
          - type: STRIDER
            count: 1
            passengers:
              - type: SKELETON
                count: 1
                coeff: {{ dmg: 2.0 }}
                equipment:
                  - {{ slot: HAND, item: maggoteers:mob_bow }}
"""

STRATEGIES["s1_vent_breeze"] = f"""  s1_vent_breeze:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: ZOMBIE
        count: 4
        coeff: {{ speed: 1.5 }}
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_gold_spear }}
      - point: "2"
        type: ZOMBIE
        count: 4
        coeff: {{ speed: 1.5 }}
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_iron_axe }}
      - {{ point: "4", type: BREEZE, count: 5, delay: 5 }}
"""

# Act1 map
STRATEGIES["w1_desert_four_camels"] = f"""  w1_desert_four_camels:
    repeat: 1
    {NORMAL}
    steps:
{camel_squad("1", 0)}
{camel_squad("2", 0)}
{camel_squad("3", 0)}
{camel_squad("4", 0)}
"""

STRATEGIES["w1_desert_prisoners"] = f"""  w1_desert_prisoners:
    repeat: 1
    {NORMAL}
    steps:
      - point: "5"
        type: HUSK
        count: 30
        coeff: {{ hp: 2.0 }}
        
        delay: 1
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_gold_shovel }}
"""

STRATEGIES["w1_desert_plague"] = f"""  w1_desert_plague:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: CREEPER, count: 3, coeff: {{ scale: 0.7, speed: 1.5 }}, delay: 0 }}
      - {{ point: "2", type: CREEPER, count: 3, coeff: {{ scale: 0.7, speed: 1.5 }}, delay: 2 }}
      - {{ point: "3", type: CREEPER, count: 3, coeff: {{ scale: 0.5, speed: 1.5 }}, delay: 2 }}
      - {{ point: "4", type: CREEPER, count: 3, coeff: {{ scale: 0.5, speed: 1.5 }}, delay: 2 }}
      - {{ point: "5", type: CREEPER, count: 1, coeff: {{ scale: 0.5, speed: 1.5 }}, affixes: [invisible], delay: 2 }}
"""

STRATEGIES["w1_jungle_shadows"] = f"""  w1_jungle_shadows:
    repeat: 2
    {NORMAL}
    steps:
      - {{ point: "1", type: DROWNED, count: 3, delay: 0 }}
      - {{ point: "2", type: DROWNED, count: 3, delay: 4 }}
      - {{ point: "3", type: BOGGED, count: 4, delay: 15 }}
      - {{ point: "4", type: BOGGED, count: 4, delay: 3 }}
"""

STRATEGIES["w1_jungle_spider_swap"] = f"""  w1_jungle_spider_swap:
    repeat: 1
    {NORMAL}
    steps:
      - point: "1"
        type: CAVE_SPIDER
        count: 3
        delay: 0
        passengers:
          - {{ type: BOGGED, count: 1 }}
      - point: "2"
        type: CAVE_SPIDER
        count: 3
        delay: 4
        passengers:
          - {{ type: BOGGED, count: 1 }}
      - point: "3"
        type: CAVE_SPIDER
        count: 3
        delay: 4
        passengers:
          - {{ type: WITCH, count: 1 }}
      - point: "4"
        type: CAVE_SPIDER
        count: 3
        delay: 4
        passengers:
          - {{ type: WITCH, count: 1 }}
"""

STRATEGIES["w1_jungle_spider_falls"] = f"""  w1_jungle_spider_falls:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "5", type: CAVE_SPIDER, count: 40, delay: 1 }}
"""

# Act1 bosses
STRATEGIES["b1_colossus"] = f"""  b1_colossus:
    repeat: 1
    {BOSS}
    steps:
      - {{ point: boss, type: IRON_GOLEM, count: 1, coeff: {{ hp: 10.0, speed: 1.3, scale: 1.2 }}, affixes: [mechanical], delay: 0 }}
"""

STRATEGIES["b1_eternal_rest"] = f"""  b1_eternal_rest:
    repeat: 1
    {BOSS}
    steps:
      - point: "1"
        type: ZOMBIE
        count: 5
        delay: 0
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_iron_helmet_ub10 }}
          - {{ slot: HAND, item: maggoteers:mob_iron_sword_ub10 }}
      - point: "2"
        type: ZOMBIE
        count: 5
        delay: 10
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_iron_helmet_ub10 }}
          - {{ slot: HAND, item: maggoteers:mob_iron_sword_ub10 }}
      - point: "5"
        type: SKELETON
        count: 5
        delay: 10
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_iron_helmet_ub10 }}
          - {{ slot: HAND, item: maggoteers:mob_bow }}
      - point: boss
        type: SKELETON
        count: 1
        coeff: {{ hp: 30.0 }}
        affixes: [eternal_curse, mechanical]
        delay: 15
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_iron_helmet_ub10 }}
          - {{ slot: CHEST, item: maggoteers:mob_iron_chest_ub10 }}
          - {{ slot: LEGS, item: maggoteers:mob_iron_legs_ub10 }}
          - {{ slot: FEET, item: maggoteers:mob_iron_boots_ub10 }}
          - {{ slot: HAND, item: maggoteers:mob_iron_sword_reach }}
"""

STRATEGIES["b1_bandit_chief"] = f"""  b1_bandit_chief:
    repeat: 1
    {BOSS}
    steps:
      - point: "1"
        type: STRIDER
        count: 5
        coeff: {{ speed: 2.0 }}
        delay: 0
        passengers:
          - type: STRIDER
            count: 1
            passengers:
              - {{ type: PILLAGER, count: 1 }}
      - point: "2"
        type: STRIDER
        count: 5
        coeff: {{ speed: 2.0 }}
        delay: 5
        passengers:
          - type: STRIDER
            count: 1
            passengers:
              - {{ type: PILLAGER, count: 1 }}
      - point: "5"
        type: STRIDER
        count: 2
        coeff: {{ speed: 2.0 }}
        delay: 10
        passengers:
          - type: STRIDER
            count: 1
            passengers:
              - {{ type: VINDICATOR, count: 1 }}
      - point: boss
        type: STRIDER
        count: 1
        coeff: {{ hp: 5.0, speed: 2.0 }}
        delay: 5
        passengers:
          - type: STRIDER
            count: 1
            coeff: {{ hp: 5.0 }}
            passengers:
              - type: PILLAGER
                count: 1
                coeff: {{ hp: 10.0, speed: 0.5 }}
                
                equipment:
                  - {{ slot: HAND, item: maggoteers:mob_crossbow_power3 }}
"""

# Continue with Act2 - I'll add key strategies in the script...
# Due to length, append remaining via function

def act2_strategies():
    s = {}
    s["w2_merchant_convoy"] = f"""  w2_merchant_convoy:
    repeat: 1
    {NORMAL}
    steps:
      - point: "1"
        type: CREEPER
        count: 9
        coeff: {{ hp: 3.0 }}
        affixes: [infected]
        delay: 0
        on_death:
          - {{ type: TNT, count: 1 }}
      - point: "1"
        type: CREEPER
        count: 9
        coeff: {{ hp: 3.0 }}
        affixes: [infected]
        delay: 10
        on_death:
          - {{ type: TNT, count: 1 }}
      - {{ point: "2", type: VINDICATOR, count: 1, coeff: {{ hp: 5.0, dmg: 10.0, speed: 0.5, scale: 2.0 }}, delay: 0 }}
"""
    s["w2_endless_spear"] = f"""  w2_endless_spear:
    repeat: 1
    {NORMAL}
    steps:
      - point: "3"
        type: ZOMBIE
        count: 10
        coeff: {{ hp: 1.5, speed: 2.0 }}
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_iron_spear }}
      - point: "4"
        type: ZOMBIE
        count: 1
        coeff: {{ hp: 1.5, speed: 2.0 }}
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_iron_spear }}
      - point: "4"
        type: ZOMBIE
        count: 9
        coeff: {{ hp: 1.5, speed: 2.0 }}
        delay: 5
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_iron_spear }}
"""
    s["w2_fireworks"] = f"""  w2_fireworks:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: CREEPER, count: 5, coeff: {{ speed: 2.0 }}, delay: 0 }}
      - {{ point: "2", type: CREEPER, count: 1, coeff: {{ speed: 2.0 }}, delay: 0 }}
      - {{ point: "2", type: CREEPER, count: 4, coeff: {{ speed: 2.0 }}, delay: 5 }}
      - point: "3"
        type: STRIDER
        count: 3
        delay: 5
        passengers:
          - {{ type: BLAZE, count: 1, coeff: {{ hp: 2.0, scale: 1.5 }} }}
      - point: "4"
        type: STRIDER
        count: 3
        delay: 5
        passengers:
          - {{ type: BLAZE, count: 1, coeff: {{ hp: 2.0, scale: 1.5 }} }}
      - point: "5"
        type: STRIDER
        count: 1
        delay: 0
        passengers:
          - {{ type: GHAST, count: 1, coeff: {{ hp: 10.0 }} }}
"""
    s["w2_lord_legacy"] = f"""  w2_lord_legacy:
    repeat: 1
    {NORMAL}
    steps:
      - point: "2"
        type: ZOMBIE
        count: 30
        coeff: {{ hp: 1.5 }}
        affixes: [infected]
        delay: 1
        on_death:
          - {{ type: ZOMBIE, count: 1, coeff: {{ hp: 3.0 }} }}
      - point: "5"
        type: ZOMBIE
        count: 30
        coeff: {{ hp: 1.5 }}
        affixes: [infected]
        delay: 1
        on_death:
          - {{ type: ZOMBIE, count: 1, coeff: {{ hp: 3.0 }} }}
"""
    s["w2_nest"] = f"""  w2_nest:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: ENDERMITE, count: 10, delay: 0 }}
      - {{ point: "2", type: ENDERMITE, count: 10, delay: 2 }}
      - {{ point: "3", type: ENDERMITE, count: 10, affixes: [blinding], delay: 2 }}
      - {{ point: "4", type: ENDERMITE, count: 10, affixes: [blinding], delay: 2 }}
      - {{ point: "5", type: ENDERMITE, count: 10, delay: 0 }}
      - {{ point: "5", type: ENDERMITE, count: 1, coeff: {{ hp: 10.0, speed: 0.9, dmg: 3.0, scale: 4.0 }}, delay: 1 }}
"""
    s["w2_territory"] = f"""  w2_territory:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: CREEPER, count: 4, coeff: {{ hp: 100.0, speed: 1.5 }}, delay: 0 }}
      - {{ point: "2", type: CREEPER, count: 4, coeff: {{ hp: 100.0, speed: 1.5 }}, delay: 10 }}
      - {{ point: "3", type: CREEPER, count: 4, coeff: {{ hp: 100.0, speed: 1.5 }}, delay: 10 }}
      - {{ point: "4", type: CREEPER, count: 4, coeff: {{ hp: 100.0, speed: 1.5 }}, delay: 10 }}
      - {{ point: "5", type: CREEPER, count: 4, coeff: {{ hp: 100.0, speed: 1.5 }}, delay: 10 }}
      - point: boss
        type: CREEPER
        count: 1
        coeff: {{ hp: 10.0, scale: 2.5 }}
        affixes: [infected]
        delay: 0
        on_death:
          - {{ type: CREEPER, count: 4, coeff: {{ scale: 0.5, speed: 2.0 }} }}
"""
    return s

STRATEGIES.update(act2_strategies())

# Act2 strong + more - add remaining in second batch file extension
# For brevity in script, load rest from embedded dict

def act2_strong_and_rest():
    s = {}
    s["s2_ice_shadows"] = f"""  s2_ice_shadows:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: CAMEL
        count: 10
        coeff: {{ speed: 2.0, hp: 3.0 }}
        delay: 0
        passengers:
          - {{ type: VINDICATOR, count: 1, coeff: {{ hp: 3.0 }} }}
      - point: "5"
        type: HORSE
        count: 5
        coeff: {{ hp: 3.0 }}
        delay: 10
        passengers:
          - type: PILLAGER
            count: 1
            coeff: {{ hp: 3.0 }}
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_crossbow_power2 }}
"""
    s["s2_migration"] = f"""  s2_migration:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: RAVAGER, count: 3, delay: 0 }}
      - {{ point: "4", type: ZOGLIN, count: 10, coeff: {{ speed: 1.5 }}, delay: 3 }}
"""
    s["s2_forbidden"] = f"""  s2_forbidden:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: WARDEN, count: 1, delay: 0 }}
      - {{ point: "2", type: WARDEN, count: 1, delay: 0 }}
      - {{ point: "3", type: VINDICATOR, count: 5, delay: 0 }}
      - {{ point: "4", type: VINDICATOR, count: 5, delay: 0 }}
"""
    s["s2_crimson_tunnel"] = f"""  s2_crimson_tunnel:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: ZOMBIE, count: 100, coeff: {{ hp: 0.2 }}, affixes: [fortified], delay: 1 }}
      - {{ point: "2", type: ZOMBIE, count: 100, coeff: {{ hp: 0.2 }}, affixes: [fortified], delay: 1 }}
"""
    s["s2_controversy"] = f"""  s2_controversy:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: ZOMBIE, count: 2, coeff: {{ hp: 10.0, dmg: 3.0, scale: 2.0 }}, delay: 0 }}
      - {{ point: "2", type: SKELETON, count: 2, coeff: {{ speed: 2.0 }}, affixes: [invisible], delay: 10, equipment: [ {{ slot: HAND, item: maggoteers:mob_iron_axe }} ] }}
      - {{ point: "3", type: SKELETON, count: 2, coeff: {{ speed: 2.0 }}, affixes: [invisible], delay: 20, equipment: [ {{ slot: HAND, item: maggoteers:mob_iron_axe }} ] }}
      - {{ point: "4", type: ZOMBIE, count: 6, coeff: {{ hp: 2.0, dmg: 1.5, scale: 0.4 }}, delay: 5 }}
"""
    s["s2_chessboard"] = f"""  s2_chessboard:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: PILLAGER, count: 5, delay: 0 }}
      - {{ point: "2", type: CREEPER, count: 2, coeff: {{ speed: 2.0 }}, delay: 2 }}
      - point: "3"
        type: CAMEL_HUSK
        count: 2
        delay: 2
        passengers:
          - {{ type: ZOMBIE, count: 1, affixes: [plastic], equipment: [ {{ slot: HAND, item: maggoteers:mob_iron_spear }} ] }}
          - {{ type: SKELETON, count: 1, equipment: [ {{ slot: HAND, item: maggoteers:mob_bow_power1 }} ] }}
      - point: "4"
        type: HORSE
        count: 2
        delay: 2
        passengers:
          - type: VINDICATOR
            count: 1
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_iron_spear }}
      - {{ point: "5", type: RAVAGER, count: 2, delay: 2 }}
      - {{ point: boss, type: VINDICATOR, count: 2, delay: 2, equipment: [ {{ slot: HAND, item: maggoteers:mob_crossbow_power2 }} ] }}
      - point: boss
        type: POLAR_BEAR
        count: 1
        delay: 2
        passengers:
          - {{ type: EVOKER, count: 1 }}
"""
    s["s2_badlands_fake_death"] = f"""  s2_badlands_fake_death:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: HUSK
        count: 5
        affixes: [infected]
        delay: 0
        on_death:
          - {{ type: WITHER_SKELETON, count: 2, coeff: {{ speed: 2.0 }} }}
      - point: "2"
        type: HUSK
        count: 5
        affixes: [infected]
        delay: 10
        on_death:
          - {{ type: WITHER_SKELETON, count: 2, coeff: {{ speed: 2.0 }} }}
"""
    s["s2_badlands_contamination"] = f"""  s2_badlands_contamination:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: WITHER_SKELETON, count: 10, delay: 1 }}
      - {{ point: "2", type: WITHER_SKELETON, count: 10, delay: 1 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 5, delay: 2 }}
      - {{ point: "4", type: WITHER_SKELETON, count: 5, delay: 2 }}
      - point: "5"
        type: ZOGLIN
        count: 5
        coeff: {{ speed: 1.5 }}
        delay: 2
        passengers:
          - {{ type: WITHER_SKELETON, count: 1 }}
"""
    s["s2_badlands_bully"] = f"""  s2_badlands_bully:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 0 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
      - {{ point: "3", type: WITHER_SKELETON, count: 2, coeff: {{ scale: 0.4, speed: 1.5 }}, delay: 5 }}
"""
    s["w2_grave_blind_box"] = f"""  w2_grave_blind_box:
    repeat: 1
    {NORMAL}
    steps:
{blind_box_steps()}
"""
    s["w2_grave_march"] = f"""  w2_grave_march:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: ZOMBIE, count: 8, delay: 0 }}
      - {{ point: "1", type: SKELETON, count: 8, delay: 0 }}
      - {{ point: "2", type: HUSK, count: 8, delay: 8 }}
      - {{ point: "2", type: STRAY, count: 8, delay: 8 }}
      - {{ point: "3", type: DROWNED, count: 8, delay: 8 }}
      - {{ point: "3", type: BOGGED, count: 8, delay: 8 }}
      - {{ point: "4", type: ZOMBIE, count: 10, coeff: {{ speed: 1.2 }}, delay: 6 }}
      - {{ point: "4", type: SKELETON, count: 10, coeff: {{ speed: 1.2 }}, delay: 6 }}
      - {{ point: "5", type: HUSK, count: 12, delay: 6 }}
      - {{ point: "5", type: STRAY, count: 12, delay: 6 }}
"""
    s["s2_grave_phantoms"] = f"""  s2_grave_phantoms:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: PILLAGER, count: 5, affixes: [invisible], delay: 0 }}
      - {{ point: "2", type: VINDICATOR, count: 5, affixes: [invisible], delay: 10 }}
      - {{ point: "3", type: WITCH, count: 5, affixes: [invisible], delay: 10 }}
      - {{ point: "4", type: PILLAGER, count: 5, affixes: [invisible], delay: 10 }}
      - {{ point: "5", type: EVOKER, count: 1, affixes: [invisible], delay: 10 }}
"""
    s["b2_silent_guard"] = f"""  b2_silent_guard:
    repeat: 1
    {BOSS}
    steps:
      - {{ point: "1", type: PIGLIN_BRUTE, count: 5, delay: 0 }}
      - {{ point: "2", type: PIGLIN_BRUTE, count: 1, delay: 0 }}
      - {{ point: "2", type: PIGLIN_BRUTE, count: 4, delay: 10 }}
      - point: "3"
        type: PIGLIN
        count: 10
        coeff: {{ hp: 2.0 }}
        delay: 0
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
      - point: "4"
        type: PIGLIN
        count: 1
        coeff: {{ hp: 2.0 }}
        delay: 0
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
      - point: "4"
        type: PIGLIN
        count: 9
        coeff: {{ hp: 2.0 }}
        delay: 5
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
      - point: "5"
        type: PIGLIN
        count: 2
        coeff: {{ hp: 2.0 }}
        delay: 0
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
          - {{ slot: HAND, item: maggoteers:mob_crossbow }}
      - point: "5"
        type: PIGLIN
        count: 2
        coeff: {{ hp: 2.0 }}
        delay: 20
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
          - {{ slot: HAND, item: maggoteers:mob_crossbow }}
      - point: "5"
        type: PIGLIN
        count: 2
        coeff: {{ hp: 2.0 }}
        delay: 20
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
          - {{ slot: HAND, item: maggoteers:mob_crossbow }}
      - point: "5"
        type: PIGLIN
        count: 2
        coeff: {{ hp: 2.0 }}
        delay: 20
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
          - {{ slot: HAND, item: maggoteers:mob_crossbow }}
      - point: "5"
        type: PIGLIN
        count: 2
        coeff: {{ hp: 2.0 }}
        delay: 20
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_gold_helmet }}
          - {{ slot: CHEST, item: maggoteers:mob_gold_chest }}
          - {{ slot: LEGS, item: maggoteers:mob_gold_legs }}
          - {{ slot: FEET, item: maggoteers:mob_gold_boots }}
          - {{ slot: HAND, item: maggoteers:mob_crossbow }}
      - point: boss
        type: HOGLIN
        count: 1
        coeff: {{ hp: 10.0, speed: 2.5 }}
        
        delay: 0
        passengers:
          - type: PIGLIN
            count: 1
            coeff: {{ hp: 10.0, speed: 0.5 }}
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_crossbow_power3 }}
              - {{ slot: OFF_HAND, item: maggoteers:mob_gold_sword_flame }}
"""
    s["b2_breath"] = f"""  b2_breath:
    repeat: 1
    {BOSS}
    steps:
      - {{ point: "1", type: VINDICATOR, count: 1, affixes: [plastic], delay: 0 }}
      - {{ point: "2", type: VINDICATOR, count: 1, affixes: [plastic], delay: 0 }}
      - {{ point: "3", type: VINDICATOR, count: 1, affixes: [plastic], delay: 0 }}
      - {{ point: "4", type: VINDICATOR, count: 1, affixes: [plastic], delay: 0 }}
      - {{ point: "5", type: VINDICATOR, count: 1, affixes: [plastic], delay: 0 }}
      - {{ point: boss, type: WARDEN, count: 1, coeff: {{ hp: 2.0, speed: 0.4, dmg: 0.5 }}, affixes: [blinding], delay: 30 }}
"""
    s["b2_frost_farewell"] = f"""  b2_frost_farewell:
    repeat: 1
    {BOSS}
    steps:
      - point: "1"
        type: STRAY
        count: 2
        coeff: {{ speed: 2.0 }}
        affixes: [antifreeze]
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_bow_power4 }}
      - point: "1"
        type: STRAY
        count: 2
        coeff: {{ speed: 2.0 }}
        affixes: [antifreeze]
        delay: 10
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_bow_power4 }}
      - point: "2"
        type: STRAY
        count: 2
        coeff: {{ speed: 2.0 }}
        affixes: [antifreeze]
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_bow_power4 }}
      - point: "2"
        type: STRAY
        count: 2
        coeff: {{ speed: 2.0 }}
        affixes: [antifreeze]
        delay: 10
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_bow_power4 }}
      - {{ point: "3", type: CREEPER, count: 4, coeff: {{ speed: 2.0 }}, affixes: [antifreeze], delay: 0 }}
      - {{ point: "4", type: CREEPER, count: 4, coeff: {{ speed: 2.0 }}, affixes: [antifreeze], delay: 10 }}
      - point: "5"
        type: STRAY
        count: 1
        coeff: {{ hp: 10.0, scale: 1.2 }}
        affixes: [antifreeze]
        delay: 0
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_diamond_helmet }}
          - {{ slot: HAND, item: maggoteers:mob_bow_power5 }}
      - point: boss
        type: STRAY
        count: 1
        coeff: {{ hp: 10.0, scale: 1.2 }}
        affixes: [antifreeze]
        delay: 0
        equipment:
          - {{ slot: HEAD, item: maggoteers:mob_diamond_helmet }}
          - {{ slot: HAND, item: maggoteers:mob_diamond_axe_sharp5 }}
"""
    return s

STRATEGIES.update(act2_strong_and_rest())

def act3():
    s = {}
    s["w3_duck_ops"] = f"""  w3_duck_ops:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: CHICKEN, count: 1, coeff: {{ hp: 10.0, speed: 5.0 }}, delay: 0 }}
      - {{ point: "2", type: POLAR_BEAR, count: 1, coeff: {{ hp: 10.0 }}, delay: 0 }}
      - {{ point: "3", type: WOLF, count: 1, coeff: {{ hp: 5.0, dmg: 4.0 }}, affixes: [invisible], delay: 0 }}
      - {{ point: "4", type: ARMADILLO, count: 1, affixes: [fortified], delay: 0 }}
"""
    s["w3_rabbit_holes"] = f"""  w3_rabbit_holes:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "1", type: RABBIT, count: 1, coeff: {{ hp: 10.0, speed: 1.2 }}, delay: 0 }}
      - {{ point: "2", type: RABBIT, count: 1, coeff: {{ hp: 10.0, speed: 1.2 }}, delay: 0 }}
      - {{ point: "3", type: RABBIT, count: 1, coeff: {{ hp: 10.0, speed: 1.2 }}, delay: 0 }}
      - {{ point: "4", type: RABBIT, count: 1, coeff: {{ hp: 10.0, speed: 1.2 }}, delay: 0 }}
      - {{ point: "5", type: RABBIT, count: 1, coeff: {{ hp: 10.0, speed: 1.2 }}, delay: 0 }}
"""
    s["w3_lights_out"] = f"""  w3_lights_out:
    repeat: 1
    {NORMAL}
    steps:
      - {{ point: "3", type: CREAKING, count: 1, coeff: {{ dmg: 20.0 }}, affixes: [plastic], delay: 0 }}
      - {{ point: "4", type: CREAKING, count: 1, coeff: {{ dmg: 20.0 }}, affixes: [plastic], delay: 0 }}
      - {{ point: "5", type: CREAKING, count: 1, coeff: {{ dmg: 20.0 }}, affixes: [plastic], delay: 0 }}
"""
    s["s3_surprise_factory"] = f"""  s3_surprise_factory:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: WITHER_SKELETON
        count: 6
        coeff: {{ hp: 2.0 }}
        affixes: [infected]
        delay: 0
        on_death:
          - {{ type: CREEPER, count: 1, coeff: {{ speed: 1.5 }}, affixes: [plastic] }}
      - point: "2"
        type: WITHER_SKELETON
        count: 6
        coeff: {{ hp: 2.0 }}
        affixes: [infected]
        delay: 5
        on_death:
          - {{ type: CREEPER, count: 1, coeff: {{ speed: 1.5 }}, affixes: [plastic] }}
      - point: "3"
        type: WITHER_SKELETON
        count: 8
        coeff: {{ hp: 3.0 }}
        affixes: [infected]
        delay: 0
        on_death:
          - {{ type: CREEPER, count: 1, coeff: {{ speed: 2.0 }}, affixes: [plastic] }}
      - point: "4"
        type: WITHER_SKELETON
        count: 8
        coeff: {{ hp: 3.0 }}
        affixes: [infected]
        delay: 3
        on_death:
          - {{ type: CREEPER, count: 2, coeff: {{ speed: 2.0 }}, affixes: [plastic] }}
      - point: "5"
        type: WITHER_SKELETON
        count: 10
        coeff: {{ hp: 4.0 }}
        affixes: [infected]
        delay: 0
        on_death:
          - {{ type: CREEPER, count: 2, coeff: {{ speed: 2.0, scale: 0.7 }}, affixes: [plastic] }}
"""
    s["s3_across_fire"] = f"""  s3_across_fire:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: PILLAGER
        count: 5
        coeff: {{ hp: 2.0 }}
        affixes: [fireresistance]
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_crossbow_flame }}
      - point: "2"
        type: PILLAGER
        count: 5
        coeff: {{ hp: 2.0 }}
        affixes: [fireresistance]
        delay: 8
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_crossbow_flame }}
      - point: "3"
        type: PILLAGER
        count: 8
        coeff: {{ hp: 3.0 }}
        affixes: [fireresistance]
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_crossbow_flame_quick }}
      - point: "4"
        type: PILLAGER
        count: 8
        coeff: {{ hp: 3.0 }}
        affixes: [fireresistance]
        delay: 5
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_crossbow_flame_quick }}
      - point: "5"
        type: PILLAGER
        count: 2
        coeff: {{ hp: 4.0, speed: 1.2 }}
        affixes: [fireresistance]
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_crossbow_flame }}
      - point: "5"
        type: PILLAGER
        count: 2
        coeff: {{ hp: 4.0, speed: 1.2 }}
        affixes: [fireresistance]
        delay: 6
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_crossbow_flame }}
"""
    s["s3_out_of_control"] = f"""  s3_out_of_control:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: VINDICATOR, count: 4, coeff: {{ hp: 3.0, dmg: 2.0, speed: 2.0 }}, affixes: [withering], delay: 0 }}
      - {{ point: "2", type: PILLAGER, count: 4, coeff: {{ hp: 3.0, dmg: 2.0, speed: 2.0 }}, affixes: [withering], delay: 10 }}
      - {{ point: "3", type: VINDICATOR, count: 4, coeff: {{ hp: 4.0, dmg: 3.0, speed: 2.5 }}, affixes: [withering], delay: 20 }}
      - {{ point: "4", type: PILLAGER, count: 4, coeff: {{ hp: 4.0, dmg: 3.0, speed: 2.5 }}, affixes: [withering], delay: 30 }}
"""
    chaos1 = ["ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "ZOMBIFIED_PIGLIN", "SKELETON", "STRAY", "BOGGED", "WITHER_SKELETON", "CREEPER"]
    chaos2 = ["SPIDER", "CAVE_SPIDER", "ENDERMITE", "SLIME", "MAGMA_CUBE", "WITCH", "VINDICATOR", "EVOKER", "PILLAGER"]
    chaos3 = ["RAVAGER", "GUARDIAN", "PIGLIN", "PIGLIN_BRUTE", "ZOGLIN", "HOGLIN", "WARDEN", "BREEZE", "CREAKING"]
    def chaos_step(point, types, hp=1.0, dmg=1.0, delay=0):
        lines = [f'      - point: "{point}"']
        for t in types:
            if hp == 1.0 and dmg == 1.0:
                lines.append(f'      - {{ point: "{point}", type: {t}, count: 1, delay: {delay} }}')
            else:
                lines.append(f'      - {{ point: "{point}", type: {t}, count: 1, coeff: {{ hp: {hp}, dmg: {dmg} }}, delay: {delay} }}')
        return "\n".join(lines)
    csteps = []
    for t in chaos1:
        csteps.append(f'      - {{ point: "1", type: {t}, count: 1, delay: 0 }}')
    for t in chaos2:
        csteps.append(f'      - {{ point: "2", type: {t}, count: 1, delay: 0 }}')
    for t in chaos3:
        csteps.append(f'      - {{ point: "3", type: {t}, count: 1, delay: 0 }}')
    all_types = chaos1 + chaos2 + chaos3
    for t in all_types:
        csteps.append(f'      - {{ point: "4", type: {t}, count: 1, coeff: {{ hp: 2.0 }}, delay: 0 }}')
    for t in all_types:
        csteps.append(f'      - {{ point: "5", type: {t}, count: 1, coeff: {{ hp: 3.0, dmg: 2.0 }}, delay: 0 }}')
    s["s3_chaos_facade"] = f"""  s3_chaos_facade:
    repeat: 1
    {STRONG}
    steps:
{chr(10).join(csteps)}
"""
    s["s3_keep_out"] = f"""  s3_keep_out:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: SPIDER
        count: 2
        affixes: [exoskeleton, infected]
        delay: 0
        passengers:
          - type: SKELETON
            count: 1
            equipment:
              - {{ slot: HEAD, item: maggoteers:mob_iron_helmet_ub10 }}
              - {{ slot: HAND, item: maggoteers:mob_iron_sword_ub10 }}
        on_death:
          - {{ type: CAVE_SPIDER, count: 2, affixes: [invisible] }}
      - point: "2"
        type: SPIDER
        count: 2
        affixes: [exoskeleton, infected]
        delay: 15
        passengers:
          - type: SKELETON
            count: 1
            equipment:
              - {{ slot: HEAD, item: maggoteers:mob_iron_helmet_ub10 }}
              - {{ slot: HAND, item: maggoteers:mob_iron_sword_ub10 }}
        on_death:
          - {{ type: CAVE_SPIDER, count: 2, affixes: [invisible] }}
      - point: "3"
        type: SPIDER
        count: 3
        coeff: {{ hp: 2.0, scale: 1.5 }}
        affixes: [exoskeleton, infected]
        delay: 0
        passengers:
          - type: SKELETON
            count: 1
            equipment:
              - {{ slot: HEAD, item: maggoteers:mob_diamond_helmet }}
              - {{ slot: HAND, item: maggoteers:mob_iron_axe }}
        on_death:
          - {{ type: CAVE_SPIDER, count: 3, affixes: [invisible] }}
      - point: "4"
        type: SPIDER
        count: 3
        coeff: {{ hp: 2.0, scale: 1.5 }}
        affixes: [exoskeleton, infected]
        delay: 10
        passengers:
          - type: SKELETON
            count: 1
            equipment:
              - {{ slot: HEAD, item: maggoteers:mob_diamond_helmet }}
              - {{ slot: HAND, item: maggoteers:mob_iron_axe }}
        on_death:
          - {{ type: CAVE_SPIDER, count: 3, affixes: [invisible] }}
      - point: "5"
        type: SPIDER
        count: 4
        coeff: {{ hp: 3.0, scale: 2.0 }}
        affixes: [exoskeleton, infected]
        delay: 0
        passengers:
          - type: SKELETON
            count: 1
            coeff: {{ hp: 2.0 }}
            equipment:
              - {{ slot: HEAD, item: maggoteers:mob_diamond_helmet }}
              - {{ slot: CHEST, item: maggoteers:mob_diamond_chest }}
              - {{ slot: LEGS, item: maggoteers:mob_diamond_legs }}
              - {{ slot: FEET, item: maggoteers:mob_diamond_boots }}
              - {{ slot: HAND, item: maggoteers:mob_iron_axe }}
        on_death:
          - {{ type: CAVE_SPIDER, count: 4, affixes: [invisible] }}
"""
    s["s3_fire_water"] = f"""  s3_fire_water:
    repeat: 1
    {STRONG}
    steps:
      - point: "1"
        type: CAMEL
        count: 1
        delay: 0
        passengers:
          - {{ type: BLAZE, count: 1 }}
          - type: DROWNED
            count: 1
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "2"
        type: CAMEL
        count: 1
        delay: 0
        passengers:
          - {{ type: BLAZE, count: 1 }}
          - type: DROWNED
            count: 1
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "3"
        type: CAMEL
        count: 2
        coeff: {{ hp: 2.0 }}
        delay: 10
        passengers:
          - {{ type: BLAZE, count: 1, affixes: [fireresistance] }}
          - type: DROWNED
            count: 1
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "4"
        type: CAMEL
        count: 2
        coeff: {{ hp: 3.0 }}
        delay: 10
        passengers:
          - {{ type: BLAZE, count: 1, coeff: {{ hp: 2.0 }}, affixes: [fireresistance] }}
          - type: DROWNED
            count: 1
            coeff: {{ hp: 2.0 }}
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "5"
        type: CAMEL
        count: 3
        coeff: {{ hp: 4.0 }}
        delay: 8
        passengers:
          - {{ type: BLAZE, count: 1, coeff: {{ hp: 3.0 }}, affixes: [fireresistance] }}
          - type: DROWNED
            count: 1
            coeff: {{ hp: 3.0 }}
            equipment:
              - {{ slot: HAND, item: maggoteers:mob_trident }}
"""
    s["s3_institution"] = f"""  s3_institution:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: VINDICATOR, count: 4, delay: 0 }}
      - {{ point: "2", type: VINDICATOR, count: 4, affixes: [invisible], delay: 10 }}
      - {{ point: "3", type: WITCH, count: 4, delay: 10 }}
      - {{ point: "4", type: VINDICATOR, count: 2, coeff: {{ hp: 2.0 }}, delay: 0 }}
      - {{ point: "4", type: VINDICATOR, count: 2, coeff: {{ hp: 2.0 }}, affixes: [invisible], delay: 0 }}
      - {{ point: "4", type: WITCH, count: 2, coeff: {{ hp: 2.0 }}, delay: 8 }}
      - {{ point: "5", type: VINDICATOR, count: 3, coeff: {{ hp: 3.0, dmg: 2.0 }}, delay: 0 }}
      - {{ point: "5", type: VINDICATOR, count: 3, coeff: {{ hp: 3.0, dmg: 2.0 }}, affixes: [invisible], delay: 0 }}
      - {{ point: "5", type: WITCH, count: 4, coeff: {{ hp: 3.0, dmg: 2.0 }}, delay: 5 }}
"""
    s["s3_witch_pact"] = f"""  s3_witch_pact:
    repeat: 1
    {STRONG}
    steps:
      - {{ point: "1", type: WITCH, count: 4, delay: 0 }}
      - {{ point: "2", type: EVOKER, count: 4, delay: 10 }}
      - point: "3"
        type: STRIDER
        count: 4
        delay: 8
        passengers:
          - {{ type: BLAZE, count: 1, coeff: {{ hp: 2.0 }} }}
      - {{ point: "4", type: BREEZE, count: 5, delay: 5 }}
      - {{ point: "5", type: WITCH, count: 3, coeff: {{ hp: 2.0 }}, delay: 0 }}
      - {{ point: "5", type: EVOKER, count: 3, coeff: {{ hp: 2.0 }}, delay: 0 }}
      - point: "5"
        type: STRIDER
        count: 3
        delay: 0
        passengers:
          - {{ type: BLAZE, count: 1, coeff: {{ hp: 2.0 }} }}
      - {{ point: "5", type: BREEZE, count: 4, coeff: {{ hp: 2.0 }}, delay: 5 }}
"""
    def rhine_slime(delay, hp, camel_hp):
        return f"""      - point: "1"
        type: SLIME
        count: 1
        coeff: {{ hp: {hp} }}
        affixes: [infected, mechanical]
        delay: {delay}
        on_death:
          - type: CAMEL
            count: 1
            coeff: {{ hp: {camel_hp} }}
            affixes: [infected, mechanical]
            passengers:
              - {{ type: VINDICATOR, count: 1, coeff: {{ hp: {camel_hp} }}, affixes: [mechanical] }}
              - {{ type: PILLAGER, count: 1, coeff: {{ hp: {camel_hp} }}, affixes: [mechanical] }}
            on_death:
              - {{ type: ENDERMITE, count: 4 }}"""
    s["s3_rhine_guard"] = f"""  s3_rhine_guard:
    repeat: 1
    {STRONG}
    steps:
{rhine_slime(20, 3.0, 1.0)}
{rhine_slime(40, 4.0, 1.0)}
{rhine_slime(60, 5.0, 2.0)}
{rhine_slime(80, 6.0, 2.0)}
      - {{ point: "5", type: VINDICATOR, count: 8, affixes: [mechanical], delay: 10 }}
"""
    s["b3_into_eternal"] = f"""  b3_into_eternal:
    repeat: 1
    {BOSS}
    steps:
      - {{ point: "1", type: HOGLIN, count: 10, coeff: {{ hp: 3.0 }}, delay: 0 }}
      - point: "2"
        type: WITHER_SKELETON
        count: 2
        coeff: {{ hp: 4.0 }}
        
        delay: 0
      - point: "2"
        type: WITHER_SKELETON
        count: 2
        coeff: {{ hp: 4.0 }}
        
        delay: 20
      - point: "3"
        type: PIGLIN
        count: 5
        delay: 0
      - point: "3"
        type: PIGLIN
        count: 5
        delay: 40
      - point: "4"
        type: DROWNED
        count: 3
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "4"
        type: DROWNED
        count: 3
        delay: 40
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_trident }}
      - {{ point: "5", type: IRON_GOLEM, count: 1, coeff: {{ hp: 20.0 }}, delay: 30 }}
      - {{ point: boss, type: WARDEN, count: 1, coeff: {{ hp: 4.0 }}, delay: 30 }}
"""
    s["b3_sentinel"] = f"""  b3_sentinel:
    repeat: 1
    {BOSS}
    steps:
      - point: "1"
        type: DROWNED
        count: 2
        coeff: {{ hp: 6.0 }}
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "1"
        type: DROWNED
        count: 2
        coeff: {{ hp: 6.0 }}
        delay: 30
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "2"
        type: DROWNED
        count: 2
        coeff: {{ hp: 2.0 }}
        
        delay: 0
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_trident }}
      - point: "2"
        type: DROWNED
        count: 2
        coeff: {{ hp: 2.0 }}
        
        delay: 30
        equipment:
          - {{ slot: HAND, item: maggoteers:mob_trident }}
{camel_squad("3", 0)}
{camel_squad("3", 25)}
{camel_squad("3", 25)}
{camel_squad("3", 25)}
      - {{ point: "4", type: GUARDIAN, count: 4, delay: 0 }}
      - {{ point: boss, type: ELDER_GUARDIAN, count: 1, coeff: {{ dmg: 2.0 }}, affixes: [blinding], delay: 10 }}
"""
    rad_steps = []
    for pt in "1234":
        rad_steps.append(radiance_point_steps(pt))
    s["b3_human_radiance"] = f"""  b3_human_radiance:
    repeat: 1
    {BOSS}
    steps:
      - {{ point: boss, type: SLIME, count: 1, coeff: {{ hp: 20.0, scale: 3.0 }}, delay: 0 }}
{chr(10).join(rad_steps)}
"""
    return s

STRATEGIES.update(act3())

STRATEGY_NAMES = {
    "w1_bug_buddies": "与虫为伴",
    "w1_patrol_riders": "巡逻队",
    "w1_accident": "意外",
    "w1_symbiosis": "共生",
    "w1_symptom": "病症",
    "w1_herd": "从众效应",
    "s1_resort_ghosts": "度假村冤魂",
    "s1_low_altitude": "低空机动",
    "s1_vent_breeze": "排风口",
    "w1_desert_four_camels": "事不过四",
    "w1_desert_prisoners": "死囚之夜",
    "w1_desert_plague": "公害",
    "w1_jungle_shadows": "幽影与鬼魅",
    "w1_jungle_spider_swap": "拆东补西",
    "w1_jungle_spider_falls": "卡兹瀑布",
    "b1_colossus": "无主巨像",
    "b1_eternal_rest": "永恒安息",
    "b1_bandit_chief": "大盗当头",
    "w2_merchant_convoy": "酒商运输队",
    "w2_endless_spear": "永无尽头",
    "w2_fireworks": "烟花秀",
    "w2_lord_legacy": "大君遗脉",
    "w2_nest": "巢穴",
    "w2_territory": "领地意识",
    "s2_ice_shadows": "冰海疑影",
    "s2_migration": "大迁徙",
    "s2_forbidden": "禁区",
    "s2_chessboard": "大棋一盘",
    "s2_crimson_tunnel": "猩红甬道",
    "s2_controversy": "争议频发",
    "s2_badlands_fake_death": "弄假成真",
    "s2_badlands_contamination": "本能污染",
    "s2_badlands_bully": "恃强凌弱",
    "w2_grave_blind_box": "盲盒市场",
    "w2_grave_march": "亡者行军",
    "s2_grave_phantoms": "神出鬼没",
    "b2_silent_guard": "卫士不语功",
    "b2_breath": "呼吸",
    "b2_frost_farewell": "寒渊惜别",
    "w3_duck_ops": "鸭本运作",
    "w3_rabbit_holes": "狡兔九窟",
    "w3_lights_out": "天黑请闭眼",
    "s3_surprise_factory": "惊喜工厂",
    "s3_across_fire": "隔岸观火",
    "s3_out_of_control": "失控",
    "s3_chaos_facade": "混乱的表象",
    "s3_keep_out": "生人勿近",
    "s3_fire_water": "水火相容",
    "s3_institution": "建制",
    "s3_witch_pact": "巫咒同盟",
    "s3_rhine_guard": "莱茵卫士",
    "b3_into_eternal": "迈入永恒",
    "b3_sentinel": "哨兵",
    "b3_human_radiance": "人之光辉" }

def main():
    order = [
        # act1
        "w1_bug_buddies", "w1_patrol_riders", "w1_accident", "w1_symbiosis", "w1_symptom", "w1_herd",
        "s1_resort_ghosts", "s1_low_altitude", "s1_vent_breeze",
        "w1_desert_four_camels", "w1_desert_prisoners", "w1_desert_plague",
        "w1_jungle_shadows", "w1_jungle_spider_swap", "w1_jungle_spider_falls",
        "b1_colossus", "b1_eternal_rest", "b1_bandit_chief",
        # act2
        "w2_merchant_convoy", "w2_endless_spear", "w2_fireworks", "w2_lord_legacy", "w2_nest", "w2_territory",
        "s2_ice_shadows", "s2_migration", "s2_forbidden", "s2_chessboard", "s2_crimson_tunnel", "s2_controversy",
        "s2_badlands_fake_death", "s2_badlands_contamination", "s2_badlands_bully",
        "w2_grave_blind_box", "w2_grave_march", "s2_grave_phantoms",
        "b2_silent_guard", "b2_breath", "b2_frost_farewell",
        # act3 weak (defined not pooled)
        "w3_duck_ops", "w3_rabbit_holes", "w3_lights_out",
        "s3_surprise_factory", "s3_across_fire", "s3_out_of_control", "s3_chaos_facade",
        "s3_keep_out", "s3_fire_water", "s3_institution", "s3_witch_pact", "s3_rhine_guard",
        "b3_into_eternal", "b3_sentinel", "b3_human_radiance",
    ]
    missing = [k for k in order if k not in STRATEGIES]
    if missing:
        raise SystemExit(f"Missing strategies: {missing}")
    body = ""
    for k in order:
        cn = STRATEGY_NAMES.get(k, k)
        body += f"  # {cn}（{k}）\n" + STRATEGIES[k] + "\n"
    OUT.write_text(HEADER + body, encoding="utf-8")
    spec = importlib.util.spec_from_file_location(
        "patch_infernal_defaults", Path(__file__).parent / "patch_infernal_defaults.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.patch_file(OUT)
    print(f"Wrote {OUT} ({len(order)} strategies)")

if __name__ == "__main__":
    main()
