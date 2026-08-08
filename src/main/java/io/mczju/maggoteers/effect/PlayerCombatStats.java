package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;

import java.util.List;
import java.util.UUID;

public final class PlayerCombatStats {

    private PlayerCombatStats() {}

    public static double magicDamageMultiplier(MaggoteersGame game, UUID uuid) {
        PlayerState st = PlayerStateManager.get(game, uuid);
        if (st == null) return 1.0;
        return magicDamageMultiplierFromEffects(st.effects());
    }

    static double magicDamageMultiplierFromEffects(List<PlayerEffect> effects) {
        double sum = 0.0;
        for (PlayerEffect e : effects) {
            sum += magicDamagePercentContribution(e);
        }
        return 1.0 + sum;
    }

    static double magicDamagePercentContribution(PlayerEffect e) {
        if (e.fireTrigger() != null) return 0.0;
        if (e.effect() != Effect.ADD_ATTRIBUTE) return 0.0;
        String attrName = e.params().get(EffectKeys.ATTR_NAME);
        if (attrName == null || !VirtualStats.isMagicDamage(attrName)) return 0.0;
        if (!"PERCENT".equalsIgnoreCase(e.params().getOrDefault(EffectKeys.OP, "FLAT"))) return 0.0;
        return e.params().getOrDefault(EffectKeys.VALUE, 0.0);
    }
}
