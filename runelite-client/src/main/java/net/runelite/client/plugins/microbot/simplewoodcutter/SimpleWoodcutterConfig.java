package net.runelite.client.plugins.microbot.simplewoodcutter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("simplewoodcutter")
public interface SimpleWoodcutterConfig extends Config {

    @ConfigItem(
            keyName = "treeName",
            name = "Tree name",
            description = "Name of the tree to chop, e.g. Tree, Oak tree, Willow tree",
            position = 0
    )
    default String treeName() {
        return "Tree";
    }

    @ConfigItem(
            keyName = "logName",
            name = "Log name",
            description = "Name of the logs to drop when the inventory is full",
            position = 1
    )
    default String logName() {
        return "Logs";
    }

    @ConfigItem(
            keyName = "searchRadius",
            name = "Search radius",
            description = "How many tiles around the player to look for trees",
            position = 2
    )
    default int searchRadius() {
        return 10;
    }

    @ConfigItem(
            keyName = "maxRuntimeMinutes",
            name = "Max runtime (min)",
            description = "Stop the script after this many minutes. 0 = no limit",
            position = 3
    )
    default int maxRuntimeMinutes() {
        return 0;
    }

    @ConfigItem(
            keyName = "logoutOnStop",
            name = "Logout when time is up",
            description = "Log out once the max runtime is reached",
            position = 4
    )
    default boolean logoutOnStop() {
        return true;
    }

    @ConfigItem(
            keyName = "pauseChance",
            name = "Pause chance (%)",
            description = "Chance to wait a moment after a tree is done before clicking the next one. 0 = never",
            position = 5
    )
    default int pauseChance() {
        return 30;
    }

    @ConfigItem(
            keyName = "pauseMinSeconds",
            name = "Pause min (s)",
            description = "Shortest pause after a tree",
            position = 6
    )
    default int pauseMinSeconds() {
        return 5;
    }

    @ConfigItem(
            keyName = "pauseMaxSeconds",
            name = "Pause max (s)",
            description = "Longest pause after a tree",
            position = 7
    )
    default int pauseMaxSeconds() {
        return 10;
    }

    @ConfigItem(
            keyName = "threeTick",
            name = "3-tick (experimental)",
            description = "Knife on log + tree click every 3 game ticks. Needs a knife and at least one of the logs "
                    + "from 'Log name' in the inventory, works best on teak or mahogany. Random pauses are skipped.",
            position = 8
    )
    default boolean threeTick() {
        return false;
    }

    enum ThreeTickMethod {
        /** Guam leaf (or another low herb) on swamp tar, pestle and mortar in the inventory. Uses up one herb per cycle. */
        HERB_ON_TAR,
        /** Knife on teak or mahogany logs. Uses up nothing. */
        KNIFE_ON_LOG
    }

    @ConfigItem(
            keyName = "threeTickMethod",
            name = "3-tick method",
            description = "HERB_ON_TAR: herb + swamp tar + pestle and mortar. KNIFE_ON_LOG: knife + one log",
            position = 10
    )
    default ThreeTickMethod threeTickMethod() {
        return ThreeTickMethod.HERB_ON_TAR;
    }

    @ConfigItem(
            keyName = "herbName",
            name = "3-tick herb",
            description = "Clean herb used on the swamp tar, guam to irit",
            position = 11
    )
    default String herbName() {
        return "Guam leaf";
    }

    /** MIXED switches between the other patterns; the rest are Microbot's built-in drop orders. */
    enum DropPattern {
        MIXED,
        STANDARD,
        EFFICIENT_ROW,
        COLUMN,
        EFFICIENT_COLUMN,
        ZIGZAG,
        RANDOM
    }

    @ConfigItem(
            keyName = "dropPattern",
            name = "Drop order",
            description = "Order in which logs are dropped. MIXED picks a different pattern each time the inventory is full",
            position = 12
    )
    default DropPattern dropPattern() {
        return DropPattern.MIXED;
    }

    @ConfigItem(
            keyName = "dropEverything",
            name = "Drop everything",
            description = "When the inventory is full, drop all items (nests, seeds, ...), not just logs. "
                    + "Axes, the knife and the 3-tick items are always kept",
            position = 13
    )
    default boolean dropEverything() {
        return true;
    }

    @ConfigItem(
            keyName = "neverDrop",
            name = "Never drop",
            description = "Comma separated item names that are always kept, e.g. Coins,Bird nest",
            position = 14
    )
    default String neverDrop() {
        return "Coins";
    }

    @ConfigItem(
            keyName = "stuckSeconds",
            name = "Stop if stuck (s)",
            description = "Turn the script off when the player has not chopped, walked or gained a log for this long. "
                    + "Random pauses and Break Handler breaks do not count. 0 = off",
            position = 9
    )
    default int stuckSeconds() {
        return 10;
    }
}
