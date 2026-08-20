# -*- coding: utf-8 -*-
"""Scrape Arknights Integrated Strategy ISW-NO stage names from PRTS Wiki."""
import json
import re
import urllib.parse
import urllib.request

OUT = r"e:\Intellij_Idea\plugins\MCPlugin\agent-tools-is-stages.txt"

THEME_BY_PREFIX = {
    "ro": "刻俄柏的灰蕈迷境",
    "ro1": "傀影与猩红孤钻",
    "ro2": "水月与深蓝之树",
    "ro3": "探索者的银凇结境",
    "ro4": "萨卡兹的无终奇语",
    "ro5": "沉沦者的黑流树海",
}

THEME_ORDER = list(THEME_BY_PREFIX.values())

FLOOR_BUCKET = {
    1: "一层二层",
    2: "一层二层",
    3: "三层四层",
    4: "三层四层",
    5: "五层",
    6: "六层",
}


def api(**params) -> dict:
    params["format"] = "json"
    url = "https://prts.wiki/api.php?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=120) as resp:
        return json.load(resp)


def fetch_category_titles() -> list[str]:
    titles: list[str] = []
    cont: str | None = None
    while True:
        params: dict = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": "Category:集成战略关卡",
            "cmlimit": "500",
        }
        if cont:
            params["cmcontinue"] = cont
        data = api(**params)
        for m in data["query"]["categorymembers"]:
            t = m["title"]
            if t.startswith("ISW-NO "):
                titles.append(t)
        cont = data.get("continue", {}).get("cmcontinue")
        if not cont:
            break
    return titles


def fetch_wikitexts(titles: list[str]) -> dict[str, str]:
    out: dict[str, str] = {}
    for i in range(0, len(titles), 50):
        batch = titles[i : i + 50]
        data = api(
            action="query",
            prop="revisions",
            rvprop="content",
            rvslots="main",
            titles="|".join(batch),
        )
        for _pid, page in data["query"]["pages"].items():
            t = page["title"]
            slots = page["revisions"][0]["slots"]
            out[t] = slots["main"]["*"]
    return out


def parse_stage(title: str, wikitext: str) -> tuple[str, str, int] | None:
    """Return (theme, display_name, floor) for general combat ISW-NO."""
    m_name = re.search(r"\|关卡名=([^\n|]+)", wikitext)
    m_id = re.search(r"\|关卡id=([^\n|]+)", wikitext)
    if not m_name or not m_id:
        return None
    name = m_name.group(1).strip()
    sid = m_id.group(1).strip()
    # ro4_n_3_1 / ro_n_1_2 / ro1_n_5_4
    m = re.match(r"^(ro\d|ro)_n_(\d+)_", sid)
    if not m:
        return None
    prefix = m.group(1)
    theme = THEME_BY_PREFIX.get(prefix)
    if not theme:
        return None
    floor = int(m.group(2))
    return theme, name, floor


def floor_label(floor: int) -> str:
    if floor in (1, 2):
        return "一层二层"
    if floor in (3, 4):
        return "三层四层"
    if floor == 5:
        return "五层"
    return "六层"


def main():
    titles = fetch_category_titles()
    wts = fetch_wikitexts(titles)
    by_theme: dict[str, dict[str, set[str]]] = {
        t: {"一层二层": set(), "三层四层": set(), "五层": set(), "六层": set()}
        for t in THEME_ORDER
    }
    for title, wt in wts.items():
        parsed = parse_stage(title, wt)
        if not parsed:
            continue
        theme, name, floor = parsed
        by_theme[theme][floor_label(floor)].add(name)

    lines = [
        "明日方舟 · 集成战略 · 一般作战（ISW-NO）关卡名",
        "按「一层二层 / 三层四层 / 五层」分类（第 6 层单独列出）",
        "来源：PRTS Wiki 分类「集成战略关卡」，由关卡 id（ro*_n_层号_*）解析层数",
        "https://prts.wiki",
        "抓取：2026-07-25",
        "",
        "不含：险路恶敌（ISW-DF）、鸭爵（ISW-DU/SP）、狭路相逢等特殊节点。",
        "",
    ]
    for theme in THEME_ORDER:
        lines.append("\n" + "═" * 70)
        lines.append(theme)
        lines.append("═" * 70)
        for bucket in ("一层二层", "三层四层", "五层"):
            names = sorted(by_theme[theme][bucket])
            lines.append(f"\n【{bucket}】{len(names)} 个")
            if names:
                lines.append("、".join(names))
        six = sorted(by_theme[theme]["六层"])
        if six:
            lines.append(f"\n【六层】{len(six)} 个（参考）")
            lines.append("、".join(six))

    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("OK", OUT, "ISW-NO pages", len(titles))


def scrape_bosses():
    """Layer-3 and ending bosses (ro*_b_* ids)."""
    boss_theme = {
        **THEME_BY_PREFIX,
        "ro6": "沉沦者的黑流树海",
    }
    layer3_max = {"ro": 3, "ro1": 4, "ro2": 3, "ro3": 3, "ro4": 3, "ro5": 3, "ro6": 3}
    ending_min = {"ro": 5, "ro1": 5, "ro2": 5, "ro3": 5, "ro4": 4, "ro5": 4, "ro6": 4}
    out_path = r"e:\Intellij_Idea\plugins\MCPlugin\agent-tools-is-bosses.txt"

    titles: list[str] = []
    cont: str | None = None
    while True:
        params: dict = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": "Category:集成战略关卡",
            "cmlimit": "500",
        }
        if cont:
            params["cmcontinue"] = cont
        data = api(**params)
        for m in data["query"]["categorymembers"]:
            t = m["title"]
            if t.startswith(("ISW-NO ", "ISW-DF ")):
                titles.append(t)
        cont = data.get("continue", {}).get("cmcontinue")
        if not cont:
            break

    wts = fetch_wikitexts(titles)
    layer3: dict[str, set[str]] = {}
    endings: dict[str, set[str]] = {}
    for wt in wts.values():
        m_name = re.search(r"\|关卡名=([^\n|]+)", wt)
        m_id = re.search(r"\|关卡id=([^\n|]+)", wt)
        if not m_name or not m_id:
            continue
        name = m_name.group(1).strip()
        sid = m_id.group(1).strip()
        m = re.match(r"^(ro\d|ro)_([bt])_(\d+)", sid)
        if not m:
            continue
        prefix, kind, num_s = m.group(1), m.group(2), m.group(3)
        theme = boss_theme.get(prefix)
        if not theme:
            continue
        num = int(num_s)
        if kind == "b" and num <= layer3_max.get(prefix, 3):
            layer3.setdefault(theme, set()).add(name)
        if (kind == "b" and num >= ending_min.get(prefix, 5)) or (kind == "t" and num >= 7):
            endings.setdefault(theme, set()).add(name)

    order: list[str] = []
    seen: set[str] = set()
    for v in boss_theme.values():
        if v not in seen:
            order.append(v)
            seen.add(v)

    lines = [
        "明日方舟 · 集成战略 · 三层 Boss & 结局 Boss",
        "来源：PRTS Wiki（关卡 id：ro*_b_* / ro*_t_7+）",
        "https://prts.wiki",
        "",
        "三层 Boss：ro*_b_1~3（傀影为 b_1~4），含异格版",
        "结局 Boss：各主题首个结局序号起的 b 型关 + t_7 隐藏结局",
        "不含四层/五层过渡 Boss（如 认知即重担、巍峨银凇、荒地群猎 等）",
        "",
    ]
    for theme in order:
        lines.extend(["", "=" * 68, theme, "=" * 68])
        l3 = sorted(layer3.get(theme, ()))
        end = sorted(endings.get(theme, ()))
        lines.append(f"\n【三层 Boss】{len(l3)} 个")
        lines.extend(f"  · {n}" for n in l3)
        lines.append(f"\n【结局 Boss】{len(end)} 个")
        lines.extend(f"  · {n}" for n in end)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("OK", out_path)


if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "bosses":
        scrape_bosses()
    else:
        main()
