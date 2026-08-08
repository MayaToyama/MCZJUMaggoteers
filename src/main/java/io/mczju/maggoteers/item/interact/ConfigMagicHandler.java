package io.mczju.maggoteers.item.interact;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.effect.ItemAbility;
import io.mczju.maggoteers.effect.ItemAbilityRegistry;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.CooldownService;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.item.fx.MagicFxService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Config-driven magic weapon right-click ({@code use_ability} on items YAML). */
public final class ConfigMagicHandler implements ItemUseHandler {

    @Override
    public void onUse(Player player, AbstractGame game, ItemStack stack) {
        String itemId = ItemService.itemIdOf(stack);
        if (itemId == null) {
            return;
        }
        ItemAbility ability = ItemAbilityRegistry.get(itemId).orElse(null);
        if (ability == null) {
            return;
        }
        if (!CooldownService.tryUse(player, stack, itemId, ability.cooldownSec())) {
            player.sendMessage(Component.text("技能冷却中…", NamedTextColor.GRAY));
            return;
        }
        MagicFxService.play(player, ability.fx());
        if (game instanceof MaggoteersGame mg) {
            boolean success = EffectService.executeAbility(player, mg, ability);
            if (ability.consume() && success) {
                spendOneFromMainHand(player);
            }
        }
    }

    private static void spendOneFromMainHand(Player player) {
        var hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }
}