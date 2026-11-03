package net.runelite.client.plugins.microbot.gearswitcher;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Units;

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

    /**
     * Every offensive spell that can be cast on a monster (Lunar has none). Names match MagicAction.
     * The spell needs its own spellbook to be active. Left out on purpose: Tele Block and Tele Other,
     * which only work on players.
     */
    enum Spell {
        NONE,
        // ---- Standard spellbook ----
        // Strike, Bolt, Blast, Wave, Surge
        WIND_STRIKE, WATER_STRIKE, EARTH_STRIKE, FIRE_STRIKE,
        WIND_BOLT, WATER_BOLT, EARTH_BOLT, FIRE_BOLT,
        WIND_BLAST, WATER_BLAST, EARTH_BLAST, FIRE_BLAST,
        WIND_WAVE, WATER_WAVE, EARTH_WAVE, FIRE_WAVE,
        WIND_SURGE, WATER_SURGE, EARTH_SURGE, FIRE_SURGE,
        // Binds
        BIND, SNARE, ENTANGLE,
        // Curses
        CONFUSE, WEAKEN, CURSE, VULNERABILITY, ENFEEBLE, STUN,
        // Special
        CRUMBLE_UNDEAD, IBAN_BLAST, MAGIC_DART, SARADOMIN_STRIKE, CLAWS_OF_GUTHIX, FLAMES_OF_ZAMORAK,
        // ---- Ancient Magicks ----
        SMOKE_RUSH, SHADOW_RUSH, BLOOD_RUSH, ICE_RUSH,
        SMOKE_BURST, SHADOW_BURST, BLOOD_BURST, ICE_BURST,
        SMOKE_BLITZ, SHADOW_BLITZ, BLOOD_BLITZ, ICE_BLITZ,
        SMOKE_BARRAGE, SHADOW_BARRAGE, BLOOD_BARRAGE, ICE_BARRAGE,
        // ---- Arceuus ----
        GHOSTLY_GRASP, SKELETAL_GRASP, UNDEAD_GRASP,
        INFERIOR_DEMONBANE, SUPERIOR_DEMONBANE, DARK_DEMONBANE
    }

    // Sets are edited in the side panel only, so their items are hidden here
    String set1 = "set1";
    String set2 = "set2";
    String set3 = "set3";

    @ConfigSection(name = "Supplies", description = "Eat and drink by itself while fighting monsters (PvM only)",
            position = 4, closedByDefault = true)
    String supplies = "supplies";

    @ConfigSection(name = "Auto protection prayer", description = "Switch protection prayers on monster attacks (PvM only)",
            position = 3, closedByDefault = true)
    String protection = "protection";

    // --- Set 1 ---

    @ConfigItem(keyName = "set1Hotkey", hidden = true, name = "Hotkey", description = "Key that equips set 1",
            position = 0, section = set1)
    default Keybind set1Hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(keyName = "set1Items", hidden = true, name = "Items", description = "Comma separated item names, e.g. Toxic blowpipe,Black d'hide body",
            position = 1, section = set1)
    default String set1Items() {
        return "";
    }

    @ConfigItem(keyName = "set1Prayer", hidden = true, name = "Offensive prayer", description = "Prayer turned on together with this set",
            position = 2, section = set1)
    default OffensivePrayer set1Prayer() {
        return OffensivePrayer.NONE;
    }

    @ConfigItem(keyName = "set1Spell", hidden = true, name = "Spell after switch",
            description = "Standard spellbook spell cast on the monster you are fighting right after the switch. NONE = off",
            position = 3, section = set1)
    default Spell set1Spell() {
        return Spell.NONE;
    }

    // --- Set 2 ---

    @ConfigItem(keyName = "set2Hotkey", hidden = true, name = "Hotkey", description = "Key that equips set 2",
            position = 0, section = set2)
    default Keybind set2Hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(keyName = "set2Items", hidden = true, name = "Items", description = "Comma separated item names",
            position = 1, section = set2)
    default String set2Items() {
        return "";
    }

    @ConfigItem(keyName = "set2Prayer", hidden = true, name = "Offensive prayer", description = "Prayer turned on together with this set",
            position = 2, section = set2)
    default OffensivePrayer set2Prayer() {
        return OffensivePrayer.NONE;
    }

    @ConfigItem(keyName = "set2Spell", hidden = true, name = "Spell after switch",
            description = "Standard spellbook spell cast on the monster you are fighting right after the switch. NONE = off",
            position = 3, section = set2)
    default Spell set2Spell() {
        return Spell.NONE;
    }

    // --- Set 3 ---

    @ConfigItem(keyName = "set3Hotkey", hidden = true, name = "Hotkey", description = "Key that equips set 3",
            position = 0, section = set3)
    default Keybind set3Hotkey() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(keyName = "set3Items", hidden = true, name = "Items", description = "Comma separated item names",
            position = 1, section = set3)
    default String set3Items() {
        return "";
    }

    @ConfigItem(keyName = "set3Prayer", hidden = true, name = "Offensive prayer", description = "Prayer turned on together with this set",
            position = 2, section = set3)
    default OffensivePrayer set3Prayer() {
        return OffensivePrayer.NONE;
    }

    @ConfigItem(keyName = "set3Spell", hidden = true, name = "Spell after switch",
            description = "Standard spellbook spell cast on the monster you are fighting right after the switch. NONE = off",
            position = 3, section = set3)
    default Spell set3Spell() {
        return Spell.NONE;
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

    // --- Supplies ---

    @ConfigItem(keyName = "autoSupplies", name = "Enabled",
            description = "Eat and drink by itself while fighting monsters (PvM only, off while a player is involved)",
            position = 0, section = supplies)
    default boolean autoSupplies() {
        return false;
    }

    @ConfigItem(keyName = "eatAtPercent", name = "Eat at HP %",
            description = "Eat once your health drops to this percent. 0 = never eat",
            position = 1, section = supplies)
    @Units(Units.PERCENT)
    default int eatAtPercent() {
        return 55;
    }

    @ConfigItem(keyName = "foods", name = "Food (in order)",
            description = "Comma separated, the first one you carry is used, e.g. Saradomin brew,Shark,Manta ray. "
                    + "Empty = any food in the inventory",
            position = 2, section = supplies)
    default String foods() {
        return "Shark,Manta ray,Anglerfish,Saradomin brew";
    }

    @ConfigItem(keyName = "restoreAfterBrews", name = "Restore after brews",
            description = "After this many sips of Saradomin brew, drink a super restore against the stat drain. 0 = off",
            position = 3, section = supplies)
    default int restoreAfterBrews() {
        return 3;
    }

    @ConfigItem(keyName = "prayerAt", name = "Prayer potion at",
            description = "Drink a prayer potion or super restore once prayer points drop to this. 0 = off",
            position = 4, section = supplies)
    default int prayerAt() {
        return 20;
    }

    @ConfigItem(keyName = "boostRanged", name = "Keep Ranged boosted",
            description = "Re-drink a ranging or divine ranging potion when the boost is almost gone", position = 5, section = supplies)
    default boolean boostRanged() {
        return false;
    }

    @ConfigItem(keyName = "boostMelee", name = "Keep melee boosted",
            description = "Re-drink super combat, or super attack and strength, when the boost is almost gone",
            position = 6, section = supplies)
    default boolean boostMelee() {
        return false;
    }

    @ConfigItem(keyName = "boostMagic", name = "Keep Magic boosted",
            description = "Re-drink a magic or divine magic potion when the boost is almost gone", position = 7, section = supplies)
    default boolean boostMagic() {
        return false;
    }
}
