package net.runelite.client.plugins.microbot.questhelper.prep;

import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.http.api.item.ItemPrice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Berechnet, was fuer eine Quest gebraucht wird und woher es kommt (Inventar, Bank, Kauf).
 * Liest Bank-Mengen aus Microbots Bank-Cache -> die Bank muss vorher einmal offen gewesen sein.
 */
final class PrepPlanner
{
	private PrepPlanner() {}

	static List<PrepItem> plan(QuestHelper quest, QuestPrepConfig config)
	{
		Map<String, PrepItem> byKey = new LinkedHashMap<>();

		if (quest != null)
		{
			addRequirements(byKey, quest.getItemRequirements(), PrepItem.Source.QUEST);
			if (config.includeRecommended())
			{
				addRequirements(byKey, quest.getItemRecommended(), PrepItem.Source.RECOMMENDED);
			}
		}

		addNamed(byKey, config.foodName(), config.foodAmount());
		addNamed(byKey, config.staminaName(), config.staminaAmount());
		for (String entry : config.extraItems().split(","))
		{
			String[] parts = entry.trim().split(":");
			if (parts[0].trim().isEmpty()) continue;
			int qty = 1;
			if (parts.length > 1)
			{
				try { qty = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
			}
			addNamed(byKey, parts[0].trim(), qty);
		}

		if (config.equipGear())
		{
			for (int id : GearStore.load())
			{
				PrepItem item = new PrepItem();
				item.setIds(List.of(id));
				item.setName(itemName(id));
				item.setNeeded(1);
				item.setEquip(true);
				item.setSource(PrepItem.Source.GEAR);
				byKey.putIfAbsent("id:" + id, item);
			}
		}

		List<PrepItem> out = new ArrayList<>(byKey.values());
		for (PrepItem item : out)
		{
			refreshCounts(item);
			resolvePurchase(item);
		}
		return out;
	}

	/** Mengen neu lesen (nach Kauf/Abheben). */
	static void refreshCounts(PrepItem item)
	{
		int onPlayer = 0;
		int inBank = 0;
		for (int id : item.getIds())
		{
			onPlayer += Rs2Inventory.itemQuantity(id);
			if (Rs2Equipment.isWearing(id)) onPlayer++;
			inBank += Rs2Bank.count(id);
		}
		item.setHaveOnPlayer(onPlayer);
		item.setHaveInBank(inBank);
	}

	// ------------------------------------------------------------------ intern

	private static void addRequirements(Map<String, PrepItem> byKey, List<ItemRequirement> reqs, PrepItem.Source source)
	{
		if (reqs == null) return;
		for (ItemRequirement r : reqs)
		{
			if (r == null || r.getAllIds() == null || r.getAllIds().isEmpty()) continue;
			List<Integer> ids = new ArrayList<>();
			for (Integer id : r.getAllIds())
			{
				if (id != null && id > 0) ids.add(id);
			}
			if (ids.isEmpty()) continue;

			String key = "req:" + ids.get(0);
			PrepItem existing = byKey.get(key);
			int qty = Math.max(1, r.getQuantity());
			if (existing != null)
			{
				existing.setNeeded(Math.max(existing.getNeeded(), qty));
				continue;
			}
			PrepItem item = new PrepItem();
			item.setName(r.getName());
			item.setIds(ids);
			item.setNeeded(qty);
			item.setEquip(r.mustBeEquipped());
			item.setSource(source);
			byKey.put(key, item);
		}
	}

	private static void addNamed(Map<String, PrepItem> byKey, String name, int qty)
	{
		if (name == null || name.isBlank() || qty <= 0) return;
		int id = idForName(name.trim());
		if (id <= 0)
		{
			Microbot.log("Quest-Vorbereitung: Item \"" + name + "\" nicht gefunden (Name exakt pruefen)");
			return;
		}
		PrepItem item = new PrepItem();
		item.setName(name.trim());
		item.setIds(List.of(id));
		item.setNeeded(qty);
		item.setSource(PrepItem.Source.LOADOUT);
		byKey.merge("id:" + id, item, (a, b) -> { a.setNeeded(a.getNeeded() + b.getNeeded()); return a; });
	}

	/** Erste handelbare ID waehlen und Preis bestimmen. */
	private static void resolvePurchase(PrepItem item)
	{
		for (int id : item.getIds())
		{
			ItemComposition def = definition(id);
			if (def != null && def.isTradeable())
			{
				item.setTradeable(true);
				item.setBuyId(id);
				item.setUnitPrice(price(id));
				if (item.getName() == null) item.setName(def.getName());
				return;
			}
		}
		item.setTradeable(false);
	}

	static int price(int id)
	{
		WikiPrice wiki = Rs2GrandExchange.getRealTimePrices(id);
		if (wiki != null && wiki.buyPrice > 0 && wiki.buyPrice < Integer.MAX_VALUE) return wiki.buyPrice;
		int p = Rs2GrandExchange.getPrice(id);
		return p > 0 && p < Integer.MAX_VALUE ? p : -1;
	}

	static String itemName(int id)
	{
		ItemComposition def = definition(id);
		return def == null ? "Item " + id : def.getName();
	}

	private static ItemComposition definition(int id)
	{
		return Microbot.getClientThread()
			.runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(id))
			.orElse(null);
	}

	private static int idForName(String name)
	{
		ItemManager im = Microbot.getItemManager();
		if (im == null) return -1;
		List<ItemPrice> hits = im.search(name);
		for (ItemPrice p : hits)
		{
			if (p.getName().equalsIgnoreCase(name)) return p.getId();
		}
		return -1;
	}
}
