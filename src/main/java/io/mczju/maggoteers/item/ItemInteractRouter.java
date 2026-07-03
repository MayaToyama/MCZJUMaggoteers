package io.mczju.maggoteers.item;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.effect.Trigger;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.interact.ItemUseHandler;
import io.mczju.maggoteers.item.interact.OpenClassMenuHandler;
import io.mczju.maggoteers.item.interact.OpenRestMenuHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 物品交互统一路由（§10.4）：右键 → 三层分发。
 *
 * <h2>三层分发顺序</h2>
 * <ol>
 *   <li><b>ItemKind 分发</b> — 读 PDC {@code maggoteers:kind}，查 {@link #HANDLERS}
 *       （货币/职业券/复活币等已知类别）。</li>
 *   <li><b>Weapon ID 分发</b> — kind 无命中时，读 PDC {@code maggoteers:id}，
 *       查 {@link #WEAPON_HANDLERS}（未来魔法武器通过 {@link #registerHandler} 注册）。</li>
 *   <li><b>ON_INTERACT 兜底</b> — 仍有 id 但无 handler 的本插件物品，
 *       fire {@link Trigger#ON_INTERACT}（供玩家全局触发型被动）。</li>
 * </ol>
 *
 * <h2>CD 门禁（§10.3 D2）</h2>
 * <p>第三层兜底（ON_INTERACT）由路由自身按 {@code weaponId} 计 CD（默认 1s）。
 * 第二层（已注册 handler）的 CD 由 handler 在其 {@code onUse} 内自行调用
 * {@link CooldownService#tryUse(java.util.UUID, String, int)} 管控——路由不代劳，
 * 因为不同武器的 CD 时长各异。</p>
 *
 * <h2>扩展指南（给未来 agent）</h2>
 * <p>添加一把魔法武器（如 {@code maggoteers:fire_sword}）的右键效果：</p>
 * <ol>
 *   <li>在 {@code items/*.yml} 里定义物品，带上 PDC {@code maggoteers:id = "maggoteers:fire_sword"}。</li>
 *   <li>写一个实现 {@link ItemUseHandler} 的 handler 类，在其 {@code onUse} 内调用
 *       {@link CooldownService#tryUse(java.util.UUID, String, int)} 做门禁。</li>
 *   <li>在 {@code onEnable}（{@link ItemService#init} 之后）调用：
 *       {@code ItemInteractRouter.registerHandler("maggoteers:fire_sword", new FireSwordHandler());}</li>
 * </ol>
 * <p>无需修改路由核心代码。</p>
 */
public final class ItemInteractRouter implements Listener {

    private static final Map<ItemKind, ItemUseHandler> HANDLERS = new EnumMap<>(ItemKind.class);

    /** weapon_id（maggoteers:id）→ handler。未来魔法武器用 {@link #registerHandler} 注册，免改路由核心。 */
    private static final Map<String, ItemUseHandler> WEAPON_HANDLERS = new HashMap<>();

    /** 第三层兜底的默认 CD（秒）。Plan 8 接 item_cooldowns.yml 按物品配。 */
    private static final int DEFAULT_INTERACT_CD_SEC = 1;

    static {
        OpenRestMenuHandler rest = new OpenRestMenuHandler();
        HANDLERS.put(ItemKind.CURRENCY_NORMAL, rest);
        HANDLERS.put(ItemKind.CURRENCY_BOSS, rest);
        HANDLERS.put(ItemKind.CLASS_TICKET, new OpenClassMenuHandler());
        HANDLERS.put(ItemKind.REVIVE_COIN, (player, game, stack) ->
                MenuFacade.open("maggoteers-revive", player, game));
    }

    /**
     * 注册一把魔法武器的右键 handler（供未来 agent 扩展）。
     * <p>weaponId 必须与 items/*.yml 里的 {@code maggoteers:id} 一致。
     * 例：{@code ItemInteractRouter.registerHandler("maggoteers:fire_sword", new FireSwordHandler());}
     * 在 {@code onEnable}（ItemService init 之后）调用一次即可。
     */
    public static void registerHandler(String weaponId, ItemUseHandler handler) {
        if (weaponId != null && handler != null) WEAPON_HANDLERS.put(weaponId, handler);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack stack = event.getItem();
        ItemKind kind = ItemService.readKind(stack);

        // 1) 已知 kind（货币/职业券/复活币…）→ 专用 handler
        ItemUseHandler handler = (kind == null) ? null : HANDLERS.get(kind);

        // 2) 否则：按 weapon_id（maggoteers:id）查注册表（未来魔法武器 handler 挂这里）
        String weaponId = null;
        if (handler == null) {
            weaponId = ItemService.itemIdOf(stack);
            if (weaponId != null) handler = WEAPON_HANDLERS.get(weaponId);
        }

        // 3) 仍无 handler：若是本插件物品（有 id）→ 兜底 fire ON_INTERACT（玩家全局触发型被动）
        if (handler == null) {
            if (weaponId == null) weaponId = ItemService.itemIdOf(stack);
            if (weaponId == null) return;          // 不是本插件物品
            event.setCancelled(true);
            PlayerExt pe = new PlayerExt(event.getPlayer());
            if (!pe.isInGame()) return;
            AbstractGame game = pe.getGame();
            if (game == null) return;
            // §10.3 D2：兜底层按 weaponId 计 CD（默认 1s）
            if (!CooldownService.tryUse(event.getPlayer().getUniqueId(), weaponId, DEFAULT_INTERACT_CD_SEC)) {
                event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                        "冷却中…", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                return;
            }
            EffectService.fireTrigger((MaggoteersGame) game, Trigger.ON_INTERACT);
            return;
        }

        event.setCancelled(true);
        PlayerExt pe = new PlayerExt(event.getPlayer());
        if (!pe.isInGame()) return;
        AbstractGame game = pe.getGame();
        if (game == null) return;
        handler.onUse(event.getPlayer(), game, stack);
    }
}
