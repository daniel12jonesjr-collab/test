package net.runelite.client.plugins.microbot.highalcher;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("highalcher")
public interface HighAlcherConfig extends Config {

    @ConfigSection(name = "What to alch", description = "Which items get alched", position = 0)
    String items = "items";

    @ConfigSection(name = "Restock", description = "Take more items out of the bank when the inventory is done", position = 1)
    String restock = "restock";

    @ConfigSection(name = "When to stop", description = "Stop conditions", position = 2)
    String stopping = "stopping";

    @ConfigSection(name = "Display", description = "Overlay size and the notices above the chatbox", position = 3)
    String display = "display";

    @ConfigSection(name = "Market: alch finder", description = "Which items the side panel lists as worth alching",
            position = 4, closedByDefault = true)
    String market = "market";

    @ConfigSection(name = "Market: alerts", description = "Notifications from the side panel", position = 5,
            closedByDefault = true)
    String alerts = "alerts";

    // ---------------------------------------------------------------- What to alch

    @ConfigItem(
            keyName = "keepItems",
            name = "Never alch",
            description = "Comma separated item names that are never alched. Everything else in the inventory gets alched",
            position = 0,
            section = items
    )
    default String keepItems() {
        return "Nature rune,Fire rune,Coins,Rune pouch,Divine rune pouch";
    }

    @ConfigItem(
            keyName = "minProfitPerAlch",
            name = "Min profit per alch (gp)",
            description = "Alch value minus GE price minus a nature rune. Items below this stay in the inventory. "
                    + "0 = never at a loss, negative values allow a small loss for the XP",
            position = 1,
            section = items
    )
    @Range(min = -100_000)
    default int minProfitPerAlch() {
        return 0;
    }

    @ConfigItem(
            keyName = "tradeableOnly",
            name = "Tradeable items only",
            description = "Never alch untradeable items, so quest items and the like stay safe",
            position = 2,
            section = items
    )
    default boolean tradeableOnly() {
        return true;
    }

    enum Order {
        /** First slot first, top left to bottom right. */
        TOP_DOWN,
        /** Last slot first, bottom right to top left. */
        BOTTOM_UP,
        /** The item with the best profit per alch first. */
        BEST_PROFIT,
        /** The item with the highest GE price first, so the most money gets turned back into coins first. */
        MOST_EXPENSIVE
    }

    @ConfigItem(
            keyName = "moveUnderSpell",
            name = "Move items under the spell",
            description = "Drag the next item into the inventory slot under the High Alchemy icon, "
                    + "so spell and item are clicked at the same spot. Works best with noted items",
            position = 4,
            section = items
    )
    default boolean moveUnderSpell() {
        return true;
    }

    @ConfigItem(
            keyName = "order",
            name = "Alch order",
            description = "Which item gets the next cast. TOP_DOWN / BOTTOM_UP go by slot, BEST_PROFIT by profit per alch, "
                    + "MOST_EXPENSIVE by GE price (frees up the most money first)",
            position = 3,
            section = items
    )
    default Order order() {
        return Order.TOP_DOWN;
    }

    // ---------------------------------------------------------------- Restock

    @ConfigItem(
            keyName = "restockEnabled",
            name = "Restock from bank",
            description = "When nothing is left to alch, open the nearest bank and take out the items below as notes. "
                    + "Start next to a bank",
            position = 0,
            section = restock
    )
    default boolean restockEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "restockItems",
            name = "Restock items",
            description = "Comma separated item names to take out of the bank, e.g. Rune platebody,Black d'hide body",
            position = 1,
            section = restock
    )
    default String restockItems() {
        return "";
    }

    @ConfigItem(
            keyName = "restockNatures",
            name = "Take out natures",
            description = "Also take all nature runes out of the bank when restocking",
            position = 2,
            section = restock
    )
    default boolean restockNatures() {
        return true;
    }

    // ---------------------------------------------------------------- When to stop

    @ConfigItem(
            keyName = "targetLevel",
            name = "Stop at Magic level",
            description = "Stop once this Magic level is reached. 0 = off",
            position = 0,
            section = stopping
    )
    default int targetLevel() {
        return 0;
    }

    @ConfigItem(
            keyName = "maxLoss",
            name = "Stop at total loss (gp)",
            description = "Stop once the alchs so far lost this much in total compared to selling on the GE. 0 = off",
            position = 1,
            section = stopping
    )
    default int maxLoss() {
        return 0;
    }

    @ConfigItem(
            keyName = "maxCasts",
            name = "Stop after casts",
            description = "Stop after this many alchs. 0 = no limit",
            position = 2,
            section = stopping
    )
    default int maxCasts() {
        return 0;
    }

    @ConfigItem(
            keyName = "maxRuntimeMinutes",
            name = "Max runtime (min)",
            description = "Stop after this many minutes. 0 = no limit",
            position = 3,
            section = stopping
    )
    default int maxRuntimeMinutes() {
        return 0;
    }

    @ConfigItem(
            keyName = "logoutOnStop",
            name = "Logout when done",
            description = "Log out when the script stops for any of the reasons above",
            position = 4,
            section = stopping
    )
    default boolean logoutOnStop() {
        return false;
    }

    // ---------------------------------------------------------------- Display

    enum OverlayStyle {
        /** The full stats card. */
        FULL,
        /** One slim line with the key numbers and the level bar. */
        COMPACT,
        /** No overlay. */
        HIDDEN
    }

    @ConfigItem(keyName = "overlayStyle", name = "Overlay",
            description = "FULL stats card, COMPACT slim bar, or HIDDEN", position = 0, section = display)
    default OverlayStyle overlayStyle() {
        return OverlayStyle.FULL;
    }

    @ConfigItem(keyName = "showNotices", name = "Notices above the chat",
            description = "Show High Alcher messages as small notices above the chatbox instead of chat lines. "
                    + "All of them are also kept in the Meldungen tab of the side panel",
            position = 1, section = display)
    default boolean showNotices() {
        return true;
    }

    @Range(min = 3, max = 120)
    @ConfigItem(keyName = "noticeSeconds", name = "Notice visible for", description = "How long a notice stays on screen",
            position = 2, section = display)
    @Units(Units.SECONDS)
    default int noticeSeconds() {
        return 12;
    }

    // ---------------------------------------------------------------- Market: alch finder

    @ConfigItem(keyName = "marketMinProfit", name = "Min profit per alch (gp)",
            description = "Items in the Alch tab earn at least this much per alch, after buying and the nature rune",
            position = 0, section = market)
    @Units(" gp")
    default int marketMinProfit() {
        return 200;
    }

    @ConfigItem(keyName = "marketMinVolume", name = "Min buys per hour",
            description = "Makes sure the item is actually traded enough to buy it", position = 1, section = market)
    default int marketMinVolume() {
        return 10;
    }

    @ConfigItem(keyName = "maxBuyPrice", name = "Max buy price (0 = any)", description = "Your budget per item",
            position = 2, section = market)
    @Units(" gp")
    default int maxBuyPrice() {
        return 0;
    }

    @ConfigItem(keyName = "includeMembers", name = "Members items", description = "", position = 3, section = market)
    default boolean includeMembers() {
        return true;
    }

    @Range(min = 1, max = 120)
    @ConfigItem(keyName = "maxPriceAge", name = "Max price age", description = "Older prices are ignored",
            position = 4, section = market)
    @Units(Units.MINUTES)
    default int maxPriceAge() {
        return 15;
    }

    @Range(min = 30, max = 600)
    @ConfigItem(keyName = "refreshSeconds", name = "Refresh every", description = "", position = 5, section = market)
    @Units(Units.SECONDS)
    default int refreshSeconds() {
        return 60;
    }

    @Range(min = 10, max = 200)
    @ConfigItem(keyName = "maxRows", name = "Max rows in the panel", description = "", position = 6, section = market)
    default int maxRows() {
        return 40;
    }

    // ---------------------------------------------------------------- Market: alerts

    @ConfigItem(keyName = "notifyLimitFree", name = "Buy limit free again",
            description = "Tell me when the 4-hour buy limit of a bought item is free again (Käufe tab)",
            position = 0, section = alerts)
    default boolean notifyLimitFree() {
        return true;
    }

    @ConfigItem(keyName = "notifyDesktop", name = "New alch items: desktop",
            description = "Desktop notification when a new item worth alching shows up", position = 1, section = alerts)
    default boolean notifyDesktop() {
        return false;
    }

    @ConfigItem(keyName = "notifyChat", name = "New alch items: notice",
            description = "Notice above the chatbox when a new item worth alching shows up", position = 2, section = alerts)
    default boolean notifyChat() {
        return true;
    }

    @Range(min = 1, max = 10)
    @ConfigItem(keyName = "maxAlertsPerUpdate", name = "Max alerts per update", description = "Against spam",
            position = 3, section = alerts)
    default int maxAlertsPerUpdate() {
        return 3;
    }

    @Range(min = 1, max = 240)
    @ConfigItem(keyName = "alertCooldown", name = "Same item again after", description = "",
            position = 4, section = alerts)
    @Units(Units.MINUTES)
    default int alertCooldown() {
        return 30;
    }
}
