package io.mczju.maggoteers.item.interact;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.item.CooldownService;
import io.mczju.maggoteers.util.ParticleEffects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 示例魔法武器：右键测试之刃 → 范围斩（粒子环 + 对周围生物伤害），3s CD（演示 weapon 地基）。 */
public final class TestBladeHandler implements ItemUseHandler {
    private static final String WEAPON_ID = "maggoteers:test_blade";
    private static final int COOLDOWN_SEC = 3;
    private static final double RADIUS = 4.0;
    private static final double DAMAGE = 6.0;

    @Override
    public void onUse(Player player, AbstractGame game, ItemStack stack) {
        if (!CooldownService.tryUse(player.getUniqueId(), WEAPON_ID, COOLDOWN_SEC)) {
            player.sendMessage(Component.text("旋风斩冷却中…", NamedTextColor.GRAY));
            return;
        }
        // 范围提示粒子
        ParticleEffects.playAreaRing(player.getWorld(), player.getLocation(), RADIUS);
        // 对周围非自身生物伤害
        for (Entity en : player.getWorld().getNearbyEntities(player.getLocation(), RADIUS, RADIUS, RADIUS)) {
            if (en instanceof LivingEntity le && en != player) {
                le.damage(DAMAGE, player);
            }
        }
        player.sendMessage(Component.text("⚔ 旋风斩！", NamedTextColor.YELLOW));
    }
}
