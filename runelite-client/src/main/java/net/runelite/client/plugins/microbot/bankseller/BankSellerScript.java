package net.runelite.client.plugins.microbot.bankseller;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sells the bank in batches:
 * <pre>
 * BANK    fill the inventory with noted, untried items
 * SELL    put one offer into every free GE slot (3 on F2P worlds, 8 on members worlds)
 * WAIT    until every offer has sold or timed out
 * COLLECT abort what did not sell, collect everything to the bank
 *         -> SELL again while the inventory still holds items, otherwise BANK
 * </pre>
 * Every item is tried exactly once: its id goes into {@link #triedItemIds} before it is withdrawn,
 * so an item that did not sell and was returned to the bank is never picked again.
 */
@Slf4j
public class BankSellerScript extends Script {

    enum State {
        BANK,
        SELL,
        WAIT,
        COLLECT
    }

    private static final int F2P_SLOTS = 3;
    private static final int MEMBERS_SLOTS = 8;
    private static final int MAX_COLLECT_ATTEMPTS = 5;

    private final Set<Integer> triedItemIds = new HashSet<>();
    /** Unnoted item ids sitting in the inventory, waiting for a free GE slot. */
    private final Deque<Integer> pendingItems = new ArrayDeque<>();
    /** Unnoted item id -> time the offer was placed. */
    private final Map<Integer, Long> activeOffers = new LinkedHashMap<>();
    private final Map<Integer, String> itemNames = new HashMap<>();

    private BankSellerConfig config;
    private Set<String> excludedNames;
    private State state;
    private int collectAttempts;
    private int soldCount;
    private int unsoldCount;

    public boolean run(BankSellerConfig config) {
        this.config = config;
        this.excludedNames = Arrays.stream(config.excludedItems().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        triedItemIds.clear();
        pendingItems.clear();
        activeOffers.clear();
        itemNames.clear();
        state = State.BANK;
        soldCount = 0;
        unsoldCount = 0;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;

                switch (state) {
                    case BANK:
                        bank();
                        break;
                    case SELL:
                        sell();
                        break;
                    case WAIT:
                        waitForOffers();
                        break;
                    case COLLECT:
                        collect();
                        break;
                }
            } catch (Exception ex) {
                log.error("[BankSeller] Error in loop", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /** Empties the inventory and withdraws untried items as notes until the inventory is full. */
    private void bank() {
        if (Rs2GrandExchange.isOpen()) {
            Rs2GrandExchange.closeExchange();
            return;
        }
        if (!Rs2Bank.isOpen()) {
            Microbot.status = "Opening bank";
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 5000);
            return;
        }
        if (!Rs2Inventory.isEmpty()) {
            Microbot.status = "Depositing inventory";
            Rs2Bank.depositAll();
            return;
        }

        boolean membersWorld = Rs2Player.isInMemberWorld();
        // Bank slot order keeps the mouse moving tab by tab, row by row instead of jumping around
        List<Rs2ItemModel> candidates = Rs2Bank.bankItems().stream()
                .filter(item -> !triedItemIds.contains(item.getId()))
                .filter(item -> shouldSell(item, membersWorld))
                .sorted(Comparator.comparingInt(Rs2ItemModel::getSlot))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            finish();
            return;
        }
        if (!Rs2Bank.setWithdrawAsNote()) return;

        List<Rs2ItemModel> batch = planBatch(candidates, Rs2Inventory.emptySlotCount());
        // Mark as tried before anything can fail, so a broken item can never loop forever
        batch.forEach(item -> triedItemIds.add(item.getId()));

        // Click through the whole batch at a human pace without waiting for the server after each
        // click; the game queues the withdrawals and delivers them over the next ticks.
        for (Rs2ItemModel item : batch) {
            if (!isRunning()) return;
            Microbot.status = "Withdrawing " + item.getName();
            Rs2Bank.withdrawAll(item.getId());
            sleepFatigued(Rs2Random.logNormalBounded(110, 280));
        }

        // One wait for the whole batch. Items that never arrive just cost this one timeout.
        sleepUntil(() -> batch.stream().allMatch(item -> Rs2Inventory.hasItem(item.getName(), true)), 3000);
        for (Rs2ItemModel item : batch) {
            if (Rs2Inventory.hasItem(item.getName(), true)) {
                itemNames.put(item.getId(), item.getName());
                pendingItems.add(item.getId());
            } else {
                log.info("[BankSeller] Could not withdraw {}, skipping", item.getName());
            }
        }

        if (!pendingItems.isEmpty()) {
            state = State.SELL;
        }
    }

    /** Picks as many candidates as fit into the free inventory slots once withdrawn as notes. */
    private List<Rs2ItemModel> planBatch(List<Rs2ItemModel> candidates, int freeSlots) {
        List<Rs2ItemModel> batch = new ArrayList<>();
        for (Rs2ItemModel item : candidates) {
            if (freeSlots <= 0) break;
            ItemComposition composition = item.getItemComposition();
            boolean takesOneSlot = item.isStackable() || (composition != null && composition.getLinkedNoteId() != -1);
            batch.add(item);
            freeSlots -= takesOneSlot ? 1 : Math.min(item.getQuantity(), freeSlots);
        }
        return batch;
    }

    private boolean shouldSell(Rs2ItemModel item, boolean membersWorld) {
        if (item.getQuantity() <= 0) return false; // bank placeholder
        if (item.getId() == ItemID.COINS || item.getId() == ItemID.PLATINUM) return false;
        if (!item.isTradeable()) return false;
        if (excludedNames.contains(item.getName().toLowerCase())) return false;

        ItemComposition composition = item.getItemComposition();
        if (composition == null) return false;
        boolean membersItem = composition.isMembers();

        switch (config.itemFilter()) {
            case F2P_ONLY:
                return !membersItem;
            case P2P_ONLY:
                // Members items cannot be offered on a F2P world
                return membersItem && membersWorld;
            case AUTO_BY_WORLD:
            default:
                return membersWorld || !membersItem;
        }
    }

    /** Fills every free GE slot with one offer at the guide price, lowered by a single -5% click. */
    private void sell() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepGaussianFatigued(350, 100);
            return;
        }

        int worldSlots = Rs2Player.isInMemberWorld() ? MEMBERS_SLOTS : F2P_SLOTS;
        int freeSlots = Math.min(Rs2GrandExchange.getAvailableSlotsCount(), worldSlots - countUsedSlots());
        if (freeSlots <= 0) {
            Microbot.showMessage("Bank Seller: no free Grand Exchange slot. Free a slot and restart.");
            shutdown();
            return;
        }

        while (freeSlots > 0 && !pendingItems.isEmpty() && isRunning()) {
            int itemId = pendingItems.poll();
            String name = itemNames.get(itemId);
            int quantity = Rs2Inventory.itemQuantity(name, true);
            if (quantity <= 0) continue;

            Microbot.status = "Selling " + quantity + "x " + name;
            GrandExchangeRequest request = GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.SELL)
                    .itemName(name)
                    .exact(true)
                    .quantity(quantity)
                    .price(0)       // keep the price the GE suggests
                    .percent(-5)    // exactly one click on the -5% button
                    .toBank(true)
                    .build();

            if (Rs2GrandExchange.processOffer(request)) {
                activeOffers.put(itemId, System.currentTimeMillis());
                freeSlots--;
            } else {
                // The items stay in the inventory and get deposited on the next bank trip
                log.info("[BankSeller] Could not place offer for {}, skipping", name);
                unsoldCount++;
            }
            sleepGaussianFatigued(420, 130);
        }

        if (!activeOffers.isEmpty()) {
            state = State.WAIT;
        } else if (pendingItems.isEmpty()) {
            state = State.BANK;
        }
    }

    /** Waits until every offer of this round has either sold or run past the timeout. */
    private void waitForOffers() {
        long timeoutMs = config.sellTimeoutSeconds() * 1000L;
        long now = System.currentTimeMillis();

        boolean allDone = activeOffers.entrySet().stream().allMatch(entry -> {
            GrandExchangeOffer offer = findOffer(entry.getKey());
            return offer == null
                    || offer.getState() == GrandExchangeOfferState.SOLD
                    || now - entry.getValue() > timeoutMs;
        });
        if (!allDone) {
            Microbot.status = "Waiting for " + activeOffers.size() + " offers";
            return;
        }

        for (int itemId : activeOffers.keySet()) {
            GrandExchangeOffer offer = findOffer(itemId);
            if (offer != null && offer.getState() == GrandExchangeOfferState.SOLD) {
                soldCount++;
            } else {
                unsoldCount++;
                log.info("[BankSeller] {} did not sell in time, aborting", itemNames.get(itemId));
            }
        }
        collectAttempts = 0;
        state = State.COLLECT;
    }

    /** Aborts every offer that is still running, then collects coins and leftovers to the bank. */
    private void collect() {
        if (activeOffers.keySet().stream().allMatch(id -> findOffer(id) == null)) {
            activeOffers.clear();
            state = pendingItems.isEmpty() ? State.BANK : State.SELL;
            return;
        }
        if (++collectAttempts > MAX_COLLECT_ATTEMPTS) {
            Microbot.showMessage("Bank Seller: could not clear the Grand Exchange offers. Stopping.");
            shutdown();
            return;
        }

        Microbot.status = "Collecting";
        for (int itemId : activeOffers.keySet()) {
            GrandExchangeOffer offer = findOffer(itemId);
            if (offer != null && offer.getState() == GrandExchangeOfferState.SELLING) {
                Rs2GrandExchange.abortOffer(itemNames.get(itemId), true);
                sleepGaussianFatigued(380, 110);
            }
        }
        Rs2GrandExchange.collectAllToBank();
        // The next tick checks again whether all slots of this round are empty now
        sleepUntil(() -> activeOffers.keySet().stream().allMatch(id -> findOffer(id) == null), 3000);
    }

    /** The GE offer that holds the given (unnoted) item, or null if there is none. */
    private GrandExchangeOffer findOffer(int itemId) {
        for (GrandExchangeOffer offer : Microbot.getClient().getGrandExchangeOffers()) {
            if (offer != null && offer.getItemId() == itemId && offer.getState() != GrandExchangeOfferState.EMPTY) {
                return offer;
            }
        }
        return null;
    }

    private int countUsedSlots() {
        int used = 0;
        for (GrandExchangeOffer offer : Microbot.getClient().getGrandExchangeOffers()) {
            if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY) used++;
        }
        return used;
    }

    private void finish() {
        Microbot.status = "Done";
        Rs2Bank.closeBank();
        Microbot.showMessage("Bank Seller finished: " + soldCount + " sold, " + unsoldCount + " not sold.");
        shutdown();
    }
}
