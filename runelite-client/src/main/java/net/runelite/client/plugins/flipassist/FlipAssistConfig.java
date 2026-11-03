package net.runelite.client.plugins.flipassist;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(FlipAssistConfig.GROUP)
public interface FlipAssistConfig extends Config
{
	String GROUP = "flipassist";
	String WATCH_KEY = "watchIds";

	@ConfigSection(name = "Alch", description = "High Alchemy", position = 0)
	String alch = "alch";

	@ConfigSection(name = "Flip", description = "Kaufen und teurer verkaufen", position = 1)
	String flip = "flip";

	@ConfigSection(name = "Meldungen", description = "Benachrichtigungen", position = 2)
	String alerts = "alerts";

	@ConfigSection(name = "Allgemein", description = "", position = 3)
	String general = "general";

	// ---------------------------------------------------------------- Alch
	@ConfigItem(keyName = "minAlchProfit", name = "Min. Gewinn pro Alch", description = "Nach Kaufpreis und Nature Rune",
		position = 0, section = alch)
	@Units(" gp")
	default int minAlchProfit() { return 1_500; }

	@ConfigItem(keyName = "minAlchVolume", name = "Min. Kaeufe pro Stunde",
		description = "Sorgt dafuer, dass man das Item auch wirklich bekommt", position = 1, section = alch)
	default int minAlchVolume() { return 10; }

	@ConfigItem(keyName = "includeNatureRune", name = "Nature Rune einrechnen", description = "", position = 2, section = alch)
	default boolean includeNatureRune() { return true; }

	// ---------------------------------------------------------------- Flip
	@ConfigItem(keyName = "minFlipMargin", name = "Min. Marge pro Stueck", description = "Nach 2 % GE-Steuer",
		position = 0, section = flip)
	@Units(" gp")
	default int minFlipMargin() { return 1_500; }

	@ConfigItem(keyName = "minFlipVolume", name = "Min. Handel pro Stunde",
		description = "Engpass-Seite (Kaeufe bzw. Verkaeufe)", position = 1, section = flip)
	default int minFlipVolume() { return 20; }

	// ---------------------------------------------------------------- Meldungen
	@ConfigItem(keyName = "notifyDesktop", name = "Desktop-Benachrichtigung", description = "", position = 0, section = alerts)
	default boolean notifyDesktop() { return true; }

	@ConfigItem(keyName = "notifyChat", name = "Chat-Meldung", description = "", position = 1, section = alerts)
	default boolean notifyChat() { return true; }

	@Range(min = 1, max = 10)
	@ConfigItem(keyName = "maxAlertsPerUpdate", name = "Max. Meldungen pro Update", description = "Gegen Spam",
		position = 2, section = alerts)
	default int maxAlertsPerUpdate() { return 3; }

	@Range(min = 1, max = 240)
	@ConfigItem(keyName = "alertCooldown", name = "Gleiches Item erneut melden nach", description = "",
		position = 3, section = alerts)
	@Units(Units.MINUTES)
	default int alertCooldown() { return 30; }

	// ---------------------------------------------------------------- Allgemein
	@ConfigItem(keyName = "maxBuyPrice", name = "Max. Kaufpreis (0 = egal)", description = "Dein Budget pro Stueck",
		position = 0, section = general)
	@Units(" gp")
	default int maxBuyPrice() { return 0; }

	@ConfigItem(keyName = "includeMembers", name = "Members-Items", description = "", position = 1, section = general)
	default boolean includeMembers() { return true; }

	@Range(min = 1, max = 120)
	@ConfigItem(keyName = "maxPriceAge", name = "Preise max. alt", description = "Aeltere Preise werden ignoriert",
		position = 2, section = general)
	@Units(Units.MINUTES)
	default int maxPriceAge() { return 15; }

	@Range(min = 30, max = 600)
	@ConfigItem(keyName = "refreshSeconds", name = "Aktualisieren alle", description = "", position = 3, section = general)
	@Units(Units.SECONDS)
	default int refreshSeconds() { return 60; }

	@Range(min = 10, max = 200)
	@ConfigItem(keyName = "maxRows", name = "Max. Eintraege im Panel", description = "", position = 4, section = general)
	default int maxRows() { return 40; }
}
