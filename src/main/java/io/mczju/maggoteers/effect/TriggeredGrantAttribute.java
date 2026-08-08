package io.mczju.maggoteers.effect;

import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.util.GameRegistries;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Triggered reward STAT: template + {@code {id}:grant} layers and {@code REVOKE_GRANTS}. */
public final class TriggeredGrantAttribute {

    public static final String GRANT_SUFFIX = ":grant";
    public static final String OP_REVOKE = "REVOKE_GRANTS";

    private TriggeredGrantAttribute() {}

    public static String grantId(String sourceId) {
        return sourceId + GRANT_SUFFIX;
    }

    public static boolean isRevoke(EffectContext params) {
        if (params == null) return false;
        return OP_REVOKE.equalsIgnoreCase(params.getOrDefault(EffectKeys.OP, ""));
    }

    public static boolean grantUsesBukkitAttribute(PlayerEffect grant) {
        if (grant == null || grant.effect() != Effect.ADD_ATTRIBUTE) return false;
        EffectContext params = grant.params();
        if (params == null) return false;
        String name = params.get(EffectKeys.ATTR_NAME);
        if (name != null && GameRegistries.isVirtualAttribute(name)) return false;
        Attribute attr = params.get(EffectKeys.ATTR);
        if (attr != null) return true;
        if (name != null && isKnownBukkitAttributeName(name)) return true;
        return name != null && GameRegistries.attribute(name) != null;
    }

    private static boolean isKnownBukkitAttributeName(String name) {
        String u = name.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "MAX_HEALTH", "ATTACK_DAMAGE", "MOVEMENT_SPEED", "ATTACK_SPEED" -> true;
            default -> false;
        };
    }

    public static void applyGrant(Player p, MaggoteersGame game, PlayerEffect template) {
        if (p == null || game == null || template == null) return;
        EffectContext params = template.params() != null ? template.params().copy() : new EffectContext();
        PlayerEffect grant = new PlayerEffect(
                grantId(template.id()),
                Effect.ADD_ATTRIBUTE,
                params,
                null,
                template.expiryTrigger(),
                template.expiryCharges(),
                0,
                null,
                template.stack(),
                template.upgradeMax(),
                0);
        grant.setLevel(template.level());
        EffectService.apply(p, grant, game);
    }

    /** Removes all grants for {@code sourceId}. Returns true if any removed grant used Bukkit attributes. */
    public static boolean revokeGrants(PlayerState st, String sourceId) {
        if (st == null || sourceId == null || sourceId.isBlank()) return false;
        String gid = grantId(sourceId);
        boolean needsResync = false;
        var it = st.effects().iterator();
        while (it.hasNext()) {
            PlayerEffect e = it.next();
            if (!gid.equals(e.id())) continue;
            if (grantUsesBukkitAttribute(e)) needsResync = true;
            it.remove();
        }
        return needsResync;
    }

    public static boolean isRevokeOp(String op) {
        return op != null && OP_REVOKE.equalsIgnoreCase(op.trim());
    }

    public static String normalizeOp(String op) {
        return op == null ? "" : op.toUpperCase(Locale.ROOT);
    }
}