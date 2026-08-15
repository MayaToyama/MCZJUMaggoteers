package io.mczju.maggoteers.item.interact;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import io.mczju.maggoteers.effect.AbilityStep;
import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.effect.ItemAbility;
import io.mczju.maggoteers.effect.ItemAbilityRegistry;
import io.mczju.maggoteers.item.fx.MagicUseFx;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.CooldownService;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.item.fx.MagicFxConfig;
import io.mczju.maggoteers.item.fx.MagicFxService;
import io.mczju.maggoteers.config.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

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
        if (CooldownService.onCooldown(player.getUniqueId(), itemId)) {
            player.sendMessage(MessageService.component("item.ability_cooldown", Map.of()));
            return;
        }
        if (!(game instanceof MaggoteersGame mg)) {
            return;
        }
        boolean success = EffectService.executeAbility(player, mg, ability);
        if (!success) {
            return;
        }
        CooldownService.tryUse(player, stack, itemId, ability.cooldownSec());
        boolean areaRing = false;
        for (AbilityStep step : ability.steps()) {
            if (!step.isDeferred()
                    && MagicFxConfig.shouldPlayCastAreaRing(step.effect(), step.params())) {
                areaRing = true;
                break;
            }
        }
        MagicFxService.play(player, resolveCastFx(ability), areaRing);
        if (ability.consume()) {
            spendOneFromMainHand(player);
        }
    }

    private static MagicUseFx resolveCastFx(ItemAbility ability) {
        MagicUseFx fx = ability.fx();
        if (fx == null) {
            return null;
        }
        for (AbilityStep step : ability.steps()) {
            if (step.isDeferred() || step.effect() != Effect.HEAL_AREA || step.params() == null) {
                continue;
            }
            double radius = step.params().getOrDefault(EffectKeys.RADIUS, fx.radius());
            if (radius > 0 && radius != fx.radius()) {
                return new MagicUseFx(
                        fx.sound(), fx.soundVolume(), fx.soundPitch(), fx.preset(), fx.particle(),
                        radius, fx.rayLength(), fx.density(), fx.rippleRings(), fx.expandSteps(),
                        fx.spiralTicks());
            }
        }
        return fx;
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
