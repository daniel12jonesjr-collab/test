package net.runelite.client.plugins.microbot.pvmhotkeys;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

@ConfigGroup(PvmHotkeysConfig.GROUP)
public interface PvmHotkeysConfig extends Config
{
	String GROUP = "pvmhotkeys";

	@ConfigSection(name = "Slot 1", description = "Erster Zauber-Hotkey", position = 0)
	String slot1 = "slot1";

	@ConfigSection(name = "Slot 2", description = "Zweiter Zauber-Hotkey", position = 1)
	String slot2 = "slot2";

	@ConfigSection(name = "Slot 3", description = "Dritter Zauber-Hotkey", position = 2)
	String slot3 = "slot3";

	@ConfigSection(name = "Allgemein", description = "Allgemeine Einstellungen", position = 3)
	String general = "general";

	// ---------- Slot 1 ----------
	@ConfigItem(keyName = "hotkey1", name = "Hotkey", description = "Taste fuer Slot 1", position = 0, section = slot1)
	default Keybind hotkey1() { return Keybind.NOT_SET; }

	@ConfigItem(keyName = "spell1", name = "Zauber", description = "Zauber fuer Slot 1", position = 1, section = slot1)
	default Rs2CombatSpells spell1() { return Rs2CombatSpells.FIRE_SURGE; }

	// ---------- Slot 2 ----------
	@ConfigItem(keyName = "hotkey2", name = "Hotkey", description = "Taste fuer Slot 2", position = 0, section = slot2)
	default Keybind hotkey2() { return Keybind.NOT_SET; }

	@ConfigItem(keyName = "spell2", name = "Zauber", description = "Zauber fuer Slot 2", position = 1, section = slot2)
	default Rs2CombatSpells spell2() { return Rs2CombatSpells.ICE_BARRAGE; }

	// ---------- Slot 3 ----------
	@ConfigItem(keyName = "hotkey3", name = "Hotkey", description = "Taste fuer Slot 3", position = 0, section = slot3)
	default Keybind hotkey3() { return Keybind.NOT_SET; }

	@ConfigItem(keyName = "spell3", name = "Zauber", description = "Zauber fuer Slot 3", position = 1, section = slot3)
	default Rs2CombatSpells spell3() { return Rs2CombatSpells.BLOOD_BARRAGE; }

	// ---------- Allgemein ----------
	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "minTicksBetweenCasts",
		name = "Min. Ticks zwischen Zaubern",
		description = "Normale Kampfzauber haben 5 Ticks Angriffstempo. Schnellere Eingaben werden ignoriert.",
		position = 0,
		section = general
	)
	default int minTicksBetweenCasts() { return 5; }

	@ConfigItem(
		keyName = "fallbackToNearest",
		name = "Ohne Ziel: naechstes Monster",
		description = "Wenn du gerade nichts angreifst, wird das naechste angreifbare Monster gewaehlt.",
		position = 1,
		section = general
	)
	default boolean fallbackToNearest() { return false; }

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat-Meldungen",
		description = "Zeigt im Chat an, warum ein Zauber nicht ausgeloest wurde.",
		position = 2,
		section = general
	)
	default boolean chatFeedback() { return true; }
}
