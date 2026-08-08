package io.mczju.maggoteers.reward;

import io.mczju.maggoteers.game.MaggoteersGame;
import io.mczju.maggoteers.item.ItemService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Optional;

public final class RewardOptionIcons {

    private RewardOptionIcons() {}

    public static ItemStack preview(RewardOption opt, Player viewer, MaggoteersGame game) {
        ItemStack stack = resolveBaseStack(opt, viewer, game);
        return appendGuiLore(stack, opt);
    }

    private static ItemStack resolveBaseStack(RewardOption opt, Player viewer, MaggoteersGame game) {
        if (opt.isBundle()) {
            for (RewardOption grant : opt.grants()) {
                ItemStack g = grantStack(grant, viewer, game);
                if (g != null && g.getType() != Material.PAPER) {
                    return g;
                }
            }
            return fallbackPaper(opt);
        }
        return grantStack(opt, viewer, game);
    }

    private static ItemStack grantStack(RewardOption opt, Player viewer, MaggoteersGame game) {
        return switch (opt.category()) {
            case WEAPON, SUPPLY -> ItemService.createItem(opt.item(), 1)
                    .orElse(new ItemStack(Material.PAPER));
            case STAT -> {
                if (game != null) {
                    yield CollectibleService.preview(opt.id(), viewer, game)
                            .orElse(fallbackPaper(opt));
                }
                yield CollectibleService.preview(opt.id(), 1)
                        .orElse(fallbackPaper(opt));
            }
            case BUNDLE -> fallbackPaper(opt);
        };
    }

    public static ItemStack fallbackPaper(RewardOption opt) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(opt.displayPlain(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack appendGuiLore(ItemStack stack, RewardOption opt) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        var lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<Component>();
        if (opt.description() != null && !opt.description().isBlank()) {
            lore.add(Component.text(opt.description(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (opt.isBundle()) {
            for (RewardOption grant : opt.grants()) {
                String line = grantSummary(grant);
                if (!line.isBlank()) {
                    lore.add(Component.text("\u00b7 " + line, NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                }
            }
        }
        lore.add(Component.text("\u25b6 \u70b9\u51fb\u9009\u62e9", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String grantSummary(RewardOption grant) {
        return switch (grant.category()) {
            case WEAPON, SUPPLY -> Optional.ofNullable(grant.item()).orElse("\u7269\u54c1");
            case STAT, BUNDLE -> grant.displayPlain();
        };
    }
}