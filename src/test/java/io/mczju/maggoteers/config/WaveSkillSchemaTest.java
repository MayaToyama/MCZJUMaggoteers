package io.mczju.maggoteers.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 1.1 怪物技能系统迁移后的 waves.yml schema 校验。 */
class WaveSkillSchemaTest {

    private static final Pattern FLOW_ID = Pattern.compile("skills\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern BLOCK_ID = Pattern.compile("^[ \\t]*-[ \\t]*([A-Za-z0-9_]+)[ \\t]*$", Pattern.MULTILINE);

    @Test
    void wavesYamlHasNoLegacyAffixOrInfernalKeys() throws Exception {
        String text = resourceText("waves.yml");
        // 只拦 key 形态（affixes: / infernal:）——头部注释里"旧 affixes/infernal"字样不算回潮
        assertFalse(text.contains("affixes:"), "legacy affixes key still present in waves.yml");
        assertFalse(text.contains("infernal:"), "legacy infernal key still present in waves.yml");
    }

    @Test
    void everyWavesSkillIdIsDefinedInMobSkillsYaml() throws Exception {
        String wavesText = resourceText("waves.yml");
        String skillsText = resourceText("mob_skills.yml");
        assertTrue(skillsText.contains("skills:"), "mob_skills.yml missing top-level skills:");

        // 收集 waves.yml 全部 skills 引用 id（flow + block 两种写法）
        Set<String> used = collectSkillIds(wavesText);
        // 收集 mob_skills.yml 顶层 skills 定义 id（缩进键，非 - id 行）
        Set<String> defined = collectDefinedSkillIds(skillsText);

        List<String> missing = used.stream().filter(id -> !defined.contains(id)).distinct().toList();
        assertTrue(missing.isEmpty(), () -> "waves.yml references undefined skills: " + missing);
    }

    /** waves.yml 引用侧：收集 flow（skills: [a, b]）与 block（- a）两种写法的 id。 */
    private static Set<String> collectSkillIds(String yamlText) {
        List<String> ids = new ArrayList<>();
        Matcher flow = FLOW_ID.matcher(yamlText);
        while (flow.find()) {
            for (String part : flow.group(1).split(",")) {
                String s = part.trim();
                if (!s.isEmpty()) ids.add(s);
            }
        }
        // block 写法：仅统计位于 skills: 下缩进的 - id 行
        boolean inSkillsBlock = false;
        for (String line : yamlText.split("\n")) {
            String t = line.trim();
            if (t.startsWith("skills:")) {
                inSkillsBlock = true;
                continue;
            }
            if (inSkillsBlock) {
                Matcher m = BLOCK_ID.matcher(t);
                if (m.matches()) {
                    ids.add(m.group(1));
                } else if (!t.startsWith("-")) {
                    inSkillsBlock = false;   // 离开 skills 块
                }
            }
        }
        return Set.copyOf(ids);
    }

    /** mob_skills.yml 定义侧：解析顶层 skills: 下第一层缩进键名（定义是 id: 键，不是 - id）。 */
    private static Set<String> collectDefinedSkillIds(String yamlText) {
        List<String> ids = new ArrayList<>();
        boolean inTop = false;
        for (String line : yamlText.split("\n")) {
            String t = line.trim();
            if (t.startsWith("skills:")) { inTop = true; continue; }
            if (!inTop) continue;
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("-")) continue;
            if (!line.startsWith(" ") && t.contains(":")) { break; }   // 离开 skills 顶层块
            String id = t.substring(0, t.indexOf(':')).trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static String resourceText(String name) throws Exception {
        InputStream in = WaveSkillSchemaTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(in, name);
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
