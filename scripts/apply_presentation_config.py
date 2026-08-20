"""Apply rewardplan presentation: RABBIT_FOOT + itemModel + lore for charms; lore for weapons."""
from __future__ import annotations

import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
REWARDPLAN = ROOT / "docs/superpowers/plans/rewardplan"
REWARDS = ROOT / "src/main/resources/rewards.yml"
COLL_MAP = ROOT / "src/main/resources/collectibles.yml"
COLL_ITEMS = ROOT / "src/main/resources/items/collectibles.yml"
WEAPON_ITEMS = ROOT / "src/main/resources/items/maggoteers.yml"

BUNDLE_SUB = {
    "class_vanguard_token", "class_vanguard_token_hp",
    "class_mage_token_atk", "class_mage_token_mag", "class_mage_token_hp",
    "class_heavy_token_atk", "class_heavy_token_hp",
    "class_sniper_token_mag", "class_sniper_token_spd", "class_sniper_token_hp",
    "class_guard_token_reach", "class_guard_token_atk",
    "a1w_parting_tnt", "a1w_parting_slow",
    "a1s_act_atk20", "a1s_act_mag20",
    "a1b_wave_coin2", "a1b_hit_penalty",
    "a2s_arrogance_atk", "a2s_arrogance_mag",
    "a2s_arrogance_clear", "a2s_arrogance_clear2",
    "a2s_martyr_heal", "a2s_martyr_buff",
    "a3s_lust_abs", "a3s_lust_atk", "a3s_lust_mag",
}

CN: dict[str, str] = {
    "严重损坏的铁砧": "CHIPPED_ANVIL", "开裂的铁砧": "DAMAGED_ANVIL", "崭新的铁砧": "ANVIL",
    "铜剑刃碎片": "COPPER_INGOT", "铁剑刃碎片": "IRON_SWORD", "合金剑刃碎片": "NETHERITE_SWORD",
    "受潮的力量粉末": "MAGMA_CREAM", "灼热的力量粉末": "BLAZE_POWDER", "力量之源": "BLAZE_ROD",
    "浑浊的魔晶": "LAPIS_LAZULI", "纯净的魔晶": "QUARTZ", "原初魔晶": "AMETHYST_CLUSTER",
    "磨损的杖芯": "DEAD_BUSH", "冬青杖芯": "SPRUCE_SAPLING", "接骨木杖芯": "PALE_OAK_SAPLING",
    "平凡的药剂": "SPLASH_POTION", "魔力药剂": "POTION", "魔力之源": "EXPERIENCE_BOTTLE",
    "热水壶": "POTION", "干硬的面包": "BREAD", "皮革甲片": "LEATHER",
    "炒鸡肉": "COOKED_CHICKEN", "微风精华": "BREEZE_ROD", "精致跑鞋": "LEATHER_BOOTS",
    "主观缓时": "CLOCK", "风神的印记": "WIND_CHARGE", "火箭靴": "IRON_BOOTS",
    "兔子脚": "RABBIT_FOOT", "解渴的西瓜汁": "POTION", "防弹插板": "HEAVY_WEIGHTED_PRESSURE_PLATE",
    "香香的奶耐": "POTION", "永恒牛排": "COOKED_BEEF", "秘银软甲": "CHAINMAIL_CHESTPLATE",
    "一大桶奶耐": "MILK_BUCKET", "捕风网": "COBWEB",
    "烈焰人突变诱发物": "BLAZE_SPAWN_EGG", "猪灵蛮兵突变诱发物": "PIGLIN_BRUTE_SPAWN_EGG",
    "悦灵突变诱发物": "ALLAY_SPAWN_EGG", "铁傀儡突变诱发物": "IRON_GOLEM_SPAWN_EGG",
    "凋零骷髅突变诱发物": "WITHER_SKELETON_SPAWN_EGG", "洞穴蜘蛛突变诱发物": "CAVE_SPIDER_SPAWN_EGG",
    "监守者突变诱发物": "WARDEN_SPAWN_EGG", "监守者突变诱发无": "WARDEN_SPAWN_EGG",
    "黑暗宝珠": "EMERALD", "微型怨灵": "GHAST_TEAR", "青铜羽饰": "COPPER_HELMET",
    "照明弹": "FIREWORK_STAR", "狂战斧": "GOLDEN_AXE", "《德鲁伊入门》": "BOOK",
    "临别赠礼": "TRAPPED_CHEST", "增生藤甲": "VINE", "稳定的原石祭坛": "ENCHANTING_TABLE",
    "清算名单": "PAPER", "灵魂立方": "SOUL_SAND", "温暖的篝火": "CAMPFIRE",
    "失落之钥": "FILLED_MAP", "懒惰护符": "SUNFLOWER", "暴怒护符": "POPPY",
    "捕鳞蓑": "LEATHER_HELMET", "老近卫军之锋": "STONE_AXE", "皇帝的收藏": "ENDER_CHEST",
    "破旧的巨型甲胄": "IRON_CHESTPLATE", "黑夜呢喃": "MUSIC_DISC_11",
    "东京湾专属配重块": "IRON_BLOCK", "合金配重块": "NETHERITE_BLOCK",
    "赫拉克勒斯的果树": "OAK_SAPLING", "贪婪护符": "PEONY", "免费牛排！": "COOKED_BEEF",
    "暴食护符": "PITCHER_PLANT", "霜晶树": "DARK_OAK_SAPLING", "死魂灵之影": "GUNPOWDER",
    "嗜血之颅": "PIGLIN_HEAD", "狂暴之颅": "SKELETON_SKULL", "肉食之颅": "ZOMBIE_HEAD",
    "裂变之颅": "CREEPER_HEAD", "凋萎之颅": "WITHER_SKELETON_SKULL", "便携魔像": "CARVED_PUMPKIN",
    "腐败之油": "COAL_BLOCK", "诅咒之刃": "STONE_SWORD", "野战医疗箱": "RED_SHULKER_BOX",
    "便携生物手雷制造站": "DISPENSER", "傲慢护符": "TORCHFLOWER", "桃木剑": "WOODEN_SWORD",
    "岩角号": "GOAT_HORN", "刺猬胸甲": "DIAMOND_CHESTPLATE", "殉道者": "RECOVERY_COMPASS",
    "迷迭香之拥": "IRON_DOOR", "璀璨悲泣": "DIAMOND_BLOCK", "蒸汽骑士的甲胄": "NETHERITE_CHESTPLATE",
    "“大静谧”": "PAINTING", "活塞式机械臂": "PISTON", "人偶牵线": "STRING",
    "隐身衣": "ELYTRA", "嫉妒护符": "TORCHFLOWER", "恐鱼生": "SALMON",
    "安眠迷香": "GLOWSTONE_DUST", "天穹尘埃": "FEATHER", "寒霜领域": "POWDER_SNOW_BUCKET",
    "咒魂护符": "WAXED_OXIDIZED_COPPER", "嗜血之斧": "GOLDEN_AXE", "嗜血宝石": "REDSTONE_BLOCK",
    "白玫瑰": "ROSE_BUSH", "护心镜": "WAXED_OXIDIZED_COPPER_TRAPDOOR",
    "色欲护符": "LILY_OF_THE_VALLEY", "红色旗帜": "RED_BANNER", "绿宝石块": "EMERALD_BLOCK",
    "铁活版门": "IRON_TRAPDOOR", "望远镜": "SPYGLASS", "锻造台": "SMITHING_TABLE",
    "流浪商人的长鞭": "LEAD", "挠痒痒痒痒挠": "GOLDEN_HOE",
    "水瓶": "POTION", "平凡的药水": "SPLASH_POTION", "紫色的药水": "POTION",
    "白色药水": "POTION", "治疗药水": "POTION", "骨头": "BONE", "美味的骨头": "BONE",
    "金苹果": "GOLDEN_APPLE", "TNT": "TNT", "熟牛排": "COOKED_BEEF",
    "11号唱片": "MUSIC_DISC_11", "画": "PAINTING", "细雪桶": "POWDER_SNOW_BUCKET",
    "涂蜡的氧化铜块": "WAXED_OXIDIZED_COPPER", "涂蜡的铜活版门": "WAXED_OXIDIZED_COPPER_TRAPDOOR",
    "铃兰": "LILY_OF_THE_VALLEY", "火把花": "TORCHFLOWER", "回溯指针": "RECOVERY_COMPASS",
    "铁门": "IRON_DOOR", "钻石块": "DIAMOND_BLOCK", "下界合金胸甲": "NETHERITE_CHESTPLATE",
    "活塞": "PISTON", "线": "STRING", "鞘翅": "ELYTRA", "生鲑鱼": "SALMON",
    "萤石粉": "GLOWSTONE_DUST", "羽毛": "FEATHER", "玫瑰丛": "ROSE_BUSH",
    "红石块": "REDSTONE_BLOCK", "金斧": "GOLDEN_AXE", "木剑": "WOODEN_SWORD",
    "山羊号角": "GOAT_HORN", "钻石胸甲": "DIAMOND_CHESTPLATE", "发射器": "DISPENSER",
    "红色潜影箱": "RED_SHULKER_BOX", "煤炭块": "COAL_BLOCK", "石剑": "STONE_SWORD",
    "雕刻南瓜": "CARVED_PUMPKIN", "火药": "GUNPOWDER", "牡丹": "PEONY",
    "瓶子草": "PITCHER_PLANT", "深色橡树树苗": "DARK_OAK_SAPLING", "橡树树苗": "OAK_SAPLING",
    "铜头盔": "COPPER_HELMET", "烟火之心": "FIREWORK_STAR", "书": "BOOK",
    "陷阱箱": "TRAPPED_CHEST", "藤蔓": "VINE", "附魔台": "ENCHANTING_TABLE",
    "纸": "PAPER", "灵魂沙": "SOUL_SAND", "篝火": "CAMPFIRE", "藏宝图": "FILLED_MAP",
    "向日葵": "SUNFLOWER", "虞美人": "POPPY", "黑色皮革头盔": "LEATHER_HELMET",
    "石斧": "STONE_AXE", "末影箱": "ENDER_CHEST", "铁胸甲": "IRON_CHESTPLATE",
    "铁块": "IRON_BLOCK", "下界合金块": "NETHERITE_BLOCK",
    "猪灵头": "PIGLIN_HEAD", "骷髅头": "SKELETON_SKULL", "僵尸头": "ZOMBIE_HEAD",
    "苦力怕头": "CREEPER_HEAD", "凋零骷髅头": "WITHER_SKELETON_SKULL",
    "铜剑": "COPPER_INGOT", "铁剑": "IRON_SWORD", "下界合金剑": "NETHERITE_SWORD",
    "岩浆膏": "MAGMA_CREAM", "烈焰粉": "BLAZE_POWDER", "烈焰棒": "BLAZE_ROD",
    "青金石": "LAPIS_LAZULI", "石英": "QUARTZ", "紫水晶簇": "AMETHYST_CLUSTER",
    "枯萎的灌木": "DEAD_BUSH", "云杉树苗": "SPRUCE_SAPLING", "苍白树苗": "PALE_OAK_SAPLING",
    "面包": "BREAD", "皮革": "LEATHER", "烤鸡肉": "COOKED_CHICKEN", "旋风棒": "BREEZE_ROD",
    "皮革靴子": "LEATHER_BOOTS", "钟": "CLOCK", "风弹": "WIND_CHARGE", "铁靴子": "IRON_BOOTS",
    "铁质压力板": "HEAVY_WEIGHTED_PRESSURE_PLATE", "锁链胸甲": "CHAINMAIL_CHESTPLATE",
    "牛奶桶": "MILK_BUCKET", "栓绳": "LEAD", "金锄": "GOLDEN_HOE",
    "烈焰人刷怪蛋": "BLAZE_SPAWN_EGG", "猪灵蛮兵刷怪蛋": "PIGLIN_BRUTE_SPAWN_EGG",
    "悦灵刷怪蛋": "ALLAY_SPAWN_EGG", "铁傀儡刷怪蛋": "IRON_GOLEM_SPAWN_EGG",
    "凋零骷髅刷怪蛋": "WITHER_SKELETON_SPAWN_EGG", "洞穴蜘蛛刷怪蛋": "CAVE_SPIDER_SPAWN_EGG",
    "监守者刷怪蛋": "WARDEN_SPAWN_EGG", "绿宝石": "EMERALD",
    "铁斧": "IRON_AXE", "金剑": "GOLDEN_SWORD", "铁剑": "IRON_SWORD",
    "金矛": "GOLDEN_SWORD", "铁矛": "IRON_SWORD", "三叉戟": "TRIDENT",
    "弩": "CROSSBOW", "重锤": "MACE", "盾牌": "SHIELD", "燧石": "FLINT",
    "末影之眼": "ENDER_EYE", "铁锭": "IRON_INGOT", "木棍": "STICK",
    "紫水晶碎片": "AMETHYST_SHARD", "钻石剑": "DIAMOND_SWORD", "钻石锄": "DIAMOND_HOE",
    "成书": "WRITTEN_BOOK", "潮涌核心": "HEART_OF_THE_SEA", "铜矛": "COPPER_INGOT",
    "避雷针": "LIGHTNING_ROD", "黑曜石": "OBSIDIAN", "蓝冰": "BLUE_ICE",
    "铜锄": "COPPER_INGOT", "铜傀儡像": "COPPER_GOLEM_STATUE",
}

EXTRA_MODEL = {
    "class_vanguard": "RED_BANNER",
    "class_mage": "EMERALD_BLOCK",
    "class_heavy": "IRON_TRAPDOOR",
    "class_sniper": "SPYGLASS",
    "class_guard": "SMITHING_TABLE",
}

SECTION_POOL = {
    "开局职业": "class",
    "1层弱": "act1_weak",
    "1层强": "act1_strong",
    "1层boss": "act1_boss",
    "2层弱": "act2_weak",
    "2层强": "act2_strong",
    "2层boss": "act2_boss",
    "3层弱": "act3_weak",
    "3层强": "act3_strong",
    "3层boss": "act3_boss",
}

MANUAL_MODEL = {
    "a2w_spd8b": "RABBIT_FOOT",
    "a1s_lost_key": "FILLED_MAP",
    "a1s_taken_str": "POPPY",
    "a1s_ally_invis": "LEATHER_HELMET",
    "a1w_parting_gift": "TRAPPED_CHEST",
    "a1w_act_revive": "VINE",
    "a1b_frost_tree": "DARK_OAK_SAPLING",
    "a1b_wave_tnt": "GUNPOWDER",
    "a2s_arrogance": "TORCHFLOWER",
    "a2s_martyr": "RECOVERY_COMPASS",
    "a3s_lust": "LILY_OF_THE_VALLEY",
}

MANUAL_LORE = {
    "a3w_glass_regen": ["<gray>每波清空获得一把玻璃刀"],
    "class_vanguard": ["<gray>战斧 + 信物"],
    "class_mage": ["<gray>魔剑 + 信物"],
    "class_heavy": ["<gray>圣剑 + 信物"],
    "class_sniper": ["<gray>弩 + 信物"],
    "class_guard": ["<gray>长矛 + 信物"],
    "a1s_lost_key": ["<gray>进入新的一层时，获得20攻击力和魔攻加成。"],
    "a1s_taken_str": ["<gray>受击时获得2秒力量III"],
    "a2s_arrogance": [
        "<gray>回合结束时，你获得50攻击力和魔攻加成",
        "<gray>当你受到伤害时，清空该选项带来的加成",
    ],
}

CLASS_WEAPON_LORE = {
    "maggoteers:class_vanguard_axe": [
        "<gray>10伤害",
        "<gray>右键强化自身，直到下次攻击前获得100攻击力",
        "<gray>冷却20s",
    ],
    "maggoteers:class_mage_sword": [
        "<gray>5伤害",
        "<gray>右键对半径4格内敌人造成4点范围伤害",
        "<gray>冷却12s",
    ],
    "maggoteers:class_heavy_sword": [
        "<gray>6伤害",
        "<gray>右键治疗范围内玩家10点生命值",
        "<gray>冷却20s",
    ],
    "maggoteers:class_sniper_bow": [
        "<gray>右键释放伤害光束，造成6点伤害",
        "<gray>冷却3s",
    ],
    "maggoteers:class_guard_spear": [
        "<gray>10伤害",
        "<gray>1攻击速度",
        "<gray>+0.5触及范围",
    ],
}

FUNC_HINT = re.compile(
    r"\d|右键|冷却|伤害|攻击|手持|消耗|召唤|半径|直到|获得|免疫|可叠加|上限|不可叠加|扣除|发射|治疗|净化|眩晕|沉默"
)


def strip_mm(text: str) -> str:
    return re.sub(r"<[^>]+>", "", text or "").strip()


def cn_mat(name: str) -> str | None:
    name = name.strip()
    if name in CN:
        return CN[name]
    return None


def parse_lore_line(line: str, *, weapon: bool) -> list[str]:
    line = line.strip()
    if not line.startswith("·"):
        return []
    body = line[1:].strip()
    m = re.match(r"^(.+?)（(.+)）$", body)
    if not m:
        return [f"<gray>{body}"]
    title, inner = m.group(1).strip(), m.group(2).strip()
    parts = [p.strip() for p in inner.split("，") if p.strip()]
    if weapon:
        func = parts[1:] if len(parts) > 1 else parts
    else:
        func = [p for p in parts if FUNC_HINT.search(p)]
        if not func:
            func = [title]
    return [f"<gray>{p}" for p in func if p]


def parse_rewardplan_entries() -> tuple[dict[str, list[str]], dict[str, list[str]], dict[str, list[str]]]:
    """Return display->lore for collectibles, display->lore for weapons, section->model list."""
    coll_lore: dict[str, list[str]] = {}
    weapon_lore: dict[str, list[str]] = {}
    section_models: dict[str, list[str]] = {}
    section: str | None = None
    mode: str | None = None

    in_class = False
    for raw in REWARDPLAN.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        sec_m = re.match(r"^(开局职业|\d层(?:弱|强|boss))", line.strip())
        if sec_m:
            section = sec_m.group(1)
            in_class = section == "开局职业"
            section_models.setdefault(section, [])
            mode = None
            continue
        if "·武器" in line:
            mode = "weapon"
            continue
        if "·属性" in line or "·效果" in line or "·被动" in line:
            mode = "collectible"
            continue
        if not line.strip().startswith("·"):
            continue
        if mode is None and not in_class:
            continue

        title_m = re.match(r"^\s*·(.+?)（", line)
        title = title_m.group(1).strip().rstrip("。") if title_m else line.strip()[1:].strip()
        is_weapon = mode == "weapon" or (in_class and "伤害" in line)
        lore = parse_lore_line(line, weapon=is_weapon)
        if is_weapon:
            weapon_lore.setdefault(title, lore)
        else:
            coll_lore.setdefault(title, lore)
            m_name = re.match(r"^\s*·.+?（([^，）]+)，([^）]+)）", line)
            if m_name:
                name = m_name.group(1).strip()
                if FUNC_HINT.search(title):
                    coll_lore[name] = [f"<gray>{title}"]
                else:
                    coll_lore.setdefault(name, lore)
            mat_m = re.match(r"^\s*·.+?（([^，）]+)(?:，([^）]+))?）", line)
            if mat_m and section:
                mat = cn_mat(mat_m.group(2) or mat_m.group(1))
                if mat:
                    section_models[section].append(mat)
            elif re.match(r"^\s*·.+?（([^）]+)）", line) and section:
                inner = re.match(r"^\s*·.+?（([^）]+)）", line).group(1)
                mat = cn_mat(inner.split("，")[-1].strip())
                if mat:
                    section_models[section].append(mat)

    return coll_lore, weapon_lore, section_models


def collectible_options(pool_opts: list[dict]) -> list[dict]:
    out: list[dict] = []
    for opt in pool_opts:
        if opt.get("grants"):
            out.append(opt)
        elif opt.get("category") == "STAT":
            out.append(opt)
    return out


def build_id_model(section_models: dict[str, list[str]], rewards_data: dict) -> dict[str, str]:
    id_model: dict[str, str] = dict(EXTRA_MODEL)
    id_model.update(MANUAL_MODEL)
    for sec, pool_id in SECTION_POOL.items():
        mats = [m for m in section_models.get(sec, []) if m]
        opts = collectible_options(rewards_data["reward_pools"][pool_id].get("options", []))
        for opt, mat in zip(opts, mats):
            rid = opt["id"]
            if rid in BUNDLE_SUB or rid in MANUAL_MODEL or rid in EXTRA_MODEL:
                continue
            id_model[rid] = mat
    # Match collectible display name inside rewardplan parens: （名称，材质）
    for raw in REWARDPLAN.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\s*·.+?（([^，）]+)，([^）]+)）", raw)
        if not m:
            continue
        coll_name, mat_name = m.group(1).strip(), m.group(2).strip()
        mat = cn_mat(mat_name) or cn_mat(coll_name)
        if not mat or cn_mat(coll_name):
            continue
        for pool in rewards_data["reward_pools"].values():
            for opt in pool.get("options", []):
                for o in [opt] + (opt.get("grants") or []):
                    rid = o.get("id")
                    if not rid or rid in id_model or rid in BUNDLE_SUB:
                        continue
                    disp = strip_mm(str(o.get("display", "")))
                    if disp == coll_name:
                        id_model[rid] = mat
    id_model.update(MANUAL_MODEL)
    id_model.update(EXTRA_MODEL)
    return id_model


def build_id_lore(coll_lore_by_name: dict[str, list[str]], rewards_data: dict) -> dict[str, list[str]]:
    id_lore: dict[str, list[str]] = dict(MANUAL_LORE)
    for pool in rewards_data["reward_pools"].values():
        for opt in pool.get("options", []):
            for o in [opt] + (opt.get("grants") or []):
                rid = o.get("id")
                if not rid or rid in BUNDLE_SUB:
                    continue
                disp = strip_mm(str(o.get("display", "")))
                desc = o.get("description")
                if rid in id_lore:
                    continue
                if disp in coll_lore_by_name:
                    id_lore[rid] = coll_lore_by_name[disp]
                elif desc:
                    id_lore[rid] = [f"<gray>{desc}"]
                elif disp:
                    id_lore[rid] = [f"<gray>{disp}"]
    return id_lore


def build_weapon_lore(weapon_lore_by_name: dict[str, list[str]], rewards_data: dict) -> dict[str, list[str]]:
    item_lore: dict[str, list[str]] = {}
    for pool in rewards_data["reward_pools"].values():
        def walk(opts: list[dict]) -> None:
            for opt in opts:
                if opt.get("category") == "WEAPON":
                    item = opt.get("item")
                    disp = strip_mm(str(opt.get("display", "")))
                    if item and disp in weapon_lore_by_name:
                        item_lore[item] = weapon_lore_by_name[disp]
                    elif item and opt.get("description"):
                        item_lore[item] = [f"<gray>{opt['description']}"]
                if opt.get("grants"):
                    walk(opt["grants"])
        walk(pool.get("options", []))
    item_lore.update(CLASS_WEAPON_LORE)
    return item_lore


def render_collectible_item(item_id: str, entry: dict) -> str:
    lines = [f"{item_id}:"]
    lines.append("  material: RABBIT_FOOT")
    lines.append(f"  itemModel: {entry['itemModel']}")
    if entry.get("customName"):
        lines.append(f"  customName: \"{entry['customName']}\"")
    if entry.get("glint") is not None:
        lines.append(f"  glint: {'true' if entry['glint'] else 'false'}")
    lore = entry.get("lore") or []
    if lore:
        lines.append("  lore:")
        for row in lore:
            lines.append(f"    - \"{row}\"")
    return "\n".join(lines)


def rewrite_collectibles(items: dict, id_model: dict[str, str], id_lore: dict[str, list[str]]) -> None:
    reward_for_item: dict[str, str] = {}
    coll_map = yaml.safe_load(COLL_MAP.read_text(encoding="utf-8"))["collectibles"]
    for rid, spec in coll_map.items():
        if rid in BUNDLE_SUB:
            continue
        item_key = spec["item"]
        reward_for_item[item_key] = rid

    out_blocks: list[str] = ["# rewardplan 护符：RABBIT_FOOT 底材 + itemModel 外观 + 功能 lore", ""]
    for item_id in sorted(items.keys(), key=lambda k: reward_for_item.get(k, k)):
        if not item_id.startswith("maggoteers:charm_"):
            continue
        rid = reward_for_item.get(item_id)
        if not rid:
            rid = item_id.removeprefix("maggoteers:charm_")
        if rid in BUNDLE_SUB:
            continue
        old = items[item_id]
        model = id_model.get(rid)
        if not model:
            model = old.get("itemModel") or old.get("material", "RABBIT_FOOT")
        lore = id_lore.get(rid) or old.get("lore") or [f"<gray>{strip_mm(str(old.get('customName', rid)))}"]
        entry = {
            "itemModel": model,
            "customName": old.get("customName"),
            "glint": old.get("glint", True),
            "lore": lore,
        }
        out_blocks.append(render_collectible_item(item_id, entry))
        out_blocks.append("")
    COLL_ITEMS.write_text("\n".join(out_blocks).rstrip() + "\n", encoding="utf-8")


def patch_weapon_lore(item_lore: dict[str, list[str]]) -> None:
    lines = WEAPON_ITEMS.read_text(encoding="utf-8").splitlines(keepends=True)
    out: list[str] = []
    cur_item: str | None = None
    skip_lore = False
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^(maggoteers:[\w]+):\s*$", line.strip())
        if m:
            cur_item = m.group(1)
            skip_lore = False
            out.append(line)
            i += 1
            continue
        if cur_item and line.strip() == "lore:":
            skip_lore = True
            i += 1
            while i < len(lines) and (lines[i].startswith("    ") or lines[i].strip() == ""):
                i += 1
            continue
        if cur_item and skip_lore:
            skip_lore = False
        if cur_item and line.startswith("  ") and not line.startswith("    ") and line.strip().endswith(":"):
            if cur_item in item_lore and "lore:" not in "".join(out[-8:]):
                out.extend(format_weapon_lore(item_lore[cur_item]))
            skip_lore = False
        out.append(line)
        if cur_item and line.strip() and not line.startswith(" ") and not line.startswith("#"):
            cur_item = None
        i += 1

    text = "".join(out)
    for item_id, lore in item_lore.items():
        if f"{item_id}:" not in text:
            continue
        if re.search(rf"^{re.escape(item_id)}:\n(?:.*\n)*?  lore:", text, re.MULTILINE):
            continue
        block = format_weapon_lore(lore)
        text = re.sub(
            rf"^({re.escape(item_id)}:\n(?:  .+\n)*?  customName: .+\n)",
            r"\1" + "".join(block),
            text,
            count=1,
            flags=re.MULTILINE,
        )
    WEAPON_ITEMS.write_text(text, encoding="utf-8")


def format_weapon_lore(lore: list[str]) -> list[str]:
    rows = ["  lore:\n"]
    for row in lore:
        rows.append(f"    - \"{row}\"\n")
    return rows


def main() -> None:
    rewards_data = yaml.safe_load(REWARDS.read_text(encoding="utf-8"))
    coll_by_name, weapon_by_name, section_models = parse_rewardplan_entries()
    id_model = build_id_model(section_models, rewards_data)
    id_lore = build_id_lore(coll_by_name, rewards_data)
    weapon_item_lore = build_weapon_lore(weapon_by_name, rewards_data)

    items = yaml.safe_load(COLL_ITEMS.read_text(encoding="utf-8"))
    rewrite_collectibles(items, id_model, id_lore)
    patch_weapon_lore(weapon_item_lore)

    missing_model = [rid for rid in yaml.safe_load(COLL_MAP.read_text(encoding="utf-8"))["collectibles"] if rid not in BUNDLE_SUB and rid not in id_model]
    missing_lore = [rid for rid in yaml.safe_load(COLL_MAP.read_text(encoding="utf-8"))["collectibles"] if rid not in BUNDLE_SUB and rid not in id_lore]
    print(f"id_model={len(id_model)} id_lore={len(id_lore)} weapon_lore={len(weapon_item_lore)}")
    if missing_model:
        print("missing model:", ", ".join(sorted(missing_model)[:20]), ("..." if len(missing_model) > 20 else ""))
    if missing_lore:
        print("missing lore:", ", ".join(sorted(missing_lore)[:20]), ("..." if len(missing_lore) > 20 else ""))


if __name__ == "__main__":
    main()
