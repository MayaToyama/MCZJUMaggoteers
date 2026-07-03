package io.mczju.maggoteers.world;

import io.mczju.maggoteers.world.MapRepository.MapEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.util.Random;
import java.util.logging.Logger;

/**
 * 把某层 4 象限 NBT 粘贴进虚空世界（照搬前代 VampireSurvivor WorldManager.loadStructure 已验证方法）。
 * <p>4 象限在层原点基础上按固定偏移拼成完整地图：
 * <pre>
 *   NW(-32,0,-32)  NE(0,0,-32)
 *   SW(-32,0,  0)  SE(0,0,  0)
 * </pre>
 * 每 32×32 一象限；粘贴点 = 层原点 + 象限偏移。
 * <p>缺 NBT 且 fallback=true → 铺 64×64 玻璃平台 + 中央实心柱（v1 可玩兜底，防落虚空）。
 */
public final class StructurePaster {
    private static final Logger LOG = Logger.getLogger("Maggoteers");
    private static final int QUAD = 32;

    /**
     * @param world     目标世界
     * @param originX   层原点 X
     * @param baseY     层原点 Y（粘贴基准高度）
     * @param originZ   层原点 Z
     * @param nbtDir    maps/actN/<mapId>/ 目录（含 nw/sw/ne/se.nbt）
     * @param map       地图条目（hasNbt 判断哪几象限有 nbt）
     * @param fallback  true=未粘到任何 nbt 时铺玻璃平台
     */
    public static void pasteAct(World world, int originX, int baseY, int originZ,
                                File nbtDir, MapEntry map, boolean fallback) {
        boolean anyPasted = false;
        for (String q : new String[]{"nw", "sw", "ne", "se"}) {
            if (!map.hasNbt(q)) continue;
            File nbt = new File(nbtDir, q + ".nbt");
            if (!nbt.isFile()) continue;
            try {
                Structure s = Bukkit.getStructureManager().loadStructure(nbt);
                if (s == null) continue;
                BlockVector origin = new BlockVector(originX + offX(q), baseY, originZ + offZ(q));
                s.place(world, origin, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, new Random());
                anyPasted = true;
                LOG.info("已粘贴 " + q + ".nbt @ " + origin);
            } catch (Exception e) {
                LOG.warning("粘贴 " + q + ".nbt 失败：" + e.getMessage());
            }
        }
        if (!anyPasted && fallback) {
            buildPlatform(world, originX, baseY, originZ);
            LOG.warning("未粘贴任何 NBT，已铺设兜底玻璃平台 @ (" + originX + "," + baseY + "," + originZ + ")");
        }
    }

    /** 64×64 玻璃平台（顶面 = baseY）+ 中央下方实心柱（保证玩家/怪落在平台上）。 */
    private static void buildPlatform(World w, int ox, int baseY, int oz) {
        for (int dx = -QUAD; dx < QUAD; dx++) {
            for (int dz = -QUAD; dz < QUAD; dz++) {
                w.getBlockAt(ox + dx, baseY, oz + dz).setType(Material.GLASS);
            }
        }
        for (int dy = 1; dy <= 40; dy++) w.getBlockAt(ox, baseY - dy, oz).setType(Material.GLASS);
    }

    private static int offX(String q) { return q.endsWith("e") ? 0 : -QUAD; }
    private static int offZ(String q) { return q.startsWith("s") ? 0 : -QUAD; }

    private StructurePaster() {}
}
