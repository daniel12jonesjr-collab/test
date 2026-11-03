package net.runelite.client.plugins.microbot.questhelper.prep;

import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.ConditionalStep;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schlaegt Teleports fuer eine Quest vor.
 *
 *  1. Alle Orte der Quest-Schritte sammeln (aus Quest Helper)
 *  2. Alle Teleport-Items/-Zauber aus Microbots Wegfinder-Daten laden
 *  3. Greedy: Teleport waehlen, der die meisten noch nicht abgedeckten Orte erreicht, wiederholen
 *  4. Pro Vorschlag die benoetigten Items bestimmen (bevorzugt solche, die man schon besitzt)
 */
final class TeleportAdvisor
{
	private static final int RADIUS = 30;
	private static volatile List<Transport> teleports;

	private TeleportAdvisor() {}

	static List<TeleportSuggestion> suggest(QuestHelper quest, int maxSuggestions, int usesPerTeleport)
	{
		List<WorldPoint> spots = questLocations(quest);
		List<TeleportSuggestion> out = new ArrayList<>();
		if (spots.isEmpty() || maxSuggestions <= 0) return out;

		Set<WorldPoint> uncovered = new HashSet<>(spots);
		Set<String> used = new HashSet<>();

		for (int round = 0; round < maxSuggestions && !uncovered.isEmpty(); round++)
		{
			Transport best = null;
			int bestCover = 0;
			for (Transport t : teleports())
			{
				String key = label(t);
				if (used.contains(key)) continue;
				int cover = covered(t.getDestination(), uncovered).size();
				if (cover > bestCover)
				{
					best = t;
					bestCover = cover;
				}
			}
			if (best == null) break;

			used.add(label(best));
			Set<WorldPoint> hit = covered(best.getDestination(), uncovered);
			uncovered.removeAll(hit);

			TeleportSuggestion s = new TeleportSuggestion();
			s.setName(label(best));
			s.setDestination(best.getDestination());
			s.setCoveredSteps(bestCover);
			s.setItems(itemsFor(best, usesPerTeleport));
			out.add(s);
		}
		return out;
	}

	// ------------------------------------------------------------------ Quest-Orte

	static List<WorldPoint> questLocations(QuestHelper quest)
	{
		Set<WorldPoint> points = new HashSet<>();
		if (quest == null || quest.getPanels() == null) return new ArrayList<>();
		for (PanelDetails panel : quest.getPanels())
		{
			if (panel == null || panel.getSteps() == null) continue;
			for (QuestStep step : panel.getSteps())
			{
				collect(step, points, 0);
			}
		}
		return new ArrayList<>(points);
	}

	private static void collect(QuestStep step, Set<WorldPoint> points, int depth)
	{
		if (step == null || depth > 6) return;
		if (step instanceof DetailedQuestStep)
		{
			DetailedQuestStep d = (DetailedQuestStep) step;
			if (d.getDefinedPoint() != null && d.getDefinedPoint().getWorldPoint() != null)
			{
				points.add(d.getDefinedPoint().getWorldPoint());
			}
		}
		if (step instanceof ConditionalStep)
		{
			Collection<QuestStep> children = ((ConditionalStep) step).getSteps();
			if (children != null)
			{
				for (QuestStep c : children) collect(c, points, depth + 1);
			}
		}
	}

	private static Set<WorldPoint> covered(WorldPoint dest, Set<WorldPoint> spots)
	{
		Set<WorldPoint> hit = new HashSet<>();
		if (dest == null) return hit;
		for (WorldPoint p : spots)
		{
			if (p.getPlane() == dest.getPlane() && p.distanceTo2D(dest) <= RADIUS) hit.add(p);
		}
		return hit;
	}

	// ------------------------------------------------------------------ Teleports

	private static List<Transport> teleports()
	{
		if (teleports == null)
		{
			List<Transport> list = new ArrayList<>();
			Map<String, Transport> dedup = new LinkedHashMap<>();
			for (Set<Transport> set : Transport.loadAllFromResources().values())
			{
				for (Transport t : set)
				{
					if (t.getDestination() == null) continue;
					if (t.getType() != TransportType.TELEPORTATION_ITEM && t.getType() != TransportType.TELEPORTATION_SPELL) continue;
					dedup.putIfAbsent(label(t) + "@" + t.getDestination(), t);
				}
			}
			list.addAll(dedup.values());
			teleports = list;
		}
		return teleports;
	}

	private static String label(Transport t)
	{
		String info = t.getDisplayInfo();
		return info != null && !info.isBlank() ? info : (t.getName() != null ? t.getName() : t.getType().name());
	}

	/** Pro Anforderung die beste Alternative: schon vorhanden > billigste handelbare. */
	private static List<PrepItem> itemsFor(Transport t, int uses)
	{
		List<PrepItem> items = new ArrayList<>();
		for (TransportItemRequirement req : t.getItemRequirements())
		{
			// Rang: 0 = schon genug vorhanden, 1 = muss gekauft werden; danach billigster Preis
			int bestId = -1;
			int bestRank = Integer.MAX_VALUE;
			int bestPrice = Integer.MAX_VALUE;
			int perUse = 1;

			for (int id : req.getItemIds())
			{
				int q = Math.max(1, req.getRequiredQuantity(id));
				PrepItem probe = new PrepItem();
				probe.setIds(List.of(id));
				PrepPlanner.refreshCounts(probe);
				int rank = probe.getHaveOnPlayer() + probe.getHaveInBank() >= q ? 0 : 1;
				int price = PrepPlanner.price(id);
				int cmpPrice = price > 0 ? price : Integer.MAX_VALUE;

				if (rank < bestRank || (rank == bestRank && cmpPrice < bestPrice))
				{
					bestId = id;
					bestRank = rank;
					bestPrice = cmpPrice;
					perUse = q;
				}
			}
			if (bestId == -1) continue;

			final int chosen = bestId;
			ItemComposition def = Microbot.getClientThread()
				.runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(chosen))
				.orElse(null);
			boolean stackable = def != null && def.isStackable();

			PrepItem item = new PrepItem();
			item.setIds(List.of(bestId));
			item.setName(def != null ? def.getName() : "Item " + bestId);
			// Runen/Tabs werden verbraucht -> mal Nutzungen. Schmuck/Stab nur einmal.
			item.setNeeded(stackable ? perUse * Math.max(1, uses) : perUse);
			item.setSource(PrepItem.Source.TELEPORT);
			item.setTradeable(def != null && def.isTradeable());
			item.setBuyId(item.isTradeable() ? bestId : -1);
			item.setUnitPrice(bestPrice == Integer.MAX_VALUE ? -1 : bestPrice);
			PrepPlanner.refreshCounts(item);
			items.add(item);
		}
		return items;
	}
}
