package net.runelite.client.plugins.microbot.enchanter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Stand next to a bank with runes (or a staff) and the jewellery in the bank. Each loop does one thing:
 * stop when the runtime is up, enchant while there is jewellery in the inventory, otherwise bank and take out more.
 */
@Slf4j
@Singleton // plugin and overlay must share one instance, otherwise the overlay reads a script that never started
public class EnchanterScript extends Script {

    /** Stop after this many casts in a row that did not go through (no runes, wrong spellbook, ...). */
    private static final int MAX_FAILED_CASTS = 3;
    /**
     * ONCE_PER_INVENTORY: the game enchants one piece every few seconds by itself.
     * Only when nothing changed for this long has it stopped, and we cast again.
     */
    private static final long AUTO_IDLE_MS = 8000;
    /** The bank window and the child index of its X button, the same ones Rs2Bank.closeBank() clicks. */
    private static final int BANK_WINDOW = 786434;
    private static final int BANK_CLOSE_BUTTON = 11;

    private int failedCasts;
    private int lastSeenCount;

    // Read by the overlay
    @Getter
    private volatile long startedAt;
    @Getter
    private volatile long maxRuntimeMs;
    @Getter
    private volatile int enchanted;
    @Getter
    private volatile long lastEnchantAt;
    @Getter
    private volatile int sourceItemId = -1;
    @Getter
    private volatile int productItemId = -1;
    /** ONCE_PER_INVENTORY: last time the number of unenchanted pieces went down. 0 = not started on this inventory. */
    private long lastProgressAt;
    private int lastCount;
    /** True once jewellery was in the inventory, so the pause happens once per finished inventory. */
    private boolean inventoryInProgress;
    /** Where the script was started. New spots are picked around it, so the bank never gets far away. */
    private WorldPoint home;

    public boolean run(EnchanterConfig config) {
        failedCasts = 0;
        inventoryInProgress = false;
        home = null;
        lastSeenCount = -1;
        enchanted = 0;
        lastEnchantAt = 0;
        productItemId = -1;
        startedAt = System.currentTimeMillis();
        maxRuntimeMs = TimeUnit.MINUTES.toMillis(config.maxRuntimeMinutes());
        lastProgressAt = 0;
        lastCount = -1;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (maxRuntimeMs > 0 && System.currentTimeMillis() - startedAt >= maxRuntimeMs) {
                    if (config.logoutOnStop()) Rs2Player.logout();
                    stop("Max runtime of " + config.maxRuntimeMinutes() + " min reached");
                    return;
                }
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (home == null) home = Rs2Player.getWorldLocation();

                String itemName = config.itemName();
                trackProgress(itemName);
                Rs2ItemModel item = nextPiece(itemName, config.clickOrder());
                if (item == null) {
                    lastProgressAt = 0;
                    // The last inventory is done: sometimes wait a few seconds or change spot before banking again
                    if (inventoryInProgress) {
                        inventoryInProgress = false;
                        if (Rs2Random.betweenInclusive(1, 100) <= config.pauseChance()) {
                            int min = config.pauseMinSeconds();
                            int seconds = Rs2Random.betweenInclusive(min, Math.max(min, config.pauseMaxSeconds()));
                            Microbot.status = "Pausing " + seconds + "s";
                            sleep(seconds * 1000);
                        }
                        if (Rs2Random.betweenInclusive(1, 100) <= config.moveChance()) {
                            changeSpot(config.moveRadius());
                        }
                        return;
                    }
                    restock(itemName);
                    return;
                }
                inventoryInProgress = true;
                if (Rs2Bank.isOpen()) {
                    // Click the X instead of Rs2Bank.closeBank(), which presses Escape when that game setting is on
                    Rs2Widget.clickChildWidget(BANK_WINDOW, BANK_CLOSE_BUTTON);
                    sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
                    return;
                }

                if (config.mode() == EnchanterConfig.Mode.ONCE_PER_INVENTORY && stillAutoEnchanting(itemName)) {
                    Microbot.status = "Enchanting by itself, " + lastCount + " left";
                    return;
                }
                if (enchant(config.spell().getAction(), item, itemName)) {
                    lastProgressAt = System.currentTimeMillis();
                    lastCount = Rs2Inventory.count(itemName);
                }
            } catch (Exception ex) {
                log.error("[Enchanter] Error in loop", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Counts enchanted pieces for the overlay: every drop in the number of unenchanted pieces outside the bank.
     * Also remembers the item ids, so the overlay can show icons and GE prices.
     */
    private void trackProgress(String itemName) {
        Rs2ItemModel source = Rs2Inventory.get(itemName, true);
        if (source != null) sourceItemId = source.getId();

        int count = Rs2Inventory.count(itemName);
        if (lastSeenCount >= 0 && count < lastSeenCount && !Rs2Bank.isOpen()) {
            enchanted += lastSeenCount - count;
            lastEnchantAt = System.currentTimeMillis();
            if (productItemId < 0) {
                Rs2Inventory.all(i -> !i.getName().equalsIgnoreCase(itemName)
                                && !i.getName().toLowerCase().endsWith(" rune"))
                        .stream().findFirst()
                        .ifPresent(product -> productItemId = product.getId());
            }
        }
        lastSeenCount = count;
    }

    /** True while the game is still working through the inventory after our last cast. */
    private boolean stillAutoEnchanting(String itemName) {
        if (lastProgressAt == 0) return false;
        long now = System.currentTimeMillis();
        int count = Rs2Inventory.count(itemName);
        if (count < lastCount) {
            lastCount = count;
            lastProgressAt = now;
        }
        return now - lastProgressAt < AUTO_IDLE_MS;
    }

    /** How many of the last pieces BOTTOM_UP_LOOSE picks from. */
    private static final int LOOSE_PICK_COUNT = 3;

    /** The piece that gets the next cast, by slot order. Null when there is none left. */
    private static Rs2ItemModel nextPiece(String itemName, EnchanterConfig.ClickOrder order) {
        List<Rs2ItemModel> pieces = Rs2Inventory.all(i -> i.getName().equalsIgnoreCase(itemName)).stream()
                .sorted(Comparator.comparingInt(Rs2ItemModel::getSlot))
                .collect(Collectors.toList());
        if (pieces.isEmpty()) return null;

        int last = pieces.size() - 1;
        switch (order) {
            case TOP_DOWN:
                return pieces.get(0);
            case BOTTOM_UP:
                return pieces.get(last);
            default:
                return pieces.get(last - Rs2Random.betweenInclusive(0, Math.min(LOOSE_PICK_COUNT, pieces.size()) - 1));
        }
    }

    /** One cast on one piece. True once that piece is enchanted. */
    private boolean enchant(MagicAction spell, Rs2ItemModel item, String itemName) {
        Microbot.status = "Enchanting " + itemName;
        int before = Rs2Inventory.count(itemName);
        // Selecting the spell opens the inventory, then a click on the item casts it
        boolean cast = Rs2Magic.cast(spell) && Rs2Inventory.interact(item, "cast")
                && sleepUntil(() -> Rs2Inventory.count(itemName) < before, 3000);
        if (cast) {
            failedCasts = 0;
            return true;
        }
        failedCasts++;
        if (failedCasts >= MAX_FAILED_CASTS) {
            stop("Could not cast " + spell.getName() + " (runes, staff, spellbook or level?)");
        }
        return false;
    }

    /** Walk to a random reachable tile around {@link #home}, at least two tiles from where we stand. */
    private void changeSpot(int radius) {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (home == null || here == null || radius < 2) return;
        List<WorldPoint> spots = Rs2Tile.getReachableTilesFromTile(home, radius).keySet().stream()
                .filter(p -> p.distanceTo(here) >= 2)
                .collect(Collectors.toList());
        if (spots.isEmpty()) return;

        WorldPoint target = spots.get(Rs2Random.betweenInclusive(0, spots.size() - 1));
        Microbot.status = "Changing spot";
        if (Rs2Walker.walkFastCanvas(target, false)) {
            sleepUntil(() -> target.equals(Rs2Player.getWorldLocation()), 6000);
        }
    }

    /** Deposit everything except runes, then take out all of the jewellery that is left. */
    private void restock(String itemName) {
        if (!Rs2Bank.isOpen()) {
            Microbot.status = "Opening bank";
            if (Rs2Bank.openBank()) sleepUntil(Rs2Bank::isOpen, 5000);
            return;
        }
        Microbot.status = "Banking";
        Rs2Bank.depositAllExcept(i -> i.getName().toLowerCase().endsWith(" rune"));
        sleepUntil(() -> Rs2Inventory.all(i -> !i.getName().toLowerCase().endsWith(" rune")).isEmpty(), 2000);

        if (!Rs2Bank.hasItem(itemName, true)) {
            stop("No " + itemName + " left in the bank");
            return;
        }
        Rs2Bank.withdrawAll(itemName, true);
        sleepUntil(() -> Rs2Inventory.hasItem(itemName, true), 3000);
    }

    private void stop(String reason) {
        log.info("[Enchanter] {}, stopping", reason);
        Microbot.status = reason;
        Microbot.getNotifier().notify("Enchanter stopped: " + reason);
        Microbot.stopPlugin(EnchanterPlugin.class);
    }
}
