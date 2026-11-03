package net.runelite.client.plugins.microbot.simplewoodcutter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Minimal power-chopping loop. Each 600ms tick re-reads the game state and does one thing:
 * drop logs if the inventory is full, otherwise click the nearest tree if the player is idle.
 */
@Slf4j
@Singleton // plugin and overlay must share one instance, otherwise the overlay reads a script that never started
public class SimpleWoodcutterScript extends Script {

    /** True while the player is busy with a tree we clicked; flips back once they go idle. */
    private boolean treeInProgress;
    /** Game tick of the last 3-tick cycle, -1 to start a new rhythm on the next tick. */
    private int lastCycleTick;
    /** Watchdog: last time the player chopped, walked or the inventory changed. */
    private long lastProgressAt;
    private int lastInventoryCount;
    /** Read by the overlay to show the runtime. */
    @Getter
    private volatile long startedAt;
    @Getter
    private volatile long maxRuntimeMs;

    public boolean run(SimpleWoodcutterConfig config) {
        treeInProgress = false;
        lastCycleTick = -1;
        lastProgressAt = System.currentTimeMillis();
        lastInventoryCount = -1;
        startedAt = System.currentTimeMillis();
        maxRuntimeMs = TimeUnit.MINUTES.toMillis(config.maxRuntimeMinutes());

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // Wall-clock limit: time spent in Break Handler breaks counts as well
                if (maxRuntimeMs > 0 && System.currentTimeMillis() - startedAt >= maxRuntimeMs) {
                    stopAfterMaxRuntime(config);
                    return;
                }
                // Breaks, pauses and being logged out are not "stuck", so the watchdog restarts its clock
                if (!super.run() || !Microbot.isLoggedIn()) {
                    lastProgressAt = System.currentTimeMillis();
                    return;
                }
                if (isStuck(config)) {
                    log.info("[SimpleWoodcutter] No progress for {}s, stopping", config.stuckSeconds());
                    Microbot.status = "Stuck, stopped";
                    Microbot.stopPlugin(SimpleWoodcutterPlugin.class);
                    return;
                }

                if (config.threeTick()) {
                    threeTickStep(config);
                    return;
                }

                if (Rs2Inventory.isFull()) {
                    Microbot.status = "Dropping logs";
                    Rs2Inventory.dropAll(droppable(config, null), dropOrder(config));
                    sleepUntil(() -> !Rs2Inventory.isFull(), 10000);
                    return;
                }

                // Still chopping or walking to a tree: wait for the next tick
                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) return;

                // The last tree is done: sometimes wait a few seconds before starting the next one
                if (treeInProgress) {
                    treeInProgress = false;
                    if (Rs2Random.between(1, 100) <= config.pauseChance()) {
                        int min = config.pauseMinSeconds();
                        int seconds = Rs2Random.between(min, Math.max(min, config.pauseMaxSeconds()));
                        Microbot.status = "Pausing " + seconds + "s";
                        sleep(seconds * 1000);
                        lastProgressAt = System.currentTimeMillis(); // our own pause is not being stuck
                        return;
                    }
                }

                Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query()
                        .withName(config.treeName())
                        .nearestReachableOnClientThread(config.searchRadius());

                if (tree == null) {
                    Microbot.status = "No tree found";
                    return;
                }

                Microbot.status = "Chopping " + config.treeName();
                if (tree.click("Chop down")) {
                    // Wait until the chop animation starts; if it never does, the next tick retries
                    treeInProgress = sleepUntil(Rs2Player::isAnimating, 5000);
                }
            } catch (Exception ex) {
                log.error("[SimpleWoodcutter] Error in loop", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * One 3-tick cycle: on the right game tick, use the knife on a log (this sets the skilling timer to 3)
     * and click the tree in the same tick, so the next woodcutting roll comes after 3 ticks instead of 4.
     * Both clicks have to land inside one 600ms tick, so slow mouse settings will make it miss cycles.
     */
    private void threeTickStep(SimpleWoodcutterConfig config) {
        String logName = config.logName();
        boolean knifeMethod = config.threeTickMethod() == SimpleWoodcutterConfig.ThreeTickMethod.KNIFE_ON_LOG;

        if (Rs2Inventory.isFull()) {
            // The knife trick needs one log to stay in the inventory, the herb trick does not
            Rs2ItemModel keep = knifeMethod ? Rs2Inventory.get(logName, true) : null;
            Microbot.status = "Dropping logs";
            Rs2Inventory.dropAll(droppable(config, keep), dropOrder(config));
            lastCycleTick = -1;
            return;
        }

        // Exact names, so e.g. "Guam leaf" never picks a "Grimy guam leaf"
        Rs2ItemModel first = knifeMethod ? Rs2Inventory.get("Knife", true) : Rs2Inventory.get(config.herbName(), true);
        Rs2ItemModel second = knifeMethod ? Rs2Inventory.get(logName, true) : Rs2Inventory.get("Swamp tar", true);
        boolean ready = first != null && second != null
                && (knifeMethod || Rs2Inventory.hasItem("Pestle and mortar", true));
        if (!ready) {
            String needed = knifeMethod
                    ? "a knife and one " + logName
                    : "pestle and mortar, swamp tar and " + config.herbName();
            log.info("[SimpleWoodcutter] 3-tick needs {}, stopping", needed);
            Microbot.status = "3-tick needs " + needed;
            Microbot.stopPlugin(SimpleWoodcutterPlugin.class);
            return;
        }

        // Find the tree before waiting, so the tick itself is only spent on the two clicks
        Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query()
                .withName(config.treeName())
                .nearestReachableOnClientThread(config.searchRadius());
        if (tree == null) {
            Microbot.status = "No tree found";
            return;
        }

        int targetTick = lastCycleTick < 0 ? currentTick() + 1 : lastCycleTick + 3;
        if (!sleepUntil(() -> currentTick() >= targetTick, 2500)) return;

        lastCycleTick = currentTick();
        Microbot.status = "3-tick " + config.treeName();
        Rs2Inventory.combine(first, second);
        tree.click("Chop down");
    }

    /** True once the player has neither chopped, walked nor gained or dropped an item for too long. */
    private boolean isStuck(SimpleWoodcutterConfig config) {
        long now = System.currentTimeMillis();
        int inventoryCount = Rs2Inventory.count();
        if (Rs2Player.isAnimating() || Rs2Player.isMoving() || inventoryCount != lastInventoryCount) {
            lastProgressAt = now;
            lastInventoryCount = inventoryCount;
            return false;
        }
        return config.stuckSeconds() > 0 && now - lastProgressAt > config.stuckSeconds() * 1000L;
    }

    /**
     * What goes when the inventory is full. Only logs, or with "Drop everything" also nests, seeds and other
     * loot. Tools (axe, knife, 3-tick items), the "Never drop" list and {@code keep} always stay.
     */
    private static Predicate<Rs2ItemModel> droppable(SimpleWoodcutterConfig config, Rs2ItemModel keep) {
        Set<String> neverDrop = Arrays.stream(config.neverDrop().split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        String herb = config.herbName().toLowerCase();

        return item -> {
            if (keep != null && item.getSlot() == keep.getSlot()) return false;
            String name = item.getName().toLowerCase();
            if (!config.dropEverything()) return name.equals(config.logName().toLowerCase());
            boolean tool = name.endsWith("axe") // bronze axe ... dragon felling axe
                    || name.equals("knife") || name.equals("pestle and mortar")
                    || name.equals("swamp tar") || name.equals(herb);
            return !tool && !neverDrop.contains(name);
        };
    }

    /** MIXED picks one of Microbot's drop patterns at random for every full inventory. */
    private static InteractOrder dropOrder(SimpleWoodcutterConfig config) {
        SimpleWoodcutterConfig.DropPattern pattern = config.dropPattern();
        return pattern == SimpleWoodcutterConfig.DropPattern.MIXED
                ? InteractOrder.random()
                : InteractOrder.valueOf(pattern.name());
    }

    private static int currentTick() {
        return Microbot.getClient().getTickCount();
    }

    private void stopAfterMaxRuntime(SimpleWoodcutterConfig config) {
        log.info("[SimpleWoodcutter] Max runtime of {} min reached, stopping", config.maxRuntimeMinutes());
        Microbot.status = "Max runtime reached";
        if (config.logoutOnStop()) {
            Rs2Player.logout();
        }
        // Turns the plugin off in the panel too, which calls shutdown() on this script
        Microbot.stopPlugin(SimpleWoodcutterPlugin.class);
    }
}
