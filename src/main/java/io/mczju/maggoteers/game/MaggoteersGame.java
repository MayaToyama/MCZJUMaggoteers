package io.mczju.maggoteers.game;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameMeta;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.DefaultGameWaitStrategy;
import com.github.mczjuops.mczjugamecore.game.strategy.wait.GameWaitStrategy;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.player.strategy.AbstractPlayerDeathStrategy;
import io.mczju.maggoteers.MaggoteersPlugin;
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

import java.util.List;
import java.util.Random;

public class MaggoteersGame extends AbstractGame {

    private volatile boolean planReady = false;
    private volatile List<ActPlan> plannedActs;
    private volatile String planError;

    private GameOutcome outcome = GameOutcome.IN_PROGRESS;
    private volatile boolean wavesStarted = false;

    @Override public String getId() { return "maggoteers"; }

    @Override
    public GameMeta getGameMeta() {
        return GameMeta.builder()
                .displayName("<gold>卫戍协议")
                .icon(Material.NETHERITE_SWORD)
                .author("<green>MCZJU")
                .description(List.of("<aqua>4人合作 PvE · 波数防守 + Roguelike"))
                .build();
    }

    @Override
    public GameWaitStrategy getGameWaitStrategy() {
        int min = MaggoteersPlugin.getInstance().getConfig().getInt("wait.min_players", 1);
        return new DefaultGameWaitStrategy(this, 4, min);
    }

    @Override
    public AbstractPlayerDeathStrategy getPlayerDeathStrategy() {
        return new MaggoteersDeathStrategy(this);
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
        if (outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.WIN;
        MaggoteersPlugin.getInstance().getLogger().info("对局胜利 endGame identity="
                + System.identityHashCode(this));
        MCZJUGameCore.getGameManager().endGame(this);
    }

    public void fail() {
        if (outcome != GameOutcome.IN_PROGRESS) return;
        outcome = GameOutcome.FAIL;
        MaggoteersPlugin.getInstance().getLogger().info("对局失败 endGame identity="
                + System.identityHashCode(this));
        MCZJUGameCore.getGameManager().endGame(this);
    }

    @Override
    protected boolean onGameInit() {
        planReady = false;
        wavesStarted = false;
        return true;
    }

    @Override
    protected void onGameStart() {
        runWhenReady(this::startInWorld);
    }

    private void startInWorld() {
        World w = WorldService.create(this);
        if (w == null) { sender().error("世界创建失败，对局终止。"); return; }

        int lives = MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2);
        ClassSelectGate.reset(this);
        for (PlayerExt pe : getPlayers()) {
            pe.switchProfile(this.getId());
            pe.player().setGameMode(GameMode.ADVENTURE);
            pe.player().getInventory().clear();
            PlayerStateManager.init(this, pe.player().getUniqueId(), lives);
        }

        final long seed = WorldService.getSeed(this);
        final int players = getPlayers().size();
        sender().info("<gray>正在生成本局剧本（异步）…");
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
                if (planReady) { cancel(); beginClassSelect(); }
                else if (++ticks > 20 * 30) { cancel(); sender().warn("剧本生成超时(30s)，终止。"); }
            }
        }.runTaskTimer(MaggoteersPlugin.getInstance(), 1L, 1L);
    }

    /** 进图 → 发职业券 → 选职业（右键券可重开菜单）；全员选完再开波。 */
    private void beginClassSelect() {
        if (plannedActs == null) {
            sender().error("剧本生成失败" + (planError != null ? "：" + planError : "") + "，对局终止。");
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

        sender().info("<yellow>已进入第 1 层地图 · 请选择职业（右键职业选择券可重新打开菜单）");
        for (PlayerExt pe : getPlayers()) {
            ItemService.giveKind(pe.player(), ItemKind.CLASS_TICKET, 1);
            ItemService.giveKind(pe.player(), ItemKind.SHOP_EMERALD, 1);
            MenuFacade.open("maggoteers-class", pe.player(), this);
        }
        int timeout = MaggoteersPlugin.getInstance().getConfig().getInt("class_select.timeout_sec", 45);
        new BukkitRunnable() {
            int left = timeout;
            @Override public void run() {
                if (outcome != GameOutcome.IN_PROGRESS) { cancel(); return; }
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
        if (plannedActs == null || outcome != GameOutcome.IN_PROGRESS || wavesStarted) return;
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
        sender().info("<green>卫戍协议 已就绪！"
                + (prep > 0 ? " 地图准备 " + prep + " 秒后开战。" : "")
                + " 三层共 "
                + plannedActs.stream().mapToInt(a -> a.waves().size()).sum() + " 波。每人复活 "
                + MaggoteersPlugin.getInstance().getConfig().getInt("lives.default", 2) + " 次。");
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
        EffectService.fireTrigger(this, io.mczju.maggoteers.effect.Trigger.ON_GAME_END);

        // 结算：按 outcome 发账户货币（D4）
        {
            int winFlat = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.win_flat", 100);
            int failPerAct = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.fail_per_act", 25);
            int failPerWave = MaggoteersPlugin.getInstance().getConfig().getInt("settlement.fail_per_wave", 5);
            int[] prog = WaveScheduler.progress(this);
            var input = new io.mczju.maggoteers.persist.Settlement.Input(
                    outcome, prog[0], prog[1]);
            int grant = io.mczju.maggoteers.persist.Settlement.grant(input, winFlat, failPerAct, failPerWave);
            if (grant > 0) {
                for (var pe : getPlayers()) {
                    var data = pe.getData(io.mczju.maggoteers.persist.MaggoteersPlayerData.class);
                    if (data != null) {
                        data.grant(grant);
                        sender().info("<yellow>" + pe.player().getName() + " 结算获得 " + grant + " 卫戍币。");
                    }
                }
            }
        }

        for (var pe : getPlayers()) {
            var pl = pe.player();
            pl.getInventory().clear();
            EffectService.removeAll(pl);
            pe.switchProfile(null);
            pl.setGameMode(GameMode.SURVIVAL);
            resetLobbyVitality(pl);
        }

        io.mczju.maggoteers.effect.SummonRegistry.clearAll(this);
        io.mczju.maggoteers.effect.MobAiLockRegistry.restoreAll();
        io.mczju.maggoteers.effect.EffectListener.stopTick();
        WaveScheduler.stop(this);
        RunScoreboard.stop(this);
        ClassSelectGate.clear(this);
        PlayerStateManager.destroyAll(this);
        WorldService.cleanup(this);
        io.mczju.maggoteers.effect.AuraService.clearGame(this);
    }

    /** 剥除局内效果后再复位；勿写死 20 血（MAX_HEALTH 加成可能尚未还原）。 */
    private static void resetLobbyVitality(Player pl) {
        var maxHp = pl.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHp != null ? maxHp.getValue() : 20.0;
        pl.setHealth(max);
        pl.setFoodLevel(20);
        pl.setSaturation(5f);
        pl.setFallDistance(0f);
        pl.setFireTicks(0);
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
