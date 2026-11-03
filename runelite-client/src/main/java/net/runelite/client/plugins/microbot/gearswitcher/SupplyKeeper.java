package net.runelite.client.plugins.microbot.gearswitcher;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Keeps you fed and potted while you fight monsters. Checked every game tick, at most one eat or drink per
 * 3 ticks (the game's own food and potion delay), in this order:
 * <ol>
 *   <li>Eat when HP is at or below the threshold (always, also outside a fight).</li>
 *   <li>Super restore after a few brew sips, against the brew's stat drain.</li>
 *   <li>Prayer potion when prayer is low.</li>
 *   <li>Ranged, melee and Magic boosts when they are almost gone.</li>
 * </ol>
 * Steps 2-4 only while fighting a monster, so nothing is wasted while banking or walking.
 * The plugin does not call this at all while a player is involved.
 */
@Slf4j
@Singleton
class SupplyKeeper {

    private static final int ACTION_TICKS = 3;

    private final Client client;
    private final AtomicBoolean busy = new AtomicBoolean();
    private ExecutorService executor;
    private volatile int lastActionTick = -ACTION_TICKS;
    /** Brew sips since the last super restore. */
    private volatile int brewSips;

    @Inject
    SupplyKeeper(Client client) {
        this.client = client;
    }

    void start() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gearswitcher-supplies");
            t.setDaemon(true);
            return t;
        });
        brewSips = 0;
    }

    void stop() {
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    /** Client thread, every game tick. */
    void onTick(GearSwitcherConfig config) {
        ExecutorService ex = executor;
        if (ex == null || !config.autoSupplies()) return;
        int tick = client.getTickCount();
        if (tick - lastActionTick < ACTION_TICKS || !busy.compareAndSet(false, true)) return;

        boolean fighting = isFighting();
        int hpPercent = 100 * client.getBoostedSkillLevel(Skill.HITPOINTS)
                / Math.max(1, client.getRealSkillLevel(Skill.HITPOINTS));
        ex.submit(() -> {
            try {
                if (act(config, fighting, hpPercent)) lastActionTick = tick;
            } catch (Exception e) {
                log.debug("[GearSwitcher] Supplies failed", e);
            } finally {
                busy.set(false);
            }
        });
    }

    /** One eat or drink at most. True when something was used. */
    private boolean act(GearSwitcherConfig config, boolean fighting, int hpPercent) {
        if (config.eatAtPercent() > 0 && hpPercent <= config.eatAtPercent() && eat(config.foods())) return true;
        if (!fighting) return false;

        if (config.restoreAfterBrews() > 0 && brewSips >= config.restoreAfterBrews()) {
            Rs2ItemModel restore = first(List.of("super restore", "blighted super restore"));
            if (restore != null && Rs2Inventory.interact(restore, "Drink")) {
                brewSips = 0;
                return true;
            }
        }
        if (config.prayerAt() > 0 && Rs2Player.drinkPrayerPotionAt(config.prayerAt())) return true;
        if (config.boostRanged() && Rs2Player.drinkCombatPotionAt(Skill.RANGED)) return true;
        if (config.boostMelee()
                && (Rs2Player.drinkCombatPotionAt(Skill.STRENGTH) || Rs2Player.drinkCombatPotionAt(Skill.ATTACK))) {
            return true;
        }
        return config.boostMagic() && Rs2Player.drinkCombatPotionAt(Skill.MAGIC);
    }

    /** The first food of the list you carry; brews are drunk and counted. Empty list = any food. */
    private boolean eat(String foodList) {
        List<String> order = Arrays.stream(foodList.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (order.isEmpty()) return Rs2Player.useFood();

        Rs2ItemModel food = first(order);
        if (food == null) return Rs2Player.useFood();
        boolean brew = food.getName().toLowerCase().contains("brew");
        boolean used = Rs2Inventory.interact(food, brew ? "Drink" : "Eat");
        if (used && brew) brewSips++;
        return used;
    }

    /**
     * First unnoted inventory item whose name starts with one of {@code prefixes}, in list order.
     * Prefixes so that "Saradomin brew" also finds "Saradomin brew(3)".
     */
    private static Rs2ItemModel first(List<String> prefixes) {
        List<Rs2ItemModel> items = Rs2Inventory.all(i -> !i.isNoted());
        for (String prefix : prefixes) {
            for (Rs2ItemModel item : items) {
                if (item.getName().toLowerCase().startsWith(prefix)) return item;
            }
        }
        return null;
    }

    /** Client thread: we target a monster, or a monster targets us. */
    private boolean isFighting() {
        Player local = client.getLocalPlayer();
        if (local == null) return false;
        if (local.getInteracting() instanceof NPC) return true;
        for (NPC npc : client.getTopLevelWorldView().npcs()) {
            if (npc.getInteracting() == local) return true;
        }
        return false;
    }
}
