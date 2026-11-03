package net.runelite.client.plugins.microbot.pvmhotkeys;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Nur allgemeine Einstellungen. Presets (Hotkey, Gear, Gebete, Aktion) werden im Seitenpanel verwaltet.
 */
@ConfigGroup(PvmHotkeysConfig.GROUP)
public interface PvmHotkeysConfig extends Config
{
	String GROUP = "pvmhotkeys";

	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "minTicksBetweenCasts",
		name = "Min. Ticks zwischen Zaubern",
		description = "Normale Kampfzauber haben 5 Ticks Angriffstempo. Fruehere Zauber-Eingaben werden ignoriert.",
		position = 0
	)
	default int minTicksBetweenCasts() { return 5; }

	@ConfigItem(
		keyName = "fallbackToNearest",
		name = "Ohne Ziel: naechstes Monster",
		description = "Wenn du gerade nichts angreifst, wird das naechste angreifbare Monster gewaehlt.",
		position = 1
	)
	default boolean fallbackToNearest() { return false; }

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Chat-Meldungen",
		description = "Zeigt im Chat an, wenn ein Teil eines Presets nicht ausgefuehrt werden konnte.",
		position = 2
	)
	default boolean chatFeedback() { return true; }

	@ConfigItem(
		keyName = "showTimers",
		name = "Cooldown-Balken anzeigen",
		description = "Overlay mit Zauber-Cooldown, Freeze-Dauer (Entangle, Eis-Zauber ...) und Immunitaet.",
		position = 3
	)
	default boolean showTimers() { return true; }
}
