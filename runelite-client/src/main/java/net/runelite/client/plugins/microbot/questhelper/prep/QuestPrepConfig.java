package net.runelite.client.plugins.microbot.questhelper.prep;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(QuestPrepConfig.GROUP)
public interface QuestPrepConfig extends Config
{
	String GROUP = "questprep";
	String GEAR_KEY = "gearIds";

	@ConfigSection(name = "Vorbereitung", description = "Vor Questbeginn", position = 0)
	String prep = "prep";

	@ConfigSection(name = "Einkauf", description = "Grand Exchange", position = 1)
	String shop = "shop";

	@ConfigSection(name = "Loadout", description = "Food, Staminas, Teleports", position = 2)
	String loadout = "loadout";

	@ConfigSection(name = "Unterwegs", description = "Pruefungen vor jedem Schritt", position = 3)
	String guard = "guard";

	// ------------------------------------------------------------------ Vorbereitung
	@ConfigItem(keyName = "enabled", name = "Quest-Vorbereitung aktiv",
		description = "Vor Questbeginn: Bedarf pruefen, einkaufen, Loadout aus der Bank holen.", position = 0, section = prep)
	default boolean enabled() { return true; }

	@ConfigItem(keyName = "depositFirst", name = "Inventar vorher einlagern",
		description = "Leert das Inventar in die Bank, bevor das Loadout geholt wird.", position = 1, section = prep)
	default boolean depositFirst() { return true; }

	@ConfigItem(keyName = "includeRecommended", name = "Empfohlene Items mitnehmen",
		description = "Auch die von Quest Helper empfohlenen Items (meist Teleports) einplanen.", position = 2, section = prep)
	default boolean includeRecommended() { return true; }

	@ConfigItem(keyName = "equipGear", name = "Quest-Gear anlegen",
		description = "Die im Panel per \"Set\" gespeicherte Ausruestung holen und anlegen.", position = 3, section = prep)
	default boolean equipGear() { return true; }

	// ------------------------------------------------------------------ Einkauf
	@ConfigItem(keyName = "buyMissing", name = "Fehlende Items kaufen",
		description = "Fehlende handelbare Items EINMAL vor Questbeginn kaufen. Waehrend der Quest wird nichts gekauft.",
		position = 0, section = shop)
	default boolean buyMissing() { return true; }

	@ConfigItem(keyName = "askBeforeBuying", name = "Vor dem Kauf fragen",
		description = "Zeigt Einkaufsliste und Teleport-Vorschlaege zur Bestaetigung, bevor gekauft wird.",
		position = 1, section = shop)
	default boolean askBeforeBuying() { return true; }

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "markupPercent", name = "Preisaufschlag (%)",
		description = "Aufschlag auf den aktuellen Preis, damit Angebote schnell kaufen.", position = 2, section = shop)
	default int markupPercent() { return 15; }

	@ConfigItem(keyName = "maxItemPrice", name = "Max. Preis pro Item",
		description = "Teurere Items werden nicht automatisch gekauft.", position = 3, section = shop)
	default int maxItemPrice() { return 500_000; }

	@ConfigItem(keyName = "maxTotalSpend", name = "Max. Gesamtausgabe",
		description = "Wird das ueberschritten, stoppt die Vorbereitung mit einer Einkaufsliste.", position = 4, section = shop)
	default int maxTotalSpend() { return 2_000_000; }

	// ------------------------------------------------------------------ Loadout
	@ConfigItem(keyName = "foodName", name = "Food", description = "Exakter Itemname, leer = kein Food",
		position = 0, section = loadout)
	default String foodName() { return "Shark"; }

	@Range(min = 0, max = 28)
	@ConfigItem(keyName = "foodAmount", name = "Food-Menge", description = "", position = 1, section = loadout)
	default int foodAmount() { return 6; }

	@ConfigItem(keyName = "staminaName", name = "Stamina", description = "Exakter Itemname, leer = keine",
		position = 2, section = loadout)
	default String staminaName() { return "Stamina potion(4)"; }

	@Range(min = 0, max = 10)
	@ConfigItem(keyName = "staminaAmount", name = "Stamina-Menge", description = "", position = 3, section = loadout)
	default int staminaAmount() { return 2; }

	@ConfigItem(keyName = "extraItems", name = "Weitere Items",
		description = "Kommagetrennt mit Menge, z. B. \"Varrock teleport:5, Ring of wealth (5):1\"",
		position = 4, section = loadout)
	default String extraItems() { return "Varrock teleport:3, Falador teleport:3, Camelot teleport:3"; }

	@ConfigItem(keyName = "suggestTeleports", name = "Teleports vorschlagen",
		description = "Sucht Teleports nahe den Quest-Orten und schlaegt sie vor dem Start vor.",
		position = 5, section = loadout)
	default boolean suggestTeleports() { return true; }

	@Range(min = 1, max = 8)
	@ConfigItem(keyName = "maxTeleportSuggestions", name = "Max. Teleport-Vorschlaege", description = "",
		position = 6, section = loadout)
	default int maxTeleportSuggestions() { return 4; }

	@Range(min = 1, max = 10)
	@ConfigItem(keyName = "teleportUses", name = "Nutzungen pro Teleport",
		description = "Fuer Runen/Tabs: so oft wird jeder Teleport eingeplant.", position = 7, section = loadout)
	default int teleportUses() { return 2; }

	// ------------------------------------------------------------------ Unterwegs
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "eatAtPercent", name = "Essen ab HP (%)", description = "", position = 0, section = guard)
	default int eatAtPercent() { return 50; }

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "staminaBelow", name = "Stamina unter Energie (%)",
		description = "Trinkt Stamina/Energy, wenn die Run-Energie darunter faellt und kein Stamina-Effekt aktiv ist.",
		position = 1, section = guard)
	default int staminaBelow() { return 30; }

	@ConfigItem(keyName = "useBankTeleports", name = "Teleports aus der Bank nutzen",
		description = "Bei weiten Wegen prueft der Walker, ob ein Umweg ueber die Bank (Teleport holen) schneller ist.",
		position = 2, section = guard)
	default boolean useBankTeleports() { return true; }

	@Range(min = 20, max = 500)
	@ConfigItem(keyName = "longWalkTiles", name = "Weiter Weg ab (Felder)", description = "", position = 3, section = guard)
	default int longWalkTiles() { return 120; }
}
