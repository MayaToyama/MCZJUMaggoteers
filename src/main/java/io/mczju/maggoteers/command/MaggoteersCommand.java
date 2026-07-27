package io.mczju.maggoteers.command;

import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.config.AffixService;
import io.mczju.maggoteers.plan.Compose;
import io.mczju.maggoteers.config.ScalingConfig;
import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.mob.MobFactory;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.RunConfig;
import io.mczju.maggoteers.plan.RunPlanner;
import io.mczju.maggoteers.state.PlayerState;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.wave.WaveEngine;
import io.mczju.maggoteers.wave.WaveScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /maggoteers 命令。
 * <ul>
 *   <li>{@code shop} —— 局外解锁商店（所有人）。</li>
 *   <li>{@code debug state|plan|wave|act|give|coin|jumpto|jump|spawnmob|effects} —— 运维/调试（需 maggoteers.admin）。</li>
 * </ul>
 */
public class MaggoteersCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("仅玩家可用。"); return true; }
        if (args.length == 0) { usage(p); return true; }

        switch (args[0].toLowerCase()) {
            case "shop" -> { MenuFacade.open("maggoteers-shop", p); return true; }
            case "debug" -> { return debug(p, args); }
            default -> { usage(p); return true; }
        }
    }

    private boolean debug(Player p, String[] args) {
        if (!p.hasPermission("maggoteers.admin")) {
            p.sendMessage(Component.text("无权限（需 maggoteers.admin）。", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(Component.text("用法：/maggoteers debug <state|plan|wave|act|give|coin|jumpto|jump|spawnmob|effects> [...]", NamedTextColor.GRAY));
            return true;
        }
        var pe = new PlayerExt(p);
        MaggoteersGame game = PlayerStateManager.gameForPlayer(p.getUniqueId());
        if (game == null && pe.isInGame() && pe.getGame() instanceof MaggoteersGame mg) game = mg;
        switch (args[1].toLowerCase()) {
            case "state" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                PlayerState st = PlayerStateManager.get(game, p.getUniqueId());
                if (st == null) { msg(p, "无 PlayerState。"); return true; }
                msg(p, "reviveCount=" + st.getReviveCount() + " alive=" + st.isAlive()
                        + " gamemode=" + p.getGameMode().name()
                        + " effects=" + st.effects().size() + " acquiredUnique=" + st.acquiredUnique().size());
            }
            case "plan" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                int[] prog = WaveScheduler.progress(game);
                msg(p, "actIndex=" + prog[0] + " waveIndex=" + prog[1]);
                List<ActPlan> acts = game.getPlannedActs();
                if (acts == null) { msg(p, "剧本尚未就绪。"); return true; }
                RunConfig rc = RunPlanner.loadRunConfig(io.mczju.maggoteers.MaggoteersPlugin.getInstance());
                String[] actIds = {"act1", "act2", "act3"};
                for (int ai = 0; ai < acts.size(); ai++) {
                    ActPlan act = acts.get(ai);
                    String actId = actIds[ai];
                    int weak = rc.weak(actId);
                    int strong = rc.strong(actId);
                    StringBuilder line = new StringBuilder("层").append(ai + 1).append(" ").append(act.mapId()).append("：");
                    for (int wi = 0; wi < act.waves().size(); wi++) {
                        String tier = wi < weak ? "weak" : (wi < weak + strong ? "strong" : "boss");
                        line.append(" [").append(wi + 1).append(":").append(tier).append("=")
                                .append(act.waves().get(wi).strategyId()).append("]");
                    }
                    msg(p, line.toString());
                }
            }
            case "wave" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                var snap = WaveScheduler.snapshot(game);
                int living = WaveEngine.livingCount(game);
                msg(p, "act=" + (snap == null ? "?" : snap.actIndex()) + " tier=" + (snap == null ? "?" : snap.lastTier())
                        + " rest=" + WaveScheduler.isRestPhase(game) + " livingMobs=" + living);
            }
            case "act" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                int[] prog = WaveScheduler.progress(game);
                msg(p, "当前层=" + (prog[0] + 1) + "/3  当前层波=" + (prog[1] + 1));
            }
            case "give" -> {
                // /maggoteers debug give <item_id> [amount]
                if (args.length < 3) { msg(p, "用法：/maggoteers debug give <item_id> [amount]"); return true; }
                String id = args[2];
                int amt = args.length > 3 ? safeInt(args[3], 1) : 1;
                ItemService.give(p, id, amt);
                msg(p, "已给 " + id + " ×" + amt);
            }
            case "coin" -> {
                // /maggoteers debug coin [amount]
                int amt = args.length > 2 ? safeInt(args[2], 1) : 5;
                ItemService.giveKind(p, ItemKind.CURRENCY_NORMAL, amt);
                msg(p, "已给普通卫戍币 ×" + amt);
            }
            case "jumpto" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                if (args.length < 4) { msg(p, "用法：/maggoteers debug jumpto <层1-3> <波1-n>"); return true; }
                int act = safeInt(args[2], 1) - 1;
                int wave = safeInt(args[3], 1) - 1;
                if (!WaveScheduler.debugSeekWave(game, act, wave)) msg(p, "跳转失败（层/波越界或未开战）。");
                else msg(p, "已跳到 层" + (act + 1) + " 波" + (wave + 1));
            }
            case "jump" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                int delta = args.length > 2 ? safeInt(args[2], 1) : 1;
                if (!WaveScheduler.debugShiftWave(game, delta)) msg(p, "快进/快退失败。");
                else {
                    int[] prog = WaveScheduler.progress(game);
                    msg(p, "当前 层" + (prog[0] + 1) + " 波" + (prog[1] + 1));
                }
            }
            case "spawnmob" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                if (args.length < 3) { msg(p, "用法：/maggoteers debug spawnmob <实体> [词缀...]"); return true; }
                EntityType type;
                try {
                    type = io.mczju.maggoteers.util.GameRegistries.entityType(args[2]);
                } catch (Exception e) {
                    msg(p, "未知实体：" + args[2]);
                    return true;
                }
                List<String> affixes = new ArrayList<>();
                for (int i = 3; i < args.length; i++) affixes.add(args[i]);
                var affixList = AffixService.getInstance().resolve(affixes);
                var snap = ScalingConfig.getInstance().scaleFor(game.getPlayers().size());
                var ms = Compose.compose(new io.mczju.maggoteers.config.CoeffCfg(1, 1, 1), affixList, snap);
                var le = MobFactory.spawnDebugMob(p.getLocation(), type, ms.hp(), ms.dmg(), ms.speed(), affixes);
                if (le != null) WaveEngine.trackDebugMob(game, le, affixes);
                msg(p, le == null ? "生成失败。" : "已生成 " + type.name() + "（词缀=" + affixes + "）");
            }
            case "effects" -> {
                if (game == null) { msg(p, "不在局内。"); return true; }
                Player target = p;
                if (args.length >= 3) {
                    target = org.bukkit.Bukkit.getPlayerExact(args[2]);
                    if (target == null) { msg(p, "找不到玩家：" + args[2]); return true; }
                }
                PlayerState st = PlayerStateManager.get(game, target.getUniqueId());
                if (st == null) { msg(p, "无 PlayerState。"); return true; }
                if (st.effects().isEmpty()) { msg(p, target.getName() + " 无增益。"); return true; }
                msg(p, target.getName() + " 增益 ×" + st.effects().size() + "：");
                for (PlayerEffect ef : st.effects()) {
                    Effect kind = ef.effect();
                    String trig = ef.fireTrigger() == null ? "常驻" : ef.fireTrigger().name();
                    msg(p, " - " + ef.id() + " " + kind.name() + " lv" + ef.level() + " 触发=" + trig);
                }
            }
            default -> msg(p, "未知 debug 子命令：" + args[1]);
        }
        return true;
    }

    private static void usage(Player p) {
        p.sendMessage(Component.text("/maggoteers <shop|debug ...>", NamedTextColor.YELLOW));
    }

    private static void msg(Player p, String text) {
        p.sendMessage(Component.text(text, NamedTextColor.AQUA));
    }

    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
