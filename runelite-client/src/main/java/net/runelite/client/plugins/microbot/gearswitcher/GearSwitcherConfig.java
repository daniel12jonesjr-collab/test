package net.runelite.client.plugins.microbot.gearswitcher;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("gearswitcher")
public interface GearSwitcherConfig extends Config {

    /** Offensive prayers a set can turn on. Names match Microbot's Rs2PrayerEnum. */
    enum OffensivePrayer {
        NONE,
        // Melee
        PIETY, CHIVALRY, ULTIMATE_STRENGTH, INCREDIBLE_REFLEXES,
        // Ranged
        RIGOUR, EAGLE_EYE, DEAD_EYE, HAWK_EYE,
        // Magic
        AUGURY, MYSTIC_MIGHT, MYSTIC_VIGOUR, MYSTIC_LORE
    }

    @ConfigSection(name = "Set 1", description = "First gear set", position = 0)
    String set1 = "set1";

    @ConfigSection(name = "Set 2", description = "Second gear set", position = 1)
    String set2 = "set2";

    @ConfigSection(name = "Set 3", description = "Third gear set", position = 2)
    String set3 = "set3";

    @ConfigSection(name = "Auto protection prayer", description = "Switch protection prayers on monster attacks (PvM only)",
            position = 3, closedByDefault = true)
    String protection = "protection";

    // --- Set 1 ---

    @ConfigItem(keyName = "set1Hotkey", name = "Hotkey", description = "Key that equips set 1",
            position = 0, section = set1)
    default Keybind set1Hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(keyName = "set1Items", name = "Items", description = "Comma separated item names, e.g. Toxic blowpipe,Black d'hide body",
            position = 1, section = set1)
    default String set1Items() {
        return "";
    }

    @ConfigItem(keyName = "set1Prayer", name = "Offensive prayer", description = "Prayer turned on together with this set",
            position = 2, section = set1)
    default OffensivePrayer set1Prayer() {
        return OffensivePrayer.NONE;
    }

    // --- Set 2 ---

    @ConfigItem(keyName = "set2Hotkey", name = "Hotkey", description = "Key that equips set 2",
            position = 0, section = set2)
    default Keybind set2Hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(keyName = "set2Items", name = "Items", description = "Comma separated item names",
            position = 1, section = set2)
    default String set2Items() {
        return "";
    }

    @ConfigItem(keyName = "set2Prayer", name = "Offensive prayer", description = "Prayer turned on together with this set",
            position = 2, section = set2)
    default OffensivePrayer set2Prayer() {
        return OffensivePrayer.NONE;
    }

    // --- Set 3 ---

    @ConfigItem(keyName = "set3Hotkey", name = "Hotkey", description = "Key that equips set 3",
            position = 0, section = set3)
    default Keybind set3Hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(keyName = "set3Items", name = "Items", description = "Comma separated item names",
            position = 1, section = set3)
    default String set3Items() {
        return "";
    }

    @ConfigItem(keyName = "set3Prayer", name = "Offensive prayer", description = "Prayer turned on together with this set",
            position = 2, section = set3)
    default OffensivePrayer set3Prayer() {
        return OffensivePrayer.NONE;
    }

    // --- Auto protection prayer ---

    @ConfigItem(keyName = "autoProtection", name = "Enabled", description = "Turn protection prayers on automatically using the tables below",
            position = 0, section = protection)
    default boolean autoProtection() {
        return false;
    }

    @ConfigItem(keyName = "npcPrayers", name = "By monster name",
            description = "For monsters with one attack style. Format: name=STYLE, e.g. Abyssal demon=MELEE, Infernal mage=MAGIC. "
                    + "STYLE is MELEE, RANGE or MAGIC",
            position = 1, section = protection)
    default String npcPrayers() {
        return "";
    }

    @ConfigItem(keyName = "idPrayers", name = "By animation / projectile ID",
            description = "For monsters with several styles. Format: id=STYLE, e.g. 1234=MAGIC, 5678=RANGE. "
                    + "Find IDs with the RuneLite developer tools",
            position = 2, section = protection)
    default String idPrayers() {
        return "";
    }
}
