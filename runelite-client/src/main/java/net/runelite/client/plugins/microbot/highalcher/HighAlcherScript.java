package net.runelite.client.plugins.microbot.highalcher;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import java.awt.Rectangle;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Stand anywhere with nature runes and fire runes (or a fire staff). Each loop does one thing:
 * check the stop conditions, cast one High Alch on the next item that is not on the keep list,
 * or, with restocking on, take more items out of the bank once the inventory is done.
 * Breaks from the Break Handler plugin pause the loop through {@code super.run()}.
 */
@Slf4j
@Singleton // plugin and overlay must share one instance, otherwise the overlay reads a script that never started
public class HighAlcherScript extends Script {

    /** Stop after this many casts in a row that gave no Magic XP (no runes, wrong spellbook, ...). */
    private static final int MAX_FAILED_CASTS = 3;
    /** The bank window and the child index of its X button, the same ones the Enchanter clicks. */
    private static final int BANK_WINDOW = 786434;
    private static final int BANK_CLOSE_BUTTON = 11;

    /** High Alchemy takes 5 game ticks (3 s), the next cast goes through from that tick on. */
    private static final int ALCH_TICKS = 5;
    /** How many ticks before the ready tick the spell gets selected. */
    private static final int PRESELECT_TICKS = 2;

    /** Paused from the panel: the loop keeps running but does nothing until resumed. */
    @Getter
    private volatile boolean paused;
    /** Time spent paused so far, and when the current pause began. Paused time does not count as runtime. */
    private volatile long pausedTotalMs;
    private volatile long pausedSince;

    public synchronized void setPaused(boolean pause) {
        if (pause == paused) return;
        long now = System.currentTimeMillis();
        if (pause) {
            pausedSince = now;
        } else {
            pausedTotalMs += now - pausedSince;
        }
        paused = pause;
    }

    /** Runtime without pauses: what the max runtime, the rates and the overlay clock go by. */
    public long activeMs() {
        if (startedAt == 0) return 0;
        long now = System.currentTimeMillis();
        long pausedMs = pausedTotalMs + (paused ? now - pausedSince : 0);
        return Math.max(0, now - startedAt - pausedMs);
    }
    private int failedCasts;
    /** Game tick of the last cast that gave XP, -1 before the first one. */
    private int lastCastTick;
    /** True once restocking took items out, so the bank gets closed before the next cast. */
    private boolean restocked;
    /** Centre of the High Alchemy icon on screen, null until the spellbook was open once. */
    private java.awt.Point alchPoint;
    /** Inventory slot under the High Alchemy icon, -1 until found. */
    private int alchSlot;

    // Read by the overlay
    @Getter
    private volatile long startedAt;
    @Getter
    private volatile long maxRuntimeMs;
    @Getter
    private volatile int alched;
    @Getter
    private volatile long profit;
    @Getter
    private volatile int startXp;
    @Getter
    private volatile int xp;
    @Getter
    private volatile long lastAlchAt;
    @Getter
    private volatile int currentItemId = -1;
    @Getter
    private volatile String currentItemName = "";
    @Getter
    private volatile int currentProfitEach;
    @Getter
    private volatile int itemsLeft;
    @Getter
    private volatile int naturesLeft;

    public boolean run(HighAlcherConfig config) {
        paused = false;
        pausedTotalMs = 0;
        pausedSince = 0;
        failedCasts = 0;
        lastCastTick = -1;
        restocked = false;
        alchPoint = null;
        alchSlot = -1;
        alched = 0;
        profit = 0;
        startXp = -1;
        lastAlchAt = 0;
        currentItemId = -1;
        currentItemName = "";
        startedAt = System.currentTimeMillis();
        maxRuntimeMs = TimeUnit.MINUTES.toMillis(config.maxRuntimeMinutes());

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (paused) {
                    Microbot.status = "Paused";
                    return;
                }
                xp = magicXp();
                if (startXp < 0) startXp = xp;

                String reason = stopReason(config);
                if (reason != null) {
                    stop(reason, config);
                    return;
                }

                List<Candidate> candidates = candidates(config);
                itemsLeft = candidates.stream().mapToInt(c -> c.item.getQuantity()).sum();
                naturesLeft = Rs2Inventory.itemQuantity(ItemID.NATURERUNE);

                if (candidates.isEmpty() || naturesLeft == 0) {
                    if (config.restockEnabled()) {
                        restock(config);
                    } else {
                        stop(naturesLeft == 0 ? "Out of nature runes" : "Nothing left to alch" + skippedForProfit(config), config);
                    }
                    return;
                }
                if (Rs2Bank.isOpen()) {
                    closeBank();
                    return;
                }
                Candidate next = pick(candidates, config.order());
                if (config.moveUnderSpell() && moveUnderSpell(next)) return;
                alch(next);
            } catch (Exception ex) {
                log.error("[HighAlcher] Error in loop", ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS); // short delay: the 5-tick wait in alch() sets the pace
        return true;
    }

    /** One inventory item that may be alched, with what one alch of it earns compared to selling it. */
    private static final class Candidate {
        final Rs2ItemModel item;
        final int profitEach;
        /** GE price of one piece: the money tied up in it until it is alched. */
        final int gePrice;

        Candidate(Rs2ItemModel item, int profitEach, int gePrice) {
            this.item = item;
            this.profitEach = profitEach;
            this.gePrice = gePrice;
        }
    }

    private static List<Candidate> candidates(HighAlcherConfig config) {
        Set<String> keep = names(config.keepItems());
        int natureCost = price(ItemID.NATURERUNE);

        return Rs2Inventory.all(item -> !keep.contains(item.getName().toLowerCase())
                        && item.getId() != ItemID.NATURERUNE
                        && (!config.tradeableOnly() || item.isTradeable())
                        && item.getHaPrice() > 0)
                .stream()
                .map(item -> {
                    int ge = price(item.getId());
                    return new Candidate(item, item.getHaPrice() - ge - natureCost, ge);
                })
                .filter(c -> c.profitEach >= config.minProfitPerAlch())
                .collect(Collectors.toList());
    }

    /**
     * Items the min profit setting kept back, e.g. " (skipped: Inoculation bracelet -32 gp, min profit is 0)",
     * so a stop does not look like the item was not seen. Empty when none.
     */
    private static String skippedForProfit(HighAlcherConfig config) {
        Set<String> keep = names(config.keepItems());
        int natureCost = price(ItemID.NATURERUNE);
        String skipped = Rs2Inventory.all(item -> !keep.contains(item.getName().toLowerCase())
                        && item.getId() != ItemID.NATURERUNE
                        && (!config.tradeableOnly() || item.isTradeable())
                        && item.getHaPrice() > 0)
                .stream()
                .filter(item -> item.getHaPrice() - price(item.getId()) - natureCost < config.minProfitPerAlch())
                .map(item -> item.getName() + " " + (item.getHaPrice() - price(item.getId()) - natureCost) + " gp")
                .distinct()
                .collect(Collectors.joining(", "));
        return skipped.isEmpty() ? "" : " (skipped: " + skipped + ", min profit is " + config.minProfitPerAlch() + ")";
    }

    private Candidate pick(List<Candidate> candidates, HighAlcherConfig.Order order) {
        Candidate underSpell = null;
        if (alchSlot >= 0) {
            for (Candidate c : candidates) {
                if (c.item.getSlot() == alchSlot) underSpell = c;
            }
        }
        Comparator<Candidate> bySlot = Comparator.comparingInt(c -> c.item.getSlot());
        switch (order) {
            case BEST_PROFIT:
                return byValue(candidates, underSpell, Comparator.comparingInt((Candidate c) -> c.profitEach));
            case MOST_EXPENSIVE:
                return byValue(candidates, underSpell, Comparator.comparingInt((Candidate c) -> c.gePrice)
                        .thenComparingInt(c -> c.profitEach));
            case BOTTOM_UP:
                // Slot orders: whatever already sits under the spell goes first, otherwise they keep swapping items around
                return underSpell != null ? underSpell : candidates.stream().max(bySlot).orElse(null);
            default:
                return underSpell != null ? underSpell : candidates.stream().min(bySlot).orElse(null);
        }
    }

    /**
     * The best candidate by {@code value}. When the piece under the spell is the same item as the winner
     * (e.g. one of several unnoted rune platebodies), that piece is taken, so nothing is dragged for nothing.
     */
    private static Candidate byValue(List<Candidate> candidates, Candidate underSpell, Comparator<Candidate> value) {
        Candidate best = candidates.stream()
                .max(value.thenComparing(Comparator.comparingInt((Candidate c) -> c.item.getSlot()).reversed()))
                .orElse(null);
        if (best != null && underSpell != null && underSpell.item.getId() == best.item.getId()) return underSpell;
        return best;
    }

    /**
     * The classic alch setup: the item sits in the inventory slot right under the High Alchemy icon, so spell and
     * item are clicked at the same spot and the mouse barely moves. The spot is read from the spellbook once
     * (the magic tab is open after every cast) and matched to an inventory slot once the inventory is open.
     * Returns true when this loop was spent on finding the slot or dragging the item there.
     */
    private boolean moveUnderSpell(Candidate c) {
        if (alchPoint == null) {
            alchPoint = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget spell = Microbot.getClient().getWidget(MagicAction.HIGH_LEVEL_ALCHEMY.getWidgetId());
                if (spell == null || spell.isHidden()) return null;
                Rectangle b = spell.getBounds();
                return new java.awt.Point((int) b.getCenterX(), (int) b.getCenterY());
            }).orElse(null);
            // Spellbook not open yet: the first cast opens it, the slot is found before the second one
            if (alchPoint == null) return false;
        }
        if (alchSlot < 0) {
            if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
                Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                return true;
            }
            java.awt.Point p = alchPoint;
            alchSlot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget inventory = Microbot.getClient().getWidget(ComponentID.INVENTORY_CONTAINER);
                Widget[] slots = inventory == null ? null : inventory.getDynamicChildren();
                for (int i = 0; slots != null && i < slots.length; i++) {
                    if (slots[i] != null && slots[i].getBounds().contains(p)) return i;
                }
                return -1;
            }).orElse(-1);
            if (alchSlot < 0) {
                log.info("[HighAlcher] No inventory slot under the High Alchemy icon, alching from where items are");
                alchPoint = null;
                return false;
            }
            log.info("[HighAlcher] High Alchemy sits over inventory slot {}", alchSlot);
        }
        if (c.item.getSlot() == alchSlot) return false;

        Microbot.status = "Moving " + c.item.getName() + " under the spell";
        int slot = alchSlot;
        Rs2Inventory.moveItemToSlot(c.item, slot);
        sleepUntil(() -> {
            Rs2ItemModel there = Rs2Inventory.getItemInSlot(slot);
            return there != null && there.getId() == c.item.getId();
        }, 1200);
        return true;
    }

    /**
     * One cast, timed by game ticks: the game allows the next alch 5 ticks after the last one, so we wait for
     * exactly that tick and then click spell and item right away. Rs2Magic.alch() is not used because it adds
     * two fixed 300-600 ms waits per cast, which pushed every alch about 2 s past the 5-tick rhythm.
     * Counts the cast once the Magic XP goes up.
     */
    private void alch(Candidate c) {
        Rs2ItemModel item = c.item;
        currentItemId = item.getId();
        currentItemName = item.getName();
        currentProfitEach = c.profitEach;
        Microbot.status = "Alching " + item.getName();

        // Select the spell while the last alch is still running, so only the item click is left for the ready tick
        if (lastCastTick >= 0 && !sleepUntil(() -> tick() >= lastCastTick + ALCH_TICKS - PRESELECT_TICKS, 4000)) return;
        int xpBefore = magicXp();
        boolean selected = Rs2Magic.cast(MagicAction.HIGH_LEVEL_ALCHEMY);
        if (selected && lastCastTick >= 0 && !sleepUntil(() -> tick() >= lastCastTick + ALCH_TICKS, 2000)) return;

        boolean clicked = selected && Rs2Inventory.interact(item, "cast");
        if (clicked && sleepUntil(() -> magicXp() > xpBefore, 2400)) {
            lastCastTick = tick();
            alched++;
            profit += c.profitEach;
            lastAlchAt = System.currentTimeMillis();
            failedCasts = 0;
            return;
        }
        failedCasts++;
        log.info("[HighAlcher] Cast on {} gave no XP ({} in a row)", item.getName(), failedCasts);
        if (failedCasts >= MAX_FAILED_CASTS) {
            stop("Could not cast High Alchemy (fire runes, level 55 or standard spellbook?)", null);
        }
    }

    /** One bank step per loop: open, switch to notes, take out one kind of item, or give up. */
    private void restock(HighAlcherConfig config) {
        if (!Rs2Bank.isOpen()) {
            Microbot.status = "Opening bank";
            if (Rs2Bank.openBank()) sleepUntil(Rs2Bank::isOpen, 5000);
            return;
        }
        if (config.restockNatures() && !Rs2Inventory.hasItem(ItemID.NATURERUNE) && Rs2Bank.hasItem("Nature rune", true)) {
            Microbot.status = "Taking out nature runes";
            Rs2Bank.withdrawAll("Nature rune", true);
            sleepUntil(() -> Rs2Inventory.hasItem(ItemID.NATURERUNE), 2000);
            return;
        }
        if (!Rs2Inventory.hasItem(ItemID.NATURERUNE)) {
            stop("Out of nature runes, none in the bank either", config);
            return;
        }
        if (!Rs2Bank.hasWithdrawAsNote()) {
            Rs2Bank.setWithdrawAsNote();
            sleepUntil(Rs2Bank::hasWithdrawAsNote, 1200);
            return;
        }
        for (String name : names(config.restockItems())) {
            if (Rs2Bank.hasItem(name, false)) {
                Microbot.status = "Taking out " + name;
                Rs2Bank.withdrawAll(name, false);
                restocked = sleepUntil(() -> !candidates(config).isEmpty(), 2000) || restocked;
                return;
            }
        }
        stop(restocked ? "Restock items are all used up" : "None of the restock items are in the bank", config);
    }

    /** Click the X instead of Rs2Bank.closeBank(), which presses Escape when that game setting is on. */
    private void closeBank() {
        Microbot.status = "Closing bank";
        Rs2Widget.clickChildWidget(BANK_WINDOW, BANK_CLOSE_BUTTON);
        sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
    }

    private String stopReason(HighAlcherConfig config) {
        if (maxRuntimeMs > 0 && activeMs() >= maxRuntimeMs) {
            return "Max runtime of " + config.maxRuntimeMinutes() + " min reached";
        }
        if (config.maxCasts() > 0 && alched >= config.maxCasts()) {
            return config.maxCasts() + " alchs done";
        }
        if (config.maxLoss() > 0 && -profit >= config.maxLoss()) {
            return "Loss limit of " + config.maxLoss() + " gp reached";
        }
        if (config.targetLevel() > 0 && Experience.getLevelForXp(xp) >= config.targetLevel()) {
            return "Magic level " + config.targetLevel() + " reached";
        }
        return null;
    }

    private static Set<String> names(String list) {
        return Arrays.stream(list.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** GE guide price of one item; notes count as the item itself. */
    static int price(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int id = Microbot.getItemManager().canonicalize(itemId);
            return (long) Microbot.getItemManager().getItemPrice(id);
        }).orElse(0L).intValue();
    }

    private static int tick() {
        return Microbot.getClient().getTickCount();
    }

    private static int magicXp() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getSkillExperience(Skill.MAGIC)).orElse(0);
    }

    private void stop(String reason, HighAlcherConfig config) {
        log.info("[HighAlcher] {}, stopping", reason);
        Microbot.status = reason;
        // Desktop notification (RuneLite only shows it while the client is not focused) plus a notice above the chat
        Microbot.getNotifier().notify("High Alcher stopped: " + reason);
        notices.add("Stopped: " + reason, NoticeCenter.WARN);
        if (config != null && config.logoutOnStop()) Rs2Player.logout();
        // Only the bot stops; the plugin with its side panel stays on
        shutdown();
        Runnable listener = onStopped;
        if (listener != null) listener.run();
    }

    @javax.inject.Inject
    private NoticeCenter notices;

    /** Set by the plugin, so the panel's start button flips back when the bot stops by itself. */
    @lombok.Setter
    private volatile Runnable onStopped;
}
