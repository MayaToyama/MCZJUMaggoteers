package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerQuitStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
import io.mczju.maggoteers.config.MessageService;
import io.mczju.maggoteers.item.ItemKind;
import io.mczju.maggoteers.item.ItemService;
import io.mczju.maggoteers.plan.ActPlan;
import io.mczju.maggoteers.plan.RunPlanner;
import io.mczju.maggoteers.state.PlayerStateManager;
import io.mczju.maggoteers.ui.RunScoreboard;
import io.mczju.maggoteers.wave.WaveScheduler;
import io.mczju.maggoteers.world.ActSpawnHelper;
import io.mczju.maggoteers.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import io.mczju.maggoteers.effect.Effect;
import io.mczju.maggoteers.effect.EffectContext;
import io.mczju.maggoteers.effect.EffectKeys;
import io.mczju.maggoteers.effect.EffectService;
import io.mczju.maggoteers.effect.PlayerEffect;
import io.mczju.maggoteers.effect.Stack;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MaggoteersGame extends AbstractGame {

    private volatile boolean planReady = false;
    private volatile List<ActPlan> plannedActs;
    private volatile String planError;

    private GameOutcome outcome = GameOutcome.IN_PROGRESS;
    private volatile boolean wavesStarted = false;
    /** 本局是否已走完 cleanupRun（幂等 + 异步/延迟启动任务的存活信号）。 */
    private volatile boolean cleanedUp = false;
    /**
     * 本局参与玩家 UUID（开局时记录）。结算遍历用——失败结算在「全员 leave 后」才触发（finishGame），
     * 此刻 getPlayers() 已被 leaveGame 清空，遍历它会把中途死亡/退出的玩家全漏掉、积分发不出去。
     */
    private final List<UUID> runPlayerUuids = new ArrayList<>();

    @Override public String getId() { return "maggoteers"; }

    @Override
    public GameMeta getGameMeta() {
        return GameMeta.builder()
                .displayName(MessageService.raw("game.meta_name", Map.of()))
                .icon(Material.NETHERITE_SWORD)
                .author(MessageService.raw("game.meta_author", Map.of()))
                .description(MessageService.rawList("game.meta_description"))
                .build();
    }

    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        int min = MaggoteersPlugin.getInstance().getConfig().getInt("wait.min_players", 1);
        return new DefaultGameWaitStrategy(this, 4, min) {
            /** 清掉上局残留的 maggoteers profile 物品（joinGame 已把陈旧快照 apply 到身上）。 */
            @Override
            public boolean onPlayerJoin(PlayerExt player) {
                player.player().getInventory().clear();
                boolean ok = super.onPlayerJoin(player);
                if (ok) MaggoteersRoom.teleportWait(MaggoteersGame.this, player.player());
                return ok;
            }
            @Override
            public boolean onPartyJoin(Party party) {
                party.getAllPlayer().forEach(p -> p.player().getInventory().clear());
                boolean ok = super.onPartyJoin(party);
                if (ok) {
                    party.getAllPlayer().forEach(p ->
                            MaggoteersRoom.teleportWait(MaggoteersGame.this, p.player()));
                }
                return ok;
            }
        };
    }

    @Override
    public AbstractPlayerDeathStrategy getPlayerDeathStrategy() {
        return new MaggoteersDeathStrategy(this);
    }

    @Override
    public AbstractPlayerQuitStrategy getPlayerQuitStrategy() {
        return new MaggoteersPlayerQuitStrategy(this);
    }

    public GameOutcome getOutcome() { return outcome; }
    public void setOutcome(GameOutcome o) { this.outcome = o; }

    /** 开局选职业阶段（已进图、波次未开）。 */
    public boolean isInClassSelectPhase() {
        return outcome == GameOutcome.IN_PROGRESS && plannedActs != null && !wavesStarted;
    }

    /** 调试用：本局预生成剧本（波次未开或进行中均可读）。 */
    public List<ActPlan> getPlannedActs() { return plannedActs; }

    public void win() {
        if (cleanedUp || outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.WIN;
        MaggoteersPlugin.getInstance().getLogger().info("对局胜利 endGame identity="
                + System.identityHashCode(this));
        MCZJUGameCore.getGameManager().endGame(this);
    }

    public void fail() {
        if (cleanedUp || outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.FAIL;
        MaggoteersPlugin.getInstance().getLogger().info("对局失败 endGame identity="
                + System.identityHashCode(this));
        MCZJUGameCore.getGameManager().endGame(this);
    }

    /** 全员倒下：标记失败 + 停波清怪，但保留房间观战；真正结束等全员 leave（{@link #finishGame()}）。 */
    public void markDefeated() {
        if (cleanedUp || outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.FAIL;
        MaggoteersPlugin.getInstance().getLogger().info("对局失败(观战) markDefeated identity="
                + System.identityHashCode(this));
        WaveScheduler.defeat(this);
        sender().warn(MessageService.raw("game.defeat", Map.of()));
    }

    /** 全员 leave 后真正结束（中途全员退出视为失败）。 */
    public void finishGame() {
        if (cleanedUp) return;
        if (outcome == GameOutcome.IN_PROGRESS) {
            outcome = GameOutcome.FAIL;
        }
        MaggoteersPlugin.getInstance().getLogger().info("对局结束 finishGame identity="
                + System.identityHashCode(this));
        MCZJUGameCore.getGameManager().endGame(this);
    }

    @Override
    protected boolean onGameInit() {
        planReady = false;
        wavesStarted = false;
        runPlayerUuids.clear();   // 新局重置（防上一局快照串进本局结算）
        return true;
    }

    @Override
    protected void onGameStart() {
        runWhenReady(this::startInWorld);
    }

    private void startInWorld() {
        if (cleanedUp) return;   // 开局前已被取消/中止：勿再建世界
        World w = WorldService.create(this);
        if (w == null) {
            sender().error(MessageService.raw("game.world_create_failed", Map.of()));
            fail();
            return;
        }

        int lives = MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2);
        ClassSelectGate.reset(this);
        for (PlayerExt pe : getPlayers()) {
            runPlayerUuids.add(pe.player().getUniqueId());   // 结算快照：开局玩家全量记录
            // ⚠️ 必须先 switchProfile 再清空（bug #1 跨局残留根因）：
            //  switchProfile 的 capture 捕获「切换前」的 lobby 存档（若先 clear 会清空 lobby 永久污染大厅物品），
            //  然后 apply 载入 maggoteers 快照——该快照对中途退出的玩家保留着退局时刻的物品栏/末影箱
            //  （含护符/货币/武器）。故在 switchProfile 之后 clear 主物品栏 + 末影箱，清掉上局残留再发本局装备。
            pe.switchProfile(this.getId());
            pe.player().getInventory().clear();
            pe.player().getEnderChest().clear();
            pe.player().setGameMode(GameMode.ADVENTURE);
            PlayerStateManager.init(this, pe.player().getUniqueId(), lives);
        }

        final long seed = WorldService.getSeed(this);
        final int players = getPlayers().size();
        sender().info(MessageService.raw("game.plan_generating", Map.of()));
        Bukkit.getScheduler().runTaskAsynchronously(MaggoteersPlugin.getInstance(), () -> {
            try {
                plannedActs = RunPlanner.plan(seed, players);
            } catch (Exception e) {
                planError = e.getMessage();
                MaggoteersPlugin.getInstance().getLogger().severe("RunPlanner 失败：" + e.getMessage());
            } finally {
                planReady = true;
            }
        });
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (cleanedUp || outcome != GameOutcome.IN_PROGRESS) { cancel(); return; }
                if (planReady) { cancel(); beginClassSelect(); }
                else if (++ticks > 20 * 30) {
                    cancel();
                    sender().warn(MessageService.raw("game.plan_timeout", Map.of()));
                    fail();
                }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 1L, 1L);
    }

    /** 进图 → 发职业券 → 选职业（右键券可重开菜单）；全员选完再开波。 */
    private void beginClassSelect() {
        if (plannedActs == null) {
            String detail = planError == null ? "" : "：" + planError;
            sender().error(MessageService.raw("game.plan_failed", Map.of("detail", detail)));
            fail();
            return;
        }
        ActPlan act1 = plannedActs.get(0);
        ActSpawnHelper.pasteAndTeleport(this, act1, 0);
        RunScoreboard.start(this);
        if (MaggoteersPlugin.getInstance().getConfig().getBoolean("player.night_vision", true)) {
            for (PlayerExt pe : getPlayers()) {
                grantRunNightVision(pe.player());
            }
        }

        sender().info(MessageService.raw("game.enter_class_select", Map.of()));
        for (PlayerExt pe : getPlayers()) {
            ItemService.giveInitialEquipment(pe.player());
            ItemService.giveKind(pe.player(), ItemKind.CLASS_TICKET, 1);
            ItemService.giveKind(pe.player(), ItemKind.SHOP_EMERALD, 1);
            MenuFacade.open("maggoteers-class", pe.player(), this);
        }
        int timeout = MaggoteersPlugin.getInstance().getConfig().getInt("class_select.timeout_sec", 45);
        new BukkitRunnable() {
            int left = timeout;
            @Override public void run() {
                if (cleanedUp || outcome != GameOutcome.IN_PROGRESS) { cancel(); return; }
                if (ClassSelectGate.allChosen(MaggoteersGame.this)) { cancel(); onAllClassesChosen(); return; }
                if (--left <= 0) {
                    cancel();
                    ClassSelectGate.autoPickRemaining(MaggoteersGame.this, new Random());
                    onAllClassesChosen();
                }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 20L, 20L);
    }

    /** 全员职业选完（或超时兜底）后启动波次。 */
    public void onAllClassesChosen() {
        if (plannedActs == null || cleanedUp || outcome != GameOutcome.IN_PROGRESS || wavesStarted) return;
        wavesStarted = true;
        // 职业 STAT（含 MAX_HEALTH）应用后统一 resync + 回满，避免切武器/二次粘贴打乱血量
        for (PlayerExt pe : getPlayers()) {
            Player pl = pe.player();
            EffectService.resync(pl);
            PlayerStateManager.restoreFullHealth(pl);
        }
        io.mczju.maggoteers.effect.EffectListener.startTick();
        WaveScheduler.start(this, plannedActs);
        int prep = MaggoteersPlugin.getInstance().getConfig().getInt("act_enter.prep_sec", 10);
        String prepClause = prep > 0
                ? MessageService.raw("game.prep_clause", Map.of("prep", String.valueOf(prep)))
                : "";
        sender().info(MessageService.raw("game.ready", Map.of(
                "prep_clause", prepClause,
                "waves", String.valueOf(plannedActs.stream().mapToInt(a -> a.waves().size()).sum()),
                "lives", String.valueOf(MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2)))));
    }

    private static void grantRunNightVision(Player player) {
        EffectContext ctx = new EffectContext();
        ctx.put(EffectKeys.POTION, PotionEffectType.NIGHT_VISION);
        ctx.put(EffectKeys.AMP, 0);
        PlayerEffect pe = new PlayerEffect(
                "run_night_vision", Effect.ADD_POTION, ctx, null,
                null, 0, 0, null, Stack.REPLACE, 0, 0);
        EffectService.apply(player, pe);
    }

    private void cleanupRun() {
        if (cleanedUp) return;
        cleanedUp = true;

        // 结算：按 outcome 发账户货币（D4）。
        // ⚠️ 失败结算在「全员 leave 后」才触发（finishGame），此刻 getPlayers() 已被 leaveGame 清空——
        // 必须遍历开局快照 runPlayerUuids，否则中途死亡/退出的玩家（乃至整个败局）都拿不到积分。
        {
            int winFlat = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.win_flat", 100);
            int failPerAct = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.fail_per_act", 25);
            int failPerWave = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.fail_per_wave", 5);
            int[] prog = WaveScheduler.progress(this);
            var input = new io.mczju.maggoteers.persist.Settlement.Input(
                    outcome, prog[0], prog[1]);
            int grant = io.mczju.maggoteers.persist.Settlement.grant(input, winFlat, failPerAct, failPerWave);
            if (grant > 0) {
                var pdm = MCZJUGameCore.getPlayerDataManager();
                for (UUID uid : runPlayerUuids) {
                    var data = pdm.getPlayerData(uid.toString(), io.mczju.maggoteers.persist.MaggoteersPlayerData.class);
                    if (data == null) continue;
                    data.grant(grant);
                    // 已退玩家不在 getPlayers()，MGC 仅在退出/定时自动保存；这里显式保存确保 grant 落盘。
                    pdm.savePlayerDataAsync(uid.toString());
                    Player pl = Bukkit.getPlayer(uid);
                    if (pl != null) {
                        pl.sendMessage(MessageService.component("game.settlement_grant", Map.of(
                                "player", pl.getName(),
                                "grant", String.valueOf(grant))));
                    }
                }
            }
        }

        for (var pe : getPlayers()) {
            var pl = pe.player();
            pl.getInventory().clear();
            // 末影箱是玩家全局持久数据（局内可正常使用），对局结束必须清空，
            // 且在 switchProfile(null) 之前清——让 capture 把空末影箱写进 maggoteers profile 快照，根除跨局残留。
            pl.getEnderChest().clear();
            EffectService.removeAll(pl);
            io.mczju.maggoteers.item.CooldownService.clear(pl.getUniqueId());
            io.mczju.maggoteers.reward.RewardService.clearDraws(pl.getUniqueId());
            pe.switchProfile(null);
            pl.setGameMode(GameMode.SURVIVAL);
            resetLobbyVitality(pl);
            MaggoteersRoom.teleportReturn(this, pl);
        }

        io.mczju.maggoteers.effect.SummonRegistry.clearAll(this);
        io.mczju.maggoteers.effect.MobAiLockRegistry.restoreAll(this);
        io.mczju.maggoteers.effect.EffectListener.stopTick();
        WaveScheduler.stop(this);
        RunScoreboard.stop(this);
        ClassSelectGate.clear(this);
        PlayerStateManager.destroyAll(this);
        WorldService.cleanup(this);
        io.mczju.maggoteers.effect.AuraService.clearGame(this);
    }

    /** 剥除局内效果后再复位；勿写死 20 血（MAX_HEALTH 加成可能尚未还原）。quit 策略也会用。 */
    static void resetLobbyVitality(Player pl) {
        PlayerStateManager.healToMax(pl);
        pl.setFoodLevel(20);
        pl.setSaturation(5f);
        PlayerStateManager.clearFallFire(pl);
    }

    @Override protected void onGameCancel() { cleanupRun(); }
    @Override protected void onGameAbort()  { cleanupRun(); }
    @Override protected void onGameEnd()    { cleanupRun(); }

    private void runWhenReady(Runnable action) {
        new BukkitRunnable() {
            @Override public void run() { cancel(); action.run(); }
        }.runTask(MaggoteersPlugin.getInstance());
    }
}
