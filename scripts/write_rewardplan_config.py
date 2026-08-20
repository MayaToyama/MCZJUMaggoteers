#!/usr/bin/env python3
"""Generate rewards.yml, items/maggoteers.yml (player weapons), collectibles from rewardplan."""
from __future__ import annotations

from pathlib import Path
import textwrap

ROOT = Path(__file__).resolve().parents[1]
OUT_REWARDS = ROOT / "src/main/resources/rewards.yml"
OUT_ITEMS = ROOT / "src/main/resources/items/maggoteers.yml"
OUT_COLL_MAP = ROOT / "src/main/resources/collectibles.yml"
OUT_COLL_ITEMS = ROOT / "src/main/resources/items/collectibles.yml"
MOB_ITEMS_MARKER = "# ── 怪物专用装备"


def pct(n: int | float) -> float:
    return round(float(n) / 100.0, 4)


def hp(n_hearts: int | float) -> float:
    return float(n_hearts) * 2.0


def purify_potions():
    """rewardplan 宏：净化 — 0s 255 级负面药水各一条。"""
    names = ("WITHER", "SLOWNESS", "WEAKNESS", "BLINDNESS", "POISON", "DARKNESS")
    return [{"potion": n, "amp": 255, "duration_ticks": 1} for n in names]


def silence_potions():
    """rewardplan 宏：沉默 — 0s 255 级增益药水各一条。"""
    names = (
        "STRENGTH", "SPEED", "INVISIBILITY", "FIRE_RESISTANCE", "RESISTANCE", "REGENERATION",
    )
    return [{"potion": n, "amp": 255, "duration_ticks": 1} for n in names]


def self_harm_instant():
    return [{"potion": "INSTANT_DAMAGE", "amp": 0, "duration_ticks": 1}]


def self_harm_poison():
    return [{"potion": "POISON", "amp": 0, "duration_ticks": 1}]


def ydump(obj, indent=0) -> str:
    sp = " " * indent
    if isinstance(obj, dict):
        lines = []
        for k, v in obj.items():
            if isinstance(v, (dict, list)):
                sub = ydump(v, indent + 2)
                if "\n" in sub:
                    lines.append(f"{sp}{k}:")
                    lines.append(sub)
                else:
                    lines.append(f"{sp}{k}: {sub.strip()}")
            elif isinstance(v, bool):
                lines.append(f"{sp}{k}: {'true' if v else 'false'}")
            elif v is None:
                lines.append(f"{sp}{k}: null")
            elif isinstance(v, str):
                if any(c in v for c in ":{}[]&*#?|-<>=!%@`"):
                    lines.append(f'{sp}{k}: "{v}"')
                else:
                    lines.append(f"{sp}{k}: {v}")
            else:
                lines.append(f"{sp}{k}: {v}")
        return "\n".join(lines)
    if isinstance(obj, list):
        if not obj:
            return "[]"
        lines = []
        for item in obj:
            if isinstance(item, dict):
                lines.append(f"{sp}-")
                lines.append(ydump(item, indent + 2))
            else:
                lines.append(f"{sp}- {item}")
        return "\n".join(lines)
    return str(obj)


def stat_attr(rid, display, attr, value, op="PERCENT", desc=None, **extra):
    o = {
        "id": rid,
        "display": display,
        "category": "STAT",
        "effect": "ADD_ATTRIBUTE",
        "params": {"attr": attr, "op": op, "value": value},
        "unique": True,
        "stack": "ADD",
    }
    if desc:
        o["description"] = desc
    o.update(extra)
    return o


def stat_potion(rid, display, potion, amp=0, stack="IGNORE", upgrade_max=0, desc=None):
    o = {
        "id": rid,
        "display": display,
        "category": "STAT",
        "effect": "ADD_POTION",
        "params": {"potion": potion, "amp": amp, "duration_ticks": 0},
        "unique": True,
        "stack": stack,
    }
    if upgrade_max:
        o["upgrade_max"] = upgrade_max
    if desc:
        o["description"] = desc
    return o


def weapon(rid, display, item, desc=None):
    o = {"id": rid, "display": display, "category": "WEAPON", "item": item}
    if desc:
        o["description"] = desc
    return o


def bundle(rid, display, grants, desc=None):
    o = {"id": rid, "display": display, "grants": grants}
    if desc:
        o["description"] = desc
    return o


def coll(rid, mat, name, lore=None):
    iid = f"maggoteers:charm_{rid}"
    entry = {
        "material": mat,
        "customName": name,
        "glint": True,
    }
    if lore:
        entry["lore"] = lore if isinstance(lore, list) else [lore]
    return iid, entry


COLLECTIBLE_MAP: dict[str, str] = {}
COLLECTIBLE_ITEMS: dict[str, dict] = {}


def map_stat(opt: dict, mat: str, charm_name: str | None = None):
    if opt.get("grants"):
        rid = opt["id"]
        name = charm_name or opt.get("display", rid)
        iid, item = coll(rid, mat, name, opt.get("description"))
        COLLECTIBLE_MAP[rid] = iid
        COLLECTIBLE_ITEMS[iid] = item
        return
    if opt.get("category") != "STAT":
        return
    rid = opt["id"]
    name = charm_name or opt.get("display", rid)
    iid, item = coll(rid, mat, name, opt.get("description"))
    COLLECTIBLE_MAP[rid] = iid
    COLLECTIBLE_ITEMS[iid] = item


def map_leveled(rid, levels: dict[int, tuple[str, str]]):
    by = {}
    for lv, (mat, name) in levels.items():
        iid, item = coll(f"{rid}_{lv}", mat, name)
        by[lv] = iid
        COLLECTIBLE_ITEMS[iid] = item
    COLLECTIBLE_MAP[rid] = {"items_by_level": by}


# ── reward options ──────────────────────────────────────────────

def class_pools():
    return [
        bundle("class_vanguard", "<gold>先锋", [
            weapon("class_vanguard_axe", "<gold>初心者战斧", "maggoteers:class_vanguard_axe"),
            stat_attr("class_vanguard_token", "<red>先锋信物", "ATTACK_DAMAGE", pct(30), desc="+30攻击 +4生命"),
            stat_attr("class_vanguard_token_hp", "<red>先锋信物", "MAX_HEALTH", hp(4), op="FLAT"),
        ], "战斧 + 信物"),
        bundle("class_mage", "<green>术师", [
            weapon("class_mage_sword", "<green>初心者魔剑", "maggoteers:class_mage_sword"),
            stat_attr("class_mage_token_atk", "<green>术师信物", "ATTACK_DAMAGE", pct(20)),
            stat_attr("class_mage_token_mag", "<green>术师信物", "MAGIC_DAMAGE", pct(20)),
            stat_attr("class_mage_token_hp", "<green>术师信物", "MAX_HEALTH", -hp(4), op="FLAT"),
        ], "魔剑 + 信物"),
        bundle("class_heavy", "<blue>重装", [
            weapon("class_heavy_sword", "<blue>初心者圣剑", "maggoteers:class_heavy_sword"),
            stat_attr("class_heavy_token_atk", "<gray>重装信物", "ATTACK_DAMAGE", pct(20)),
            stat_attr("class_heavy_token_hp", "<gray>重装信物", "MAX_HEALTH", hp(4), op="FLAT"),
        ], "圣剑 + 信物"),
        bundle("class_sniper", "<aqua>狙击", [
            weapon("class_sniper_bow", "<aqua>初心者之弓", "maggoteers:class_sniper_bow"),
            stat_attr("class_sniper_token_mag", "<aqua>狙击信物", "MAGIC_DAMAGE", pct(40)),
            stat_attr("class_sniper_token_spd", "<aqua>狙击信物", "MOVEMENT_SPEED", pct(8)),
            stat_attr("class_sniper_token_hp", "<aqua>狙击信物", "MAX_HEALTH", -hp(6), op="FLAT"),
        ], "弩 + 信物"),
        bundle("class_guard", "<yellow>近卫", [
            weapon("class_guard_spear", "<yellow>初心者长矛", "maggoteers:class_guard_spear"),
            stat_attr("class_guard_token_reach", "<yellow>近卫信物", "ENTITY_INTERACTION_RANGE", 0.5, op="FLAT"),
            stat_attr("class_guard_token_atk", "<yellow>近卫信物", "ATTACK_DAMAGE", pct(30)),
        ], "长矛 + 信物"),
    ]


def shared_weapons_a1():
    return [
        weapon("w_past_fine", "<gold>昔日精品", "maggoteers:past_fine"),
        weapon("w_heat_cutter", "<red>热熔切割器", "maggoteers:heat_cutter_a1"),
        weapon("w_blast_unit", "<green>爆破单元", "maggoteers:blast_unit"),
        weapon("w_jet", "<aqua>J.E.T", "maggoteers:jet_a1"),
        weapon("w_wedge", "<blue>楔子", "maggoteers:wedge"),
        weapon("w_silver_sword", "<white>猎魔人的银剑", "maggoteers:silver_sword"),
        weapon("w_burn_blade", "<red>一刀一刀燃烧刀", "maggoteers:burn_blade"),
        weapon("w_portable_fort", "<gray>便携要塞", "maggoteers:portable_fort"),
        weapon("w_cat_hammer", "<light_purple>充气猫猫锤", "maggoteers:cat_hammer"),
    ]


def act1_weak():
    opts = [
        stat_attr("a1w_atk15", "<red>攻击+15", "ATTACK_DAMAGE", pct(15)),
        stat_attr("a1w_atk20", "<red>攻击+20", "ATTACK_DAMAGE", pct(20)),
        stat_attr("a1w_atk25", "<red>攻击+25", "ATTACK_DAMAGE", pct(25)),
        stat_attr("a1w_mag15", "<light_purple>魔攻+15", "MAGIC_DAMAGE", pct(15)),
        stat_attr("a1w_mag20", "<light_purple>魔攻+20", "MAGIC_DAMAGE", pct(20)),
        stat_attr("a1w_mag25", "<light_purple>魔攻+25", "MAGIC_DAMAGE", pct(25)),
        stat_attr("a1w_hp4a", "<green>生命+4", "MAX_HEALTH", hp(4), op="FLAT"),
        stat_attr("a1w_hp4b", "<green>生命+4", "MAX_HEALTH", hp(4), op="FLAT"),
        stat_attr("a1w_hp4c", "<green>生命+4", "MAX_HEALTH", hp(4), op="FLAT"),
        stat_attr("a1w_spd4a", "<blue>速度+4", "MOVEMENT_SPEED", pct(4)),
        stat_attr("a1w_spd4b", "<blue>速度+4", "MOVEMENT_SPEED", pct(4)),
        stat_potion("a1w_fire_res", "<gold>抗火", "FIRE_RESISTANCE", stack="IGNORE"),
        stat_potion("a1w_str1", "<red>力量 I", "STRENGTH", stack="UPGRADE_LEVEL", upgrade_max=1),
        {
            "id": "a1w_fangs",
            "display": "<dark_purple>黑暗宝珠",
            "category": "STAT",
            "trigger": "ON_DAMAGE_DEALT",
            "effect": "SUMMON",
            "params": {"entity": "EVOKER_FANGS", "anchor": "hit_target", "cleanup": "wave_clear", "friendly_fire": False},
            "unique": True,
            "stack": "IGNORE",
        },
        {
            "id": "a1w_kill_invis",
            "display": "<gray>微型怨灵",
            "category": "STAT",
            "trigger": "ON_KILL",
            "effect": "BUFF_AREA",
            "params": {
                "targets": "self",
                "potions": [{"potion": "INVISIBILITY", "amp": 0, "duration_ticks": 60}],
                "fx": {"preset": "DOT_ABOVE", "particle": "SMOKE"},
            },
            "unique": True,
            "stack": "IGNORE",
        },
        {
            "id": "a1w_thorn_arrow",
            "display": "<gold>青铜羽饰",
            "category": "STAT",
            "trigger": "ON_DAMAGE_TAKEN",
            "effect": "SUMMON",
            "params": {
                "entity": "ARROW",
                "anchor": "attacker",
                "cleanup": "duration",
                "duration_sec": 5,
                "friendly_fire": False,
                "projectile": {"speed": 5.0, "toward": "look"},
            },
            "unique": True,
            "stack": "IGNORE",
        },
        {
            "id": "a1w_mark_glow",
            "display": "<yellow>照明弹",
            "category": "STAT",
            "trigger": "ON_DAMAGE_DEALT",
            "effect": "BUFF_AREA",
            "params": {
                "targets": "hit_target",
                "potions": [{"potion": "GLOWING", "amp": 0, "duration_ticks": 1200}],
                "mark_on": "hit_target",
                "mark_fx": {"preset": "DOT_ABOVE", "particle": "END_ROD"},
            },
            "unique": True,
            "stack": "IGNORE",
        },
        {
            "id": "a1w_hit_splash",
            "display": "<red>狂战斧",
            "category": "STAT",
            "trigger": "ON_DAMAGE_DEALT",
            "effect": "DAMAGE_AREA",
            "params": {"damage": 6.0, "radius": 3.0, "targets": "enemies", "enemy_scope": "tracked"},
            "unique": True,
            "stack": "IGNORE",
        },
        {
            "id": "a1w_wave_bone",
            "display": "<gray>《德鲁伊入门》",
            "category": "STAT",
            "trigger": "ON_WAVE_CLEAR",
            "effect": "GRANT_ITEM",
            "params": {"item": "maggoteers:wolf_whistle", "count": 1},
            "unique": True,
            "stack": "IGNORE",
        },
        bundle("a1w_parting_gift", "<dark_red>临别赠礼", [
            {
                "id": "a1w_parting_tnt",
                "category": "STAT",
                "trigger": "ON_DEATH",
                "effect": "SUMMON",
                "params": {"entity": "TNT", "anchor": "death_site", "count": 5, "cleanup": "duration", "duration_sec": 10, "friendly_fire": False},
            },
            {
                "id": "a1w_parting_slow",
                "category": "STAT",
                "trigger": "ON_DEATH",
                "effect": "BUFF_AREA",
                "params": {
                    "radius": 6.0,
                    "targets": "enemies",
                    "enemy_scope": "tracked",
                    "potions": [{"potion": "SLOWNESS", "amp": 9, "duration_ticks": 100}],
                },
            },
        ]),
        {
            "id": "a1w_act_revive",
            "display": "<green>增生藤甲",
            "category": "STAT",
            "trigger": "ON_ACT_ENTER",
            "effect": "GRANT_REVIVE",
            "params": {"count": 1},
            "unique": True,
            "stack": "IGNORE",
        },
    ]
    opts.extend(shared_weapons_a1())
    mats = ["CHIPPED_ANVIL", "COPPER_INGOT", "MAGMA_CREAM", "LAPIS_LAZULI", "DEAD_BUSH", "SPLASH_POTION",
            "POTION", "BREAD", "LEATHER", "COOKED_CHICKEN", "BREEZE_ROD", "BLAZE_SPAWN_EGG", "PIGLIN_BRUTE_SPAWN_EGG",
            "EMERALD", "GHAST_TEAR", "COPPER_HELMET", "FIREWORK_STAR", "GOLDEN_AXE", "BOOK", "TRAPPED_CHEST", "VINE"]
    for o, m in zip(opts[:11], mats[:11]):
        map_stat(o, m)
    map_stat(opts[11], "BLAZE_SPAWN_EGG")
    map_leveled("a1w_str1", {1: ("PIGLIN_BRUTE_SPAWN_EGG", "<red>力量 I")})
    for o, m in zip(opts[13:], mats[13:]):
        map_stat(o, m)
    return opts


def act1_strong():
    opts = [
        stat_attr("a1s_atk25", "<red>攻击+25", "ATTACK_DAMAGE", pct(25)),
        stat_attr("a1s_atk35", "<red>攻击+35", "ATTACK_DAMAGE", pct(35)),
        stat_attr("a1s_mag25", "<light_purple>魔攻+25", "MAGIC_DAMAGE", pct(25)),
        stat_attr("a1s_mag35", "<light_purple>魔攻+35", "MAGIC_DAMAGE", pct(35)),
        stat_attr("a1s_hp4", "<green>生命+4", "MAX_HEALTH", hp(4), op="FLAT"),
        stat_attr("a1s_hp6", "<green>生命+6", "MAX_HEALTH", hp(6), op="FLAT"),
        stat_attr("a1s_spd5", "<blue>速度+5", "MOVEMENT_SPEED", pct(5)),
        stat_potion("a1s_fire_res", "<gold>抗火", "FIRE_RESISTANCE", stack="IGNORE"),
        stat_potion("a1s_str1", "<red>力量 I", "STRENGTH", stack="UPGRADE_LEVEL", upgrade_max=1),
        stat_potion("a1s_regen1", "<green>生命恢复 I", "REGENERATION", stack="UPGRADE_LEVEL", upgrade_max=1),
        stat_attr("a1s_wave_atk_mag", "<dark_purple>稳定的原石祭坛", "ATTACK_DAMAGE", pct(3), trigger="ON_WAVE_CLEAR", stack="ADD"),
        stat_attr("a1s_wave_atk_mag2", "<dark_purple>稳定的原石祭坛", "MAGIC_DAMAGE", pct(3), trigger="ON_WAVE_CLEAR", stack="ADD"),
        stat_attr("a1s_wave_atk5", "<gray>清算名单", "ATTACK_DAMAGE", pct(5), trigger="ON_WAVE_CLEAR", stack="ADD"),
        stat_attr("a1s_wave_mag5", "<dark_gray>灵魂立方", "MAGIC_DAMAGE", pct(5), trigger="ON_WAVE_CLEAR", stack="ADD"),
        {"id": "a1s_wave_full_heal", "display": "<gold>温暖的篝火", "category": "STAT", "trigger": "ON_WAVE_CLEAR",
         "effect": "HEAL", "params": {"amount": 9999.0}, "unique": True, "stack": "IGNORE"},
        bundle("a1s_lost_key", "<gold>失落之钥", [
            stat_attr("a1s_act_atk20", "", "ATTACK_DAMAGE", pct(20), trigger="ON_ACT_ENTER"),
            stat_attr("a1s_act_mag20", "", "MAGIC_DAMAGE", pct(20), trigger="ON_ACT_ENTER"),
        ]),
        {"id": "a1s_hit_slow", "display": "<yellow>懒惰护符", "category": "STAT", "trigger": "ON_DAMAGE_DEALT",
         "effect": "BUFF_AREA", "params": {"targets": "hit_target", "potions": [{"potion": "SLOWNESS", "amp": 1, "duration_ticks": 200}]},
         "unique": True, "stack": "IGNORE"},
        {"id": "a1s_taken_str", "display": "<red>暴怒护符", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN",
         "effect": "BUFF_AREA", "params": {"targets": "self", "potions": [{"potion": "STRENGTH", "amp": 2, "duration_ticks": 40}],
         "fx": {"preset": "DOT_ABOVE", "particle": "FLAME"}}, "unique": True, "stack": "IGNORE"},
        {"id": "a1s_ally_invis", "display": "<gray>捕鳞蓑", "category": "STAT", "effect": "AURA",
         "params": {"radius": 10.0, "targets": "allies", "grant": {"effect": "ADD_POTION", "potion": "INVISIBILITY", "amp": 0}}},
    ]
    w = shared_weapons_a1() + [
        weapon("w_unlit_glory", "<white>未照耀的荣光", "maggoteers:unlit_glory"),
        weapon("w_knight_greatsword", "<blue>骑士巨剑", "maggoteers:knight_greatsword"),
        weapon("w_ritual_dagger", "<dark_purple>仪式匕首", "maggoteers:ritual_dagger"),
        weapon("w_holy_water", "<aqua>圣水", "maggoteers:holy_water"),
    ]
    opts.extend(w)
    return opts


def act1_boss():
    return [
        stat_attr("a1b_atk150", "<red>攻击+150", "ATTACK_DAMAGE", pct(150)),
        stat_attr("a1b_mag150", "<light_purple>魔攻+150", "MAGIC_DAMAGE", pct(150)),
        stat_attr("a1b_hp20", "<green>生命+20", "MAX_HEALTH", hp(20), op="FLAT"),
        stat_attr("a1b_spd15", "<blue>速度+15", "MOVEMENT_SPEED", pct(15)),
        stat_attr("a1b_kb1", "<gray>击退抗性+1", "KNOCKBACK_RESISTANCE", 1.0, op="FLAT"),
        stat_potion("a1b_res1", "<gray>抗性 I", "RESISTANCE", stack="UPGRADE_LEVEL", upgrade_max=1),
        {"id": "a1b_wave_gapple", "display": "<gold>赫拉克勒斯的果树", "category": "STAT", "trigger": "ON_WAVE_CLEAR",
         "effect": "GRANT_ITEM", "params": {"item": "maggoteers:boss_golden_apple", "count": 1}, "unique": True, "stack": "IGNORE"},
        {"id": "a1b_wave_coin", "display": "<yellow>贪婪护符", "category": "STAT", "trigger": "ON_WAVE_CLEAR",
         "effect": "GRANT_ITEM", "params": {"item": "maggoteers:currency_normal", "count": 1}, "unique": True, "stack": "ADD"},
        {"id": "a1b_wave_steak", "display": "<red>暴食护符", "category": "STAT", "trigger": "ON_WAVE_CLEAR",
         "effect": "GRANT_ITEM", "params": {"item": "maggoteers:full_heal_steak", "count": 1}, "unique": True, "stack": "IGNORE"},
        bundle("a1b_frost_tree", "<aqua>霜晶树", [
            {"id": "a1b_wave_coin2", "category": "STAT", "trigger": "ON_WAVE_CLEAR", "effect": "GRANT_ITEM",
             "params": {"item": "maggoteers:currency_normal", "count": 2}},
            stat_attr("a1b_hit_penalty", "", "MAX_HEALTH", -hp(1), op="FLAT", trigger="ON_DAMAGE_TAKEN", stack="ADD"),
        ]),
        {"id": "a1b_wave_tnt", "display": "<dark_red>死魂灵之影", "category": "STAT", "trigger": "ON_WAVE_CLEAR",
         "effect": "GRANT_ITEM", "params": {"item": "maggoteers:tnt_orb", "count": 1}, "unique": True, "stack": "IGNORE"},
        weapon("w_macro_wish", "<light_purple>宏愿", "maggoteers:macro_wish"),
        weapon("w_eighty", "<red>八十！", "maggoteers:eighty_hammer"),
        weapon("w_anacreon", "<gold>阿纳克吕翁", "maggoteers:anacreon"),
    ]


# Player weapon definitions (ItemCreator YAML fragments)
PLAYER_WEAPONS: dict[str, dict] = {}


def add_weapon(iid: str, **kwargs):
    key = f"maggoteers:{iid}"
    PLAYER_WEAPONS[key] = kwargs


def power_strike(iid, name, mat, dmg, bonus_pct, cd, lore_extra=None):
    lore = [f"<gray>右键：下次攻击 +{int(bonus_pct*100)}% 伤害", f"<dark_gray>冷却 {cd} 秒"]
    if lore_extra:
        lore.extend(lore_extra)
    add_weapon(iid, material=mat, customName=name, lore=lore, unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": f"maggoteers:{iid}"}],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": dmg, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               use_ability={"cooldown_sec": cd, "effect": "ADD_ATTRIBUTE",
                            "params": {"attr": "ATTACK_DAMAGE", "op": "PERCENT", "value": bonus_pct},
                            "expiry": {"trigger": "ON_DAMAGE_DEALT", "charges": 1}, "stack": "REPLACE"})


def build_player_weapons():
    power_strike("class_vanguard_axe", "<gold>初心者战斧", "IRON_AXE", 10, 1.0, 20)
    add_weapon("class_mage_sword", material="GOLDEN_SWORD", customName="<green>初心者魔剑",
               lore=["<gray>右键：4格范围 4 伤害", "<dark_gray>冷却 12 秒"], unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:class_mage_sword"}],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 5, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               use_ability={"cooldown_sec": 12, "effect": "DAMAGE_AREA",
                            "params": {"damage": 4.0, "radius": 4.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("class_heavy_sword", material="IRON_SWORD", customName="<blue>初心者圣剑",
               lore=["<gray>右键：范围治疗 10 心", "<dark_gray>冷却 20 秒"], unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:class_heavy_sword"}],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 6, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               use_ability={"cooldown_sec": 20, "effect": "HEAL_AREA",
                            "params": {"amount": 10.0, "radius": 5.0, "targets": "allies", "include_self": True}})
    add_weapon("class_sniper_bow", material="CROSSBOW", customName="<aqua>初心者之弓",
               lore=["<gray>右键：伤害光束 6", "<dark_gray>冷却 3 秒"], unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:class_sniper_bow"}],
               use_ability={"cooldown_sec": 3, "effect": "DAMAGE_BEAM",
                            "params": {"damage": 6.0, "ray_length": 32.0, "beam_radius": 1.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("class_guard_spear", material="IRON_SWORD", itemModel="minecraft:iron_spear",
               customName="<yellow>初心者长矛", lore=["<gray>10 伤害 · 快攻速 · +0.5 触及"],
               unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:class_guard_spear"}],
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 10, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 1.0, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:entity_interaction_range", "amount": 0.5, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ])
    power_strike("past_fine", "<gold>昔日精品", "IRON_AXE", 12, 1.2, 15)
    add_weapon("heat_cutter_a1", material="GOLDEN_SWORD", customName="<red>热熔切割器", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:heat_cutter_a1"}],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 5, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               use_ability={"cooldown_sec": 10, "effect": "DAMAGE_AREA",
                            "params": {"damage": 5.0, "radius": 4.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("blast_unit", material="IRON_SWORD", customName="<green>爆破单元", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:blast_unit"}],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 10, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               use_ability={"cooldown_sec": 20, "effect": "HEAL_AREA",
                            "params": {"amount": 14.0, "radius": 5.0, "targets": "allies", "include_self": True}})
    add_weapon("jet_a1", material="GOLDEN_SWORD", itemModel="minecraft:golden_spear", customName="<aqua>J.E.T",
               unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:jet_a1"}],
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 11, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 0.1, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:entity_interaction_range", "amount": 0.5, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ],
               use_ability={"cooldown_sec": 20, "effect": "ADD_POTION",
                            "params": {"potion": "SPEED", "amp": 2, "duration_ticks": 40}})
    add_weapon("wedge", material="IRON_SWORD", itemModel="minecraft:trident", customName="<blue>楔子",
               unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:wedge"}],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 6, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               use_ability={"cooldown_sec": 3, "effect": "DAMAGE_BEAM",
                            "params": {"damage": 7.0, "ray_length": 32.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("silver_sword", material="IRON_SWORD", customName="<white>猎魔人的银剑", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:smite: 6"],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 8, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:silver_sword"}])
    add_weapon("burn_blade", material="IRON_SWORD", customName="<red>一刀一刀燃烧刀", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:fire_aspect: 5"],
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 8, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 2.0, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ], pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:burn_blade"}])
    add_weapon("portable_fort", material="CROSSBOW", customName="<gray>便携要塞", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:portable_fort"}],
               use_ability={"cooldown_sec": 1, "effect": "DAMAGE_BEAM",
                            "params": {"damage": 3.0, "ray_length": 32.0, "targets": "enemies", "enemy_scope": "tracked"}},
               held_effects=[{"effect": "ADD_ATTRIBUTE", "params": {"attr": "MOVEMENT_SPEED", "op": "PERCENT", "value": -1.0}}])
    add_weapon("cat_hammer", material="MACE", customName="<light_purple>充气猫猫锤", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:cat_hammer"}],
               use_ability={"cooldown_sec": 5, "effect": "ADD_POTION",
                            "params": {"potion": "LEVITATION", "amp": 4, "duration_ticks": 4}})
    add_weapon("macro_wish", material="GOLDEN_SWORD", customName="<light_purple>宏愿", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:macro_wish"}],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 10, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               use_ability={"cooldown_sec": 10, "effect": "DAMAGE_AREA",
                            "params": {"damage": 8.0, "radius": 3.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("eighty_hammer", material="MACE", customName="<red>八十！", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:eighty_hammer"}],
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 28, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": -3.5, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ],
               use_ability={"cooldown_sec": 10, "effect": "DISABLE_AI",
                            "params": {"targets": "hit_target", "duration_ticks": 100},
                            "expiry": {"trigger": "ON_DAMAGE_DEALT", "charges": 1}, "stack": "REPLACE"})
    add_weapon("anacreon", material="COPPER_SWORD", customName="<gold>阿纳克吕翁", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:smite: 6", "minecraft:bane_of_arthropods: 6", "minecraft:impaling: 6"],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 9, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:anacreon"}])
    add_weapon("boss_golden_apple", material="GOLDEN_APPLE", customName="<gold>金苹果", maxStackSize=4,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:boss_golden_apple"}],
               use_ability={"cooldown_sec": 1, "consume": True, "effect": "HEAL_AREA",
                            "params": {"amount": 1000.0, "radius": 1.0, "targets": "allies", "include_self": True}})
    add_weapon("full_heal_steak", material="COOKED_BEEF", customName="<red>免费牛排！", maxStackSize=4,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:full_heal_steak"}],
               use_ability={"cooldown_sec": 1, "consume": True, "effect": "HEAL_AREA",
                            "params": {"amount": 9999.0, "radius": 1.0, "targets": "allies", "include_self": True}})
    add_weapon("tnt_orb", material="TNT", customName="<dark_red>TNT 宝珠", maxStackSize=4,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:tnt_orb"}],
               use_ability={"cooldown_sec": 1, "consume": True, "effect": "SUMMON",
                            "params": {"entity": "TNT", "cleanup": "duration", "duration_sec": 10, "friendly_fire": False}})
    add_weapon("unlit_glory", material="GOLDEN_SWORD", customName="<white>未照耀的荣光", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:sharpness: 100"],
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 0, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": -3.9, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:unlit_glory"}],
               held_effects=[
                   {"effect": "ADD_POTION", "params": {"potion": "RESISTANCE", "amp": 0, "duration_ticks": 0}},
                   {"effect": "DAMAGE_AREA", "trigger": "ON_DAMAGE_TAKEN", "params": {"damage": 2.0, "radius": 1.0, "targets": "enemies", "enemy_scope": "tracked"}},
               ])
    add_weapon("knight_greatsword", material="SHIELD", itemModel="minecraft:iron_sword", customName="<blue>骑士巨剑",
               unbreakable=True, maxStackSize=1,
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 8, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 0.2, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:entity_interaction_range", "amount": 1.0, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:knight_greatsword"}],
               held_effects=[
                   {"effect": "ADD_POTION", "params": {"potion": "RESISTANCE", "amp": 0, "duration_ticks": 0}},
                   {"effect": "ADD_POTION", "params": {"potion": "SLOWNESS", "amp": 0, "duration_ticks": 0}},
               ])
    add_weapon("ritual_dagger", material="FLINT", customName="<dark_purple>仪式匕首", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:ritual_dagger"}],
               use_ability={"cooldown_sec": 20, "effect": "ADD_POTION",
                            "params": {"potion": "SPEED", "amp": 0, "duration_ticks": 100}},
               held_effects=[{"effect": "ADD_ATTRIBUTE", "params": {"attr": "MAGIC_DAMAGE", "op": "PERCENT", "value": 1.0}}])
    add_weapon("holy_water", material="POTION", customName="<aqua>圣水", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:holy_water"}],
               use_ability={"cooldown_sec": 15, "effect": "BUFF_AREA",
                            "params": {"targets": "self",
                                       "potions": purify_potions() + [{"potion": "INSTANT_HEALTH", "amp": 1, "duration_ticks": 1}]}})
    power_strike("shattered_king", "<gold>破碎君王", "COPPER_AXE", 15, 1.5, 12)
    add_weapon("famed_name", material="DIAMOND_SWORD", customName="<aqua>显赫声名", unbreakable=True, maxStackSize=1,
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 5, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:famed_name"}],
               use_ability={"cooldown_sec": 9, "effect": "DAMAGE_AREA",
                            "params": {"damage": 6.0, "radius": 4.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("knight_spirit", material="IRON_SWORD", customName="<blue>骑士精神", unbreakable=True, maxStackSize=1,
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 10, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:knight_spirit"}],
               use_ability={"cooldown_sec": 20, "effect": "HEAL_AREA",
                            "params": {"amount": 14.0, "radius": 5.0, "targets": "allies", "include_self": True}})
    add_weapon("valor", material="IRON_SWORD", itemModel="minecraft:iron_spear", customName="<yellow>骁勇", unbreakable=True, maxStackSize=1,
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 12, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 0.1, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:entity_interaction_range", "amount": 1.0, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:valor"}],
               use_ability={"cooldown_sec": 15, "effect": "ADD_POTION", "params": {"potion": "SPEED", "amp": 2, "duration_ticks": 40}})
    add_weapon("navigator", material="BOW", customName="<green>领航者", unbreakable=True, maxStackSize=1,
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 7, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:navigator"}],
               use_ability={"cooldown_sec": 2, "effect": "DAMAGE_BEAM",
                            "params": {"damage": 8.0, "ray_length": 32.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("griffon_claw", material="NETHERITE_HOE", customName="<white>骏鹰之爪", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:sweeping_edge: 10"],
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 10, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:entity_interaction_range", "amount": 0.5, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ], pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:griffon_claw"}])
    add_weapon("spike_wall", material="SHIELD", itemModel="minecraft:iron_sword", customName="<gray>尖刺壁垒", unbreakable=True, maxStackSize=1,
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 10, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": -0.2, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ], pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:spike_wall"}],
               use_ability={"cooldown_sec": 1, "effect": "DAMAGE_AREA",
                            "params": {"damage": 2.0, "radius": 2.0, "targets": "enemies", "enemy_scope": "tracked"}},
               held_effects=[{"effect": "DAMAGE_AREA", "trigger": "ON_DAMAGE_TAKEN",
                              "params": {"damage": 2.0, "radius": 2.0, "targets": "enemies", "enemy_scope": "tracked"}}])
    add_weapon("supreme_art", material="IRON_SWORD", customName="<light_purple>至高之术", unbreakable=True, maxStackSize=1,
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 4, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 2.0, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:entity_interaction_range", "amount": 0.5, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ], pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:supreme_art"}],
               held_effects=[{"effect": "BUFF_AREA", "trigger": "ON_KILL", "params": {"targets": "self",
                              "potions": [{"potion": "HASTE", "amp": 1, "duration_ticks": 100},
                                          {"potion": "STRENGTH", "amp": 1, "duration_ticks": 100}]}}])
    add_weapon("emp", material="COPPER_BLOCK", customName="<blue>E.M.P", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:emp"}],
               use_ability={"cooldown_sec": 40, "effect": "BUFF_AREA",
                            "params": {"radius": 8.0, "targets": "enemies", "enemy_scope": "tracked",
                                       "potions": silence_potions() + [{"potion": "SLOWNESS", "amp": 9, "duration_ticks": 200}]}})
    add_weapon("phantasm", material="GOLDEN_SWORD", customName="<blue>幻景", unbreakable=True, maxStackSize=1,
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 8, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:phantasm"}],
               use_ability={"cooldown_sec": 20, "effect": "ADD_POTION",
                            "self_potions": purify_potions() + self_harm_instant(),
                            "params": {"potion": "RESISTANCE", "amp": 4, "duration_ticks": 0},
                            "expiry": {"trigger": "ON_DAMAGE_DEALT", "charges": 1}, "stack": "REPLACE"})
    add_weapon("heal_potion_item", material="POTION", customName="<red>瞬间治疗药水", maxStackSize=8,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:heal_potion_item"}],
               use_ability={"cooldown_sec": 1, "consume": True, "effect": "HEAL_AREA",
                            "params": {"amount": 10.0, "radius": 1.0, "targets": "allies", "include_self": True}})
    add_weapon("splash_heal2", material="SPLASH_POTION", customName="<red>喷溅治疗II", maxStackSize=8,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:splash_heal2"}],
               use_ability={"cooldown_sec": 1, "consume": True, "effect": "HEAL_AREA",
                            "params": {"amount": 16.0, "radius": 4.0, "targets": "allies", "include_self": True}})
    add_weapon("excalibur_a3", material="GOLDEN_SWORD", customName="<gold>Excalibur", unbreakable=True, maxStackSize=1,
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 12, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:excalibur_a3"}],
               use_ability={"cooldown_sec": 25, "effect": "DAMAGE_BEAM",
                            "params": {"damage": 30.0, "ray_length": 40.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("mystery_blade", material="DIAMOND_SWORD", customName="<dark_gray>？？？？", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:sharpness: 6", "minecraft:smite: 5"],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 12, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:mystery_blade"}])
    add_weapon("frostmourne", material="DIAMOND_SWORD", customName="<blue>霜之哀伤", unbreakable=True, maxStackSize=1,
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 18, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:frostmourne"}],
               held_effects=[{"effect": "ADD_POTION", "params": {"potion": "WITHER", "amp": 0, "duration_ticks": 0}}],
               use_ability={"cooldown_sec": 8, "effect": "DAMAGE_AREA",
                            "params": {"damage": 2.0, "radius": 4.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("bible", material="BOOK", customName="<white>圣经", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:smite: 20"], pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:bible"}])
    add_weapon("herafinger", material="DIAMOND_SWORD", customName="<gold>赫拉芬格", unbreakable=True, maxStackSize=1,
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 18, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 0.0, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ], pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:herafinger"}],
               use_ability={"cooldown_sec": 10, "effect": "ADD_ATTRIBUTE",
                            "params": {"attr": "ATTACK_DAMAGE", "op": "PERCENT", "value": 2.0},
                            "expiry": {"trigger": "ON_DAMAGE_DEALT", "charges": 1}, "stack": "REPLACE"})
    add_weapon("molten_flame", material="COPPER_SWORD", customName="<red>熔铸火焰", unbreakable=True, maxStackSize=1,
               enchantments=["minecraft:fire_aspect: 2"],
               attributeModifiers=[{"attribute": "minecraft:attack_damage", "amount": 8, "operation": "ADD_NUMBER", "slot": "mainhand"}],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:molten_flame"}],
               use_ability={"cooldown_sec": 8, "effect": "DAMAGE_AREA",
                            "params": {"damage": 8.0, "radius": 5.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("mission_sure", material="HEART_OF_THE_SEA", customName="<aqua>使命必达", unbreakable=True, maxStackSize=1,
               attributeModifiers=[
                   {"attribute": "minecraft:attack_damage", "amount": 6, "operation": "ADD_NUMBER", "slot": "mainhand"},
                   {"attribute": "minecraft:attack_speed", "amount": 2.0, "operation": "ADD_NUMBER", "slot": "mainhand"},
               ], pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:mission_sure"}],
               use_ability={"cooldown_sec": 12, "effect": "HEAL_AREA",
                            "params": {"amount": 20.0, "radius": 5.0, "targets": "allies", "include_self": True}})
    add_weapon("art_tyrant", material="CROSSBOW", customName="<light_purple>艺术暴君", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:art_tyrant"}],
               held_effects=[{"effect": "ADD_ATTRIBUTE", "params": {"attr": "MOVEMENT_SPEED", "op": "PERCENT", "value": 0.10}}],
               use_ability={"cooldown_sec": 1.5, "effect": "DAMAGE_BEAM",
                            "params": {"damage": 10.0, "ray_length": 32.0, "targets": "enemies", "enemy_scope": "tracked"}})
    add_weapon("thunder_rod", material="LIGHTNING_ROD", customName="<yellow>雷霆之杖", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:thunder_rod"}],
               use_ability={"cooldown_sec": 8, "effect": "DISABLE_AI",
                            "params": {"targets": "hit_target", "duration_ticks": 80}})
    add_weapon("cataclysm", material="OBSIDIAN", customName="<dark_red>灾变核心", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:cataclysm"}],
               use_ability={"cooldown_sec": 40, "effect": "DAMAGE_AREA",
                            "params": {"damage": 20.0, "radius": 8.0, "targets": "enemies", "enemy_scope": "tracked",
                                       "potions": silence_potions()}},
               held_effects=[{"effect": "BUFF_AREA", "trigger": "ON_DAMAGE_DEALT",
                              "params": {"targets": "self", "potions": self_harm_instant()}}])
    add_weapon("bleed_knife", material="IRON_INGOT", customName="<red>放血小刀", maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:bleed_knife"}],
               use_ability={"cooldown_sec": 1, "effect": "BUFF_AREA",
                            "params": {"targets": "self", "potions": self_harm_poison()}})
    add_weapon("old_wand", material="STICK", customName="<dark_purple>老魔杖", unbreakable=True, maxStackSize=1,
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:old_wand"}],
               use_ability={"cooldown_sec": 60, "effect": "DAMAGE_BEAM",
                            "self_potions": self_harm_instant(),
                            "params": {"damage": 1000.0, "ray_length": 48.0, "targets": "enemies", "enemy_scope": "tracked"}},
               held_effects=[{"effect": "ADD_ATTRIBUTE", "trigger": "ON_KILL", "stack": "ADD",
                              "params": {"attr": "MAX_HEALTH", "op": "FLAT", "value": -6.0}}])
    add_weapon("glass_blade", material="DIAMOND_HOE", customName="<aqua>玻璃刀",
               lore=["<gray>耐久 1 · 锋利 100"], maxStackSize=1,
               enchantments=["minecraft:sharpness: 100"],
               pdc=[{"key": "maggoteers:id", "type": "STRING", "value": "maggoteers:glass_blade"}])


def shared_weapons_a2():
    return [
        weapon("w_shattered_king", "<gold>破碎君王", "maggoteers:shattered_king"),
        weapon("w_famed_name", "<aqua>显赫声名", "maggoteers:famed_name"),
        weapon("w_knight_spirit", "<blue>骑士精神", "maggoteers:knight_spirit"),
        weapon("w_valor", "<yellow>骁勇", "maggoteers:valor"),
        weapon("w_navigator", "<green>领航者", "maggoteers:navigator"),
        weapon("w_griffon_claw", "<white>骏鹰之爪", "maggoteers:griffon_claw"),
        weapon("w_spike_wall", "<gray>尖刺壁垒", "maggoteers:spike_wall"),
        weapon("w_supreme_art", "<light_purple>至高之术", "maggoteers:supreme_art"),
        weapon("w_emp", "<blue>E.M.P", "maggoteers:emp"),
    ]


def act2_weak():
    opts = [
        stat_attr("a2w_atk35", "<red>攻击+35", "ATTACK_DAMAGE", pct(35)),
        stat_attr("a2w_atk40", "<red>攻击+40", "ATTACK_DAMAGE", pct(40)),
        stat_attr("a2w_atk45", "<red>攻击+45", "ATTACK_DAMAGE", pct(45)),
        stat_attr("a2w_mag35", "<light_purple>魔攻+35", "MAGIC_DAMAGE", pct(35)),
        stat_attr("a2w_mag40", "<light_purple>魔攻+40", "MAGIC_DAMAGE", pct(40)),
        stat_attr("a2w_mag45", "<light_purple>魔攻+45", "MAGIC_DAMAGE", pct(45)),
        stat_attr("a2w_hp6", "<green>生命+6", "MAX_HEALTH", hp(6), op="FLAT"),
        stat_attr("a2w_hp8a", "<green>生命+8", "MAX_HEALTH", hp(8), op="FLAT"),
        stat_attr("a2w_hp8b", "<green>生命+8", "MAX_HEALTH", hp(8), op="FLAT"),
        stat_attr("a2w_spd8a", "<blue>速度+8", "MOVEMENT_SPEED", pct(8)),
        stat_attr("a2w_spd8b", "<blue>速度+8", "MOVEMENT_SPEED", pct(8)),
        stat_attr("a2w_spd10", "<blue>速度+10", "MOVEMENT_SPEED", pct(10)),
        stat_attr("a2w_reach_a", "<gray>触及+0.5", "ENTITY_INTERACTION_RANGE", 0.5, op="FLAT"),
        stat_attr("a2w_reach_b", "<gray>触及+0.5", "ENTITY_INTERACTION_RANGE", 0.5, op="FLAT"),
        stat_potion("a2w_str2", "<red>力量(可升级)", "STRENGTH", stack="UPGRADE_LEVEL", upgrade_max=2),
        stat_potion("a2w_regen2", "<green>生命恢复(可升级)", "REGENERATION", stack="UPGRADE_LEVEL", upgrade_max=2),
        {"id": "a2w_kill_str", "display": "<gold>嗜血之颅", "category": "STAT", "trigger": "ON_KILL", "effect": "BUFF_AREA",
         "params": {"targets": "self", "potions": [{"potion": "STRENGTH", "amp": 1, "duration_ticks": 40}]}, "unique": True, "stack": "IGNORE"},
        {"id": "a2w_kill_spd", "display": "<red>狂暴之颅", "category": "STAT", "trigger": "ON_KILL", "effect": "BUFF_AREA",
         "params": {"targets": "self", "potions": [{"potion": "SPEED", "amp": 1, "duration_ticks": 40}]}, "unique": True, "stack": "IGNORE"},
        {"id": "a2w_kill_heal", "display": "<green>肉食之颅", "category": "STAT", "trigger": "ON_KILL", "effect": "HEAL",
         "params": {"amount": 4.0}, "unique": True, "stack": "IGNORE"},
        {"id": "a2w_kill_boom", "display": "<dark_green>裂变之颅", "category": "STAT", "trigger": "ON_KILL", "effect": "DAMAGE_AREA",
         "params": {"damage": 4.0, "radius": 3.0, "targets": "enemies", "enemy_scope": "tracked"}, "unique": True, "stack": "IGNORE"},
        {"id": "a2w_kill_wither", "display": "<dark_gray>凋萎之颅", "category": "STAT", "trigger": "ON_KILL", "effect": "BUFF_AREA",
         "params": {"radius": 3.0, "targets": "enemies", "enemy_scope": "tracked",
                    "potions": [{"potion": "WITHER", "amp": 0, "duration_ticks": 40}]}, "unique": True, "stack": "IGNORE"},
        {"id": "a2w_death_golem", "display": "<gray>便携魔像", "category": "STAT", "trigger": "ON_DEATH", "effect": "SUMMON",
         "params": {"entity": "IRON_GOLEM", "anchor": "death_site", "cleanup": "wave_clear", "friendly_fire": False}, "unique": True, "stack": "IGNORE"},
        {"id": "a2w_hit_wither", "display": "<dark_gray>腐败之油", "category": "STAT", "trigger": "ON_DAMAGE_DEALT", "effect": "BUFF_AREA",
         "params": {"targets": "hit_target", "potions": [{"potion": "WITHER", "amp": 0, "duration_ticks": 200}]}, "unique": True, "stack": "IGNORE"},
        stat_attr("a2w_cursed_blade", "<dark_red>诅咒之刃", "ATTACK_DAMAGE", pct(300), desc="+300攻/魔，受击额外-10血"),
        stat_attr("a2w_cursed_blade_mag", "<dark_red>诅咒之刃", "MAGIC_DAMAGE", pct(300)),
    ]
    opts.extend(shared_weapons_a2())
    map_leveled("a2w_str2", {1: ("PIGLIN_BRUTE_SPAWN_EGG", "<red>力量 I"), 2: ("PIGLIN_BRUTE_SPAWN_EGG", "<red>力量 II")})
    map_leveled("a2w_regen2", {1: ("ALLAY_SPAWN_EGG", "<green>再生 I"), 2: ("ALLAY_SPAWN_EGG", "<green>再生 II")})
    return opts


def act2_strong():
    opts = [
        stat_attr("a2s_atk40", "<red>攻击+40", "ATTACK_DAMAGE", pct(40)),
        stat_attr("a2s_atk45", "<red>攻击+45", "ATTACK_DAMAGE", pct(45)),
        stat_attr("a2s_mag40", "<light_purple>魔攻+40", "MAGIC_DAMAGE", pct(40)),
        stat_attr("a2s_mag45", "<light_purple>魔攻+45", "MAGIC_DAMAGE", pct(45)),
        stat_attr("a2s_hp8a", "<green>生命+8", "MAX_HEALTH", hp(8), op="FLAT"),
        stat_attr("a2s_hp8b", "<green>生命+8", "MAX_HEALTH", hp(8), op="FLAT"),
        stat_attr("a2s_spd8", "<blue>速度+8", "MOVEMENT_SPEED", pct(8)),
        stat_attr("a2s_spd10", "<blue>速度+10", "MOVEMENT_SPEED", pct(10)),
        stat_attr("a2s_reach_a", "<gray>触及+0.5", "ENTITY_INTERACTION_RANGE", 0.5, op="FLAT"),
        stat_attr("a2s_reach_b", "<gray>触及+0.5", "ENTITY_INTERACTION_RANGE", 0.5, op="FLAT"),
        stat_potion("a2s_str2", "<red>力量(可升级)", "STRENGTH", stack="UPGRADE_LEVEL", upgrade_max=2),
        stat_potion("a2s_regen2", "<green>生命恢复(可升级)", "REGENERATION", stack="UPGRADE_LEVEL", upgrade_max=2),
        {"id": "a2s_wave_potion", "display": "<red>野战医疗箱", "category": "STAT", "trigger": "ON_WAVE_CLEAR", "effect": "GRANT_ITEM",
         "params": {"item": "maggoteers:heal_potion_item", "count": 2}, "unique": True, "stack": "IGNORE"},
        {"id": "a2s_wave_splash", "display": "<aqua>便携生物手雷制造站", "category": "STAT", "trigger": "ON_WAVE_CLEAR", "effect": "GRANT_ITEM",
         "params": {"item": "maggoteers:splash_heal2", "count": 2}, "unique": True, "stack": "IGNORE"},
        bundle("a2s_arrogance", "<gold>傲慢护符", [
            stat_attr("a2s_arrogance_atk", "", "ATTACK_DAMAGE", pct(50), trigger="ON_WAVE_CLEAR", stack="ADD"),
            stat_attr("a2s_arrogance_mag", "", "MAGIC_DAMAGE", pct(50), trigger="ON_WAVE_CLEAR", stack="ADD"),
            {"id": "a2s_arrogance_clear", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN", "effect": "ADD_ATTRIBUTE",
             "params": {"op": "REVOKE_GRANTS", "source_id": "a2s_arrogance_atk"}},
            {"id": "a2s_arrogance_clear2", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN", "effect": "ADD_ATTRIBUTE",
             "params": {"op": "REVOKE_GRANTS", "source_id": "a2s_arrogance_mag"}},
        ]),
        {"id": "a2s_enemy_regen", "display": "<light_purple>桃木剑", "category": "STAT", "effect": "AURA",
         "params": {"radius": 6.0, "targets": "enemies", "enemy_scope": "tracked",
                    "grant": {"effect": "ADD_POTION", "potion": "REGENERATION", "amp": 0}}},
        {"id": "a2s_ally_str", "display": "<red>岩角号", "category": "STAT", "effect": "AURA",
         "params": {"radius": 5.0, "targets": "allies",
                    "grant": {"effect": "ADD_POTION", "potion": "STRENGTH", "amp": 1}}},
        {"id": "a2s_taken_splash", "display": "<aqua>刺猬胸甲", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN", "effect": "DAMAGE_AREA",
         "params": {"damage": 4.0, "radius": 3.0, "targets": "enemies", "enemy_scope": "tracked"}, "unique": True, "stack": "IGNORE"},
        bundle("a2s_martyr", "<gold>殉道者", [
            {"id": "a2s_martyr_heal", "category": "STAT", "trigger": "ON_DEATH", "effect": "HEAL_AREA",
             "params": {"amount": 9999.0, "radius": 64.0, "targets": "allies", "include_self": False}},
            {"id": "a2s_martyr_buff", "category": "STAT", "trigger": "ON_DEATH", "effect": "BUFF_AREA",
             "params": {"radius": 64.0, "targets": "allies", "include_self": False,
                        "potions": [{"potion": "STRENGTH", "amp": 1, "duration_ticks": 1200},
                                    {"potion": "SPEED", "amp": 1, "duration_ticks": 1200}]}},
        ]),
    ]
    opts.extend(shared_weapons_a2() + [
        weapon("w_condense", "<blue>凝结核", "maggoteers:condense"),
        weapon("w_corrupt_orb", "<dark_purple>腐化宝珠", "maggoteers:corrupt_orb"),
        weapon("w_shimingzi", "<gold>Shimingzi", "maggoteers:shimingzi"),
        weapon("w_bleed_knife", "<red>放血小刀", "maggoteers:bleed_knife"),
        weapon("w_miaooneal", "<yellow>妙奥尼尔", "maggoteers:miaooneal"),
    ])
    return opts


def act2_boss():
    return [
        stat_attr("a2b_atk225", "<red>攻击+225", "ATTACK_DAMAGE", pct(225)),
        stat_attr("a2b_mag225", "<light_purple>魔攻+225", "MAGIC_DAMAGE", pct(225)),
        stat_attr("a2b_hp30", "<green>生命+30", "MAX_HEALTH", hp(30), op="FLAT"),
        stat_attr("a2b_spd20", "<blue>速度+20", "MOVEMENT_SPEED", pct(20)),
        stat_attr("a2b_reach1", "<gray>触及+1", "ENTITY_INTERACTION_RANGE", 1.0, op="FLAT"),
        stat_potion("a2b_invis", "<gray>隐身", "INVISIBILITY", stack="IGNORE"),
        {"id": "a2b_hit_heal", "display": "<green>嫉妒护符", "category": "STAT", "trigger": "ON_DAMAGE_DEALT", "effect": "HEAL",
         "params": {"amount": 1.0}, "unique": True, "stack": "IGNORE"},
        {"id": "a2b_taken_heal", "display": "<aqua>恐鱼生", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN", "effect": "HEAL",
         "params": {"amount": 2.0}, "unique": True, "stack": "IGNORE"},
        {"id": "a2b_hit_slow10", "display": "<light_purple>安眠迷香", "category": "STAT", "trigger": "ON_DAMAGE_DEALT", "effect": "BUFF_AREA",
         "params": {"targets": "hit_target", "potions": [{"potion": "SLOWNESS", "amp": 9, "duration_ticks": 20}]}, "unique": True, "stack": "IGNORE"},
        {"id": "a2b_taken_lev", "display": "<white>天穹尘埃", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN", "effect": "BUFF_AREA",
         "params": {"targets": "attacker", "potions": [{"potion": "LEVITATION", "amp": 4, "duration_ticks": 20}]}, "unique": True, "stack": "IGNORE"},
        {"id": "a2b_frost_aura", "display": "<aqua>寒霜领域", "category": "STAT", "effect": "AURA",
         "params": {"radius": 5.0, "targets": "enemies", "enemy_scope": "tracked",
                    "grant": {"effect": "ADD_POTION", "potion": "SLOWNESS", "amp": 1}}},
        weapon("w_old_wand", "<dark_purple>老魔杖", "maggoteers:old_wand"),
        weapon("w_revive_stone", "<light_purple>复活石", "maggoteers:revive_coin",
               desc="右键：选人复活或+1复活次数（同复活币）"),
    ]


def act3_weak():
    return [
        stat_potion("a3w_imm_wither", "<dark_gray>免疫凋零/黑暗", "WITHER", amp=255, stack="IGNORE"),
        stat_potion("a3w_imm_poison", "<green>免疫中毒/虚弱", "POISON", amp=255, stack="IGNORE"),
        stat_potion("a3w_imm_slow", "<blue>免疫缓慢/失明", "SLOWNESS", amp=255, stack="IGNORE"),
        weapon("w_mystery_blade", "<dark_gray>？？？？", "maggoteers:mystery_blade"),
        {
            "id": "a3w_glass_regen",
            "display": "<aqua>自修复玻璃刀",
            "description": "每波清空获得一把玻璃刀",
            "category": "STAT",
            "trigger": "ON_WAVE_CLEAR",
            "effect": "GRANT_ITEM",
            "params": {"item": "maggoteers:glass_blade", "count": 1},
            "unique": True,
            "stack": "IGNORE",
        },
        weapon("w_excalibur_a3", "<gold>Excalibur", "maggoteers:excalibur_a3"),
        weapon("w_solar_spear", "<yellow>太阳王的长矛", "maggoteers:solar_spear"),
        weapon("w_frostmourne", "<blue>霜之哀伤", "maggoteers:frostmourne"),
        weapon("w_bible", "<white>圣经", "maggoteers:bible"),
    ]


def act3_strong():
    opts = [
        stat_attr("a3s_atk60", "<red>攻击+60", "ATTACK_DAMAGE", pct(60)),
        stat_attr("a3s_atk70", "<red>攻击+70", "ATTACK_DAMAGE", pct(70)),
        stat_attr("a3s_atk80", "<red>攻击+80", "ATTACK_DAMAGE", pct(80)),
        stat_attr("a3s_mag60", "<light_purple>魔攻+60", "MAGIC_DAMAGE", pct(60)),
        stat_attr("a3s_mag70", "<light_purple>魔攻+70", "MAGIC_DAMAGE", pct(70)),
        stat_attr("a3s_mag80", "<light_purple>魔攻+80", "MAGIC_DAMAGE", pct(80)),
        stat_attr("a3s_hp12", "<green>生命+12", "MAX_HEALTH", hp(12), op="FLAT"),
        stat_attr("a3s_hp18", "<green>生命+18", "MAX_HEALTH", hp(18), op="FLAT"),
        stat_attr("a3s_hp30", "<green>生命+30", "MAX_HEALTH", hp(30), op="FLAT"),
        stat_attr("a3s_spd10", "<blue>速度+10", "MOVEMENT_SPEED", pct(10)),
        stat_attr("a3s_spd15", "<blue>速度+15", "MOVEMENT_SPEED", pct(15)),
        stat_attr("a3s_spd20", "<blue>速度+20", "MOVEMENT_SPEED", pct(20)),
        stat_attr("a3s_reach1a", "<gray>触及+1", "ENTITY_INTERACTION_RANGE", 1.0, op="FLAT"),
        stat_attr("a3s_reach1b", "<gray>触及+1", "ENTITY_INTERACTION_RANGE", 1.0, op="FLAT"),
        stat_attr("a3s_kb1a", "<gray>击退抗性+1", "KNOCKBACK_RESISTANCE", 1.0, op="FLAT"),
        stat_attr("a3s_kb1b", "<gray>击退抗性+1", "KNOCKBACK_RESISTANCE", 1.0, op="FLAT"),
        stat_potion("a3s_res3", "<gray>抗性(可升级)", "RESISTANCE", stack="UPGRADE_LEVEL", upgrade_max=3),
        stat_potion("a3s_str3", "<red>力量(可升级)", "STRENGTH", stack="UPGRADE_LEVEL", upgrade_max=3),
        stat_potion("a3s_regen4", "<green>生命恢复(可升级)", "REGENERATION", stack="UPGRADE_LEVEL", upgrade_max=4),
        {"id": "a3s_taken_res", "display": "<gray>咒魂护符", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN", "effect": "BUFF_AREA",
         "params": {"targets": "self", "potions": [{"potion": "RESISTANCE", "amp": 3, "duration_ticks": 20}]}, "unique": True, "stack": "IGNORE"},
        stat_attr("a3s_kill_atk10", "<red>嗜血之斧", "ATTACK_DAMAGE", pct(10), trigger="ON_KILL", stack="ADD",
                  expiry={"trigger": "ON_WAVE_CLEAR", "charges": -1}),
        stat_attr("a3s_kill_mag10", "<light_purple>嗜血宝石", "MAGIC_DAMAGE", pct(10), trigger="ON_KILL", stack="ADD",
                  expiry={"trigger": "ON_WAVE_CLEAR", "charges": -1}),
        {"id": "a3s_ally_regen", "display": "<white>白玫瑰", "category": "STAT", "effect": "AURA",
         "params": {"radius": 5.0, "targets": "allies", "grant": {"effect": "ADD_POTION", "potion": "REGENERATION", "amp": 0}}},
        {"id": "a3s_taken_abs", "display": "<yellow>护心镜", "category": "STAT", "trigger": "ON_DAMAGE_TAKEN", "effect": "BUFF_AREA",
         "params": {"targets": "self", "potions": [{"potion": "ABSORPTION", "amp": 0, "duration_ticks": 20}]}, "unique": True, "stack": "IGNORE"},
        bundle("a3s_lust", "<light_purple>色欲护符", [
            stat_potion("a3s_lust_abs", "", "ABSORPTION", amp=0, stack="IGNORE"),
            stat_attr("a3s_lust_atk", "", "ATTACK_DAMAGE", pct(-20)),
            stat_attr("a3s_lust_mag", "", "MAGIC_DAMAGE", pct(-20)),
        ], desc="持续伤害吸收 I，攻/魔 -20"),
    ]
    opts.extend([
        weapon("w_herafinger", "<gold>赫拉芬格", "maggoteers:herafinger"),
        weapon("w_molten_flame", "<red>熔铸火焰", "maggoteers:molten_flame"),
        weapon("w_mission_sure", "<aqua>使命必达", "maggoteers:mission_sure"),
        weapon("w_red_blessing", "<red>镀红祝福", "maggoteers:red_blessing"),
        weapon("w_art_tyrant", "<light_purple>艺术暴君", "maggoteers:art_tyrant"),
        weapon("w_phantasm", "<blue>幻景", "maggoteers:phantasm"),
        weapon("w_paragon", "<yellow>典范", "maggoteers:paragon"),
        weapon("w_thunder_rod", "<yellow>雷霆之杖", "maggoteers:thunder_rod"),
        weapon("w_cataclysm", "<dark_red>灾变核心", "maggoteers:cataclysm"),
    ])
    map_leveled("a3s_res3", {i: ("IRON_GOLEM_SPAWN_EGG", f"<gray>抗性 {i}") for i in range(1, 4)})
    map_leveled("a3s_str3", {i: ("PIGLIN_BRUTE_SPAWN_EGG", f"<red>力量 {i}") for i in range(1, 4)})
    map_leveled("a3s_regen4", {i: ("ALLAY_SPAWN_EGG", f"<green>再生 {i}") for i in range(1, 5)})
    return opts


def act3_boss():
    return [
        weapon("w_revive_coin", "<green>复活币", "maggoteers:revive_coin", desc="Boss 池补给"),
    ]


def build_rewards_yaml_from_pools(pools_data):
    pools = {
        "class": {"cost": 0, "options": pools_data["class"]},
        "act1_weak": {"cost": 1, "currency": "normal", "upgrade_level_cap": 2, "options": pools_data["act1_weak"]},
        "act1_strong": {"cost": 1, "currency": "normal", "upgrade_level_cap": 3, "options": pools_data["act1_strong"]},
        "act1_boss": {"cost": 1, "currency": "boss", "options": pools_data["act1_boss"]},
        "act2_weak": {"cost": 1, "currency": "normal", "upgrade_level_cap": 2, "options": pools_data["act2_weak"]},
        "act2_strong": {"cost": 1, "currency": "normal", "upgrade_level_cap": 2, "options": pools_data["act2_strong"]},
        "act2_boss": {"cost": 1, "currency": "boss", "options": pools_data["act2_boss"]},
        "act3_weak": {"cost": 1, "currency": "normal", "options": pools_data["act3_weak"]},
        "act3_strong": {"cost": 1, "currency": "normal", "upgrade_level_cap": 4, "options": pools_data["act3_strong"]},
        "act3_boss": {"cost": 1, "currency": "boss", "options": pools_data["act3_boss"]},
    }
    return "# 奖励池（3 选 1 / 开局职业）— 由 rewardplan 生成\n\nreward_pools:\n" + ydump(pools, 2)


def build_rewards_yaml():
    pools_data = {
        "class": class_pools(),
        "act1_weak": act1_weak(),
        "act1_strong": act1_strong(),
        "act1_boss": act1_boss(),
        "act2_weak": act2_weak(),
        "act2_strong": act2_strong(),
        "act2_boss": act2_boss(),
        "act3_weak": act3_weak(),
        "act3_strong": act3_strong(),
        "act3_boss": act3_boss(),
    }
    return build_rewards_yaml_from_pools(pools_data)


def render_item(key: str, spec: dict) -> str:
    lines = [f"{key}:"]
    for k, v in spec.items():
        if isinstance(v, (dict, list)):
            lines.append(f"  {k}:")
            lines.append(textwrap.indent(ydump(v, 0), "    ").rstrip())
        elif isinstance(v, bool):
            lines.append(f"  {k}: {'true' if v else 'false'}")
        elif isinstance(v, str):
            lines.append(f'  {k}: "{v}"' if ":" in v or v.startswith("<") else f"  {k}: {v}")
        else:
            lines.append(f"  {k}: {v}")
    return "\n".join(lines)


def build_items_yaml():
    header = textwrap.dedent("""\
        # 非券种物品（rewardplan 生成）
        # ItemCreator 格式；PDC maggoteers:id 供 ItemInteractRouter 识别

        maggoteers:revive_coin:
          material: TOTEM_OF_UNDYING
          customName: "<green>复活币"
          lore:
            - "<gray>对死者：拉回战场"
            - "<gray>对活者：+1 自动复活次数"
          glint: true
          pdc:
            - key: maggoteers:kind
              type: STRING
              value: revive_coin

        maggoteers:supply_healing:
          material: GOLDEN_APPLE
          customName: "<red>应急口粮"
          lore:
            - "<gray>立即恢复 6 颗心"
          pdc:
            - key: maggoteers:kind
              type: STRING
              value: supply_healing

        maggoteers:wolf_whistle:
          material: BONE
          customName: "<gray>美味的骨头"
          lore:
            - "<gray>右键：召唤驯服狼"
            - "<dark_gray>消耗 1 个"
          maxStackSize: 8
          pdc:
            - key: maggoteers:id
              type: STRING
              value: maggoteers:wolf_whistle
          use_ability:
            cooldown_sec: 1
            consume: true
            effect: SUMMON
            params:
              entity: WOLF
              cleanup: wave_clear
              tamed: true
              friendly_fire: false

        """)
    body = "\n\n".join(render_item(k, v) for k, v in sorted(PLAYER_WEAPONS.items()))
    old = OUT_ITEMS.read_text(encoding="utf-8") if OUT_ITEMS.exists() else ""
    mob = ""
    if MOB_ITEMS_MARKER in old:
        mob = old[old.index(MOB_ITEMS_MARKER):]
    return header + body + ("\n\n" + mob if mob else "")


def build_collectibles():
    coll_yaml = "# rewardplan 护符映射\ncollectibles:\n"
    for rid, val in sorted(COLLECTIBLE_MAP.items()):
        if isinstance(val, dict):
            coll_yaml += f"  {rid}:\n"
            coll_yaml += ydump(val, 4) + "\n"
        else:
            coll_yaml += f"  {rid}:\n    item: {val}\n"
    items_yaml = "# rewardplan 护符外观（兔子脚/材料预览）\n\n"
    items_yaml += "\n\n".join(render_item(k, v) for k, v in sorted(COLLECTIBLE_ITEMS.items()))
    return coll_yaml, items_yaml


def map_class_collectibles():
    class_mats = {
        "class_vanguard": "RED_BANNER",
        "class_mage": "EMERALD_BLOCK",
        "class_heavy": "IRON_TRAPDOOR",
        "class_sniper": "SPYGLASS",
        "class_guard": "SMITHING_TABLE",
    }
    for rid, mat in class_mats.items():
        map_stat({"id": rid, "display": rid, "grants": [{}]}, mat)


def main():
    COLLECTIBLE_MAP.clear()
    COLLECTIBLE_ITEMS.clear()
    PLAYER_WEAPONS.clear()
    build_player_weapons()
    pools_data = {
        "class": class_pools(),
        "act1_weak": act1_weak(),
        "act1_strong": act1_strong(),
        "act1_boss": act1_boss(),
        "act2_weak": act2_weak(),
        "act2_strong": act2_strong(),
        "act2_boss": act2_boss(),
        "act3_weak": act3_weak(),
        "act3_strong": act3_strong(),
        "act3_boss": act3_boss(),
    }
    for opts in pools_data.values():
        pass  # collectibles mapped in pool builders
    rewards = build_rewards_yaml_from_pools(pools_data)
    OUT_REWARDS.write_text(rewards + "\n", encoding="utf-8", newline="\n")
    OUT_ITEMS.write_text(build_items_yaml(), encoding="utf-8", newline="\n")
    c_map, c_items = build_collectibles()
    OUT_COLL_MAP.write_text(c_map, encoding="utf-8", newline="\n")
    OUT_COLL_ITEMS.write_text(c_items + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {OUT_REWARDS}")
    print(f"wrote {OUT_ITEMS} ({len(PLAYER_WEAPONS)} weapons)")
    print(f"wrote {OUT_COLL_MAP} ({len(COLLECTIBLE_MAP)} mappings)")


if __name__ == "__main__":
    main()
