package net.runelite.client.plugins.microbot.enchanter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

@ConfigGroup("enchanter")
public interface EnchanterConfig extends Config {

    @Getter
    @RequiredArgsConstructor
    enum Spell {
        LVL_1_SAPPHIRE(MagicAction.ENCHANT_SAPPHIRE_JEWELLERY),
        LVL_2_EMERALD(MagicAction.ENCHANT_EMERALD_JEWELLERY),
        LVL_3_RUBY(MagicAction.ENCHANT_RUBY_JEWELLERY),
        LVL_4_DIAMOND(MagicAction.ENCHANT_DIAMOND_JEWELLERY),
        LVL_5_DRAGONSTONE(MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY),
        LVL_6_ONYX(MagicAction.ENCHANT_ONYX_JEWELLERY),
        LVL_7_ZENYTE(MagicAction.ENCHANT_ZENYTE_JEWELLERY);

        private final MagicAction action;
    }

    @ConfigItem(
            keyName = "spell",
            name = "Spell",
            description = "Enchant spell to cast. Standard spellbook, runes in the inventory or an elemental staff",
            position = 0
    )
    default Spell spell() {
        return Spell.LVL_1_SAPPHIRE;
    }

    @ConfigItem(
            keyName = "itemName",
            name = "Item to enchant",
            description = "Exact name of the jewellery in the bank, e.g. Sapphire ring, Dragonstone amulet",
            position = 1
    )
    default String itemName() {
        return "Sapphire ring";
    }

    enum Mode {
        /** Casts on every piece: fastest. */
        CLICK_EACH,
        /** Casts once per inventory and lets the game enchant the rest by itself: slower, far fewer clicks. */
        ONCE_PER_INVENTORY
    }

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "CLICK_EACH casts on every piece (fast). ONCE_PER_INVENTORY casts once and lets the game "
                    + "carry on through the inventory (slower, fewer clicks)",
            position = 2
    )
    default Mode mode() {
        return Mode.ONCE_PER_INVENTORY;
    }

    enum ClickOrder {
        /** First slot first, top left to bottom right. */
        TOP_DOWN,
        /** Last slot first, bottom right to top left. */
        BOTTOM_UP,
        /** One of the last few pieces at random, so it still works its way up from the bottom. */
        BOTTOM_UP_LOOSE
    }

    @ConfigItem(
            keyName = "clickOrder",
            name = "Click order",
            description = "Which piece gets the next cast. BOTTOM_UP starts at the bottom right, "
                    + "BOTTOM_UP_LOOSE picks one of the last 3 pieces at random",
            position = 3
    )
    default ClickOrder clickOrder() {
        return ClickOrder.BOTTOM_UP_LOOSE;
    }

    @ConfigItem(
            keyName = "maxRuntimeMinutes",
            name = "Max runtime (min)",
            description = "Stop after this many minutes. 0 = no limit",
            position = 4
    )
    default int maxRuntimeMinutes() {
        return 50;
    }

    @ConfigItem(
            keyName = "logoutOnStop",
            name = "Logout when time is up",
            description = "Log out once the max runtime is reached",
            position = 5
    )
    default boolean logoutOnStop() {
        return true;
    }

    @ConfigItem(
            keyName = "pauseChance",
            name = "Pause chance (%)",
            description = "Chance to wait a moment after an inventory is done before banking for the next one. 0 = never",
            position = 6
    )
    default int pauseChance() {
        return 30;
    }

    @ConfigItem(
            keyName = "pauseMinSeconds",
            name = "Pause min (s)",
            description = "Shortest pause after an inventory",
            position = 7
    )
    default int pauseMinSeconds() {
        return 5;
    }

    @ConfigItem(
            keyName = "pauseMaxSeconds",
            name = "Pause max (s)",
            description = "Longest pause after an inventory",
            position = 8
    )
    default int pauseMaxSeconds() {
        return 10;
    }

    @ConfigItem(
            keyName = "moveChance",
            name = "Change spot chance (%)",
            description = "Chance to walk to another spot near the starting tile after an inventory is done. 0 = never",
            position = 9
    )
    default int moveChance() {
        return 15;
    }

    @ConfigItem(
            keyName = "moveRadius",
            name = "Change spot radius",
            description = "How many tiles away from the starting tile a new spot may be. Keep it small so the bank stays in reach",
            position = 10
    )
    default int moveRadius() {
        return 4;
    }
}
