package net.runelite.client.plugins.microbot.questhelper.prep;

import lombok.Getter;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Vorbereitung vor Questbeginn als Zustandsmaschine.
 * QuestScript ruft tick() in jeder Schleife auf. Solange BUSY zurueckkommt, wird kein Quest-Schritt gemacht.
 *
 *   SCAN_BANK -> (BUY -> WAIT_OFFERS -> SCAN_BANK) -> LOADOUT -> EQUIP -> READY
 *                                                                     \-> FAILED (mit Grund)
 */
public final class QuestPrep
{
	public enum Result { BUSY, READY, FAILED }

	public enum State
	{
		IDLE("Wartet auf Quest"),
		SCAN_BANK("Bank pruefen"),
		CONFIRM("Warte auf deine Bestaetigung"),
		BUY("Einkauf im GE"),
		WAIT_OFFERS("Warte auf GE-Angebote"),
		LOADOUT("Loadout aus der Bank holen"),
		EQUIP("Ausruestung anlegen"),
		READY("Bereit"),
		FAILED("Gestoppt");

		@Getter private final String label;

		State(String label) { this.label = label; }
	}

	private static final QuestPrep INSTANCE = new QuestPrep();
	private static final int MAX_BUY_ROUNDS = 3;

	public static QuestPrep get()
	{
		return INSTANCE;
	}

	@Getter private volatile State state = State.IDLE;
	@Getter private volatile String message = "";
	@Getter private volatile String questName;

	private final List<PrepItem> plan = new CopyOnWriteArrayList<>();
	private final Set<Integer> offered = new HashSet<>();
	private int buyRounds;

	// Ergebnis der Abfrage
	private boolean confirmed;
	private final Set<Integer> approvedBuyIds = new HashSet<>();
	private final List<PrepItem> teleportItems = new ArrayList<>();
	@Getter private final List<TeleportSuggestion> suggestions = new CopyOnWriteArrayList<>();
	private CompletableFuture<PrepDialog.Decision> pendingDecision;

	private QuestPrep() {}

	public List<PrepItem> getPlan()
	{
		return Collections.unmodifiableList(plan);
	}

	/** Panel-Button "Neu vorbereiten". */
	public synchronized void restart()
	{
		questName = null;
		state = State.IDLE;
		message = "";
	}

	/** Waehrend der Quest nicht mehr kaufen, wenn die Vorbereitung das schon erledigt hat. */
	public boolean handlesPurchases(QuestPrepConfig config)
	{
		return config.enabled() && config.buyMissing();
	}

	public synchronized Result tick(QuestHelper quest, QuestPrepConfig config)
	{
		if (!config.enabled() || quest == null) return Result.READY;

		String name = quest.getQuest() == null ? quest.toString() : quest.getQuest().getName();
		if (!name.equals(questName))
		{
			questName = name;
			plan.clear();
			offered.clear();
			buyRounds = 0;
			confirmed = false;
			approvedBuyIds.clear();
			teleportItems.clear();
			suggestions.clear();
			pendingDecision = null;
			set(State.SCAN_BANK, "Neue Quest: " + name);
		}

		switch (state)
		{
			case READY:
				return Result.READY;
			case FAILED:
				return Result.FAILED;
			case SCAN_BANK:
				scanBank(quest, config);
				break;
			case CONFIRM:
				confirm();
				break;
			case BUY:
				buy(config);
				break;
			case WAIT_OFFERS:
				waitOffers();
				break;
			case LOADOUT:
				loadout(config);
				break;
			case EQUIP:
				equip();
				break;
			default:
				set(State.SCAN_BANK, "");
		}
		return state == State.READY ? Result.READY : state == State.FAILED ? Result.FAILED : Result.BUSY;
	}

	// ------------------------------------------------------------------ Schritte

	private void scanBank(QuestHelper quest, QuestPrepConfig config)
	{
		if (!openBank()) return;

		plan.clear();
		plan.addAll(PrepPlanner.plan(quest, config));
		for (PrepItem t : teleportItems)
		{
			PrepPlanner.refreshCounts(t);
			plan.add(t);
		}

		// Kandidaten fuer den Einkauf: fehlt, handelbar, Preis bekannt und unter Limit
		List<PrepItem> candidates = new ArrayList<>();
		for (PrepItem item : plan)
		{
			if (item.missingAfterBank() == 0 || !item.isTradeable() || offered.contains(item.getBuyId())) continue;
			if (item.getUnitPrice() <= 0 || item.getUnitPrice() > config.maxItemPrice()) continue;
			candidates.add(item);
		}

		// Erste Runde: Teleports vorschlagen und fragen, bevor irgendwas gekauft wird
		if (!confirmed)
		{
			suggestions.clear();
			if (config.suggestTeleports())
			{
				Microbot.status = "Quest-Vorbereitung: suche passende Teleports";
				suggestions.addAll(TeleportAdvisor.suggest(quest, config.maxTeleportSuggestions(), config.teleportUses()));
			}
			boolean needsAnswer = config.askBeforeBuying() && (!suggestions.isEmpty() || (config.buyMissing() && !candidates.isEmpty()));
			if (needsAnswer)
			{
				Rs2Bank.closeBank();
				pendingDecision = PrepDialog.ask(questName, new ArrayList<>(suggestions),
					config.buyMissing() ? candidates : new ArrayList<>());
				set(State.CONFIRM, "Bitte Einkauf und Teleports bestaetigen");
				return;
			}
			// Ohne Abfrage: alles einplanen, was die Einstellungen erlauben
			confirmed = true;
			for (TeleportSuggestion sug : suggestions) teleportItems.addAll(sug.getItems());
			if (config.buyMissing()) candidates.forEach(i -> approvedBuyIds.add(i.getBuyId()));
			set(State.SCAN_BANK, "Plane neu");
			return;
		}

		List<PrepItem> toBuy = new ArrayList<>();
		long total = 0;
		for (PrepItem item : candidates)
		{
			if (!approvedBuyIds.contains(item.getBuyId())) continue;
			toBuy.add(item);
			total += (long) buyPrice(item, config) * item.missingAfterBank();
		}

		if (total > config.maxTotalSpend())
		{
			fail("Einkauf (" + total + " gp) ueber Limit " + config.maxTotalSpend() + " gp: "
				+ toBuy.stream().map(i -> i.missingAfterBank() + "x " + i.getName()).collect(Collectors.joining(", ")));
			return;
		}

		if (!toBuy.isEmpty())
		{
			if (++buyRounds > MAX_BUY_ROUNDS)
			{
				fail("Items nach " + MAX_BUY_ROUNDS + " Einkaufsrunden noch nicht da: "
					+ toBuy.stream().map(PrepItem::getName).collect(Collectors.joining(", ")));
				return;
			}
			set(State.BUY, toBuy.size() + " Items kaufen (~" + total + " gp)");
		}
		else
		{
			set(State.LOADOUT, "Einkauf erledigt");
		}
	}

	private void confirm()
	{
		if (pendingDecision == null)
		{
			set(State.SCAN_BANK, "");
			return;
		}
		if (!pendingDecision.isDone()) return; // Dialog noch offen -> Quest wartet

		PrepDialog.Decision d;
		try
		{
			d = pendingDecision.get();
		}
		catch (Exception e)
		{
			fail("Abfrage fehlgeschlagen: " + e.getMessage());
			return;
		}
		pendingDecision = null;

		if (d.cancelled)
		{
			fail("Vorbereitung abgebrochen");
			return;
		}
		confirmed = true;
		for (TeleportSuggestion sug : d.teleports) teleportItems.addAll(sug.getItems());
		if (d.buy) approvedBuyIds.addAll(d.approvedBuyIds);
		set(State.SCAN_BANK, d.teleports.size() + " Teleports gewaehlt, " + approvedBuyIds.size() + " Items zum Kauf freigegeben");
	}

	private void buy(QuestPrepConfig config)
	{
		if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
		if (!Rs2GrandExchange.isOpen())
		{
			Rs2GrandExchange.walkToGrandExchange();
			Rs2GrandExchange.openExchange();
			if (!sleepUntil(Rs2GrandExchange::isOpen, 15_000)) return;
		}

		for (PrepItem item : plan)
		{
			int missing = item.missingAfterBank();
			if (missing == 0 || !item.isTradeable() || item.getBuyId() <= 0 || offered.contains(item.getBuyId())) continue;
			if (!approvedBuyIds.contains(item.getBuyId())) continue;
			if (item.getUnitPrice() <= 0 || item.getUnitPrice() > config.maxItemPrice()) continue;

			String exactName = PrepPlanner.itemName(item.getBuyId());
			if (!Rs2GrandExchange.buyItem(exactName, buyPrice(item, config), missing))
			{
				break; // meist: keine freien Slots -> erst abholen, dann naechste Runde
			}
			offered.add(item.getBuyId());
			message = "Angebot: " + missing + "x " + exactName;
		}
		set(State.WAIT_OFFERS, "Warte auf Angebote");
	}

	private void waitOffers()
	{
		if (!sleepUntil(Rs2GrandExchange::hasBoughtOffer, 60_000))
		{
			fail("GE-Angebote wurden in 60 s nicht gefuellt. Preisaufschlag erhoehen oder manuell kaufen.");
			return;
		}
		Rs2GrandExchange.collectAllToBank();
		set(State.SCAN_BANK, "Gekauft, pruefe erneut");
	}

	private void loadout(QuestPrepConfig config)
	{
		if (!openBank()) return;

		if (config.depositFirst())
		{
			Rs2Bank.depositAll();
			sleepUntil(Rs2Inventory::isEmpty, 2_000);
		}

		for (PrepItem item : plan)
		{
			PrepPlanner.refreshCounts(item);
			int want = item.toWithdraw();
			if (want <= 0) continue;

			for (int id : item.getIds())
			{
				int inBank = Rs2Bank.count(id);
				if (inBank <= 0) continue;
				int take = Math.min(want, inBank);
				int before = Rs2Inventory.itemQuantity(id);
				if (Rs2Bank.withdrawX(id, take))
				{
					sleepUntil(() -> Rs2Inventory.itemQuantity(id) > before, 2_000);
					want -= take;
				}
				if (want <= 0) break;
			}
			if (Rs2Inventory.isFull())
			{
				message = "Inventar voll - Loadout zu gross";
				break;
			}
		}
		set(State.EQUIP, message);
	}

	private void equip()
	{
		if (Rs2Bank.isOpen())
		{
			Rs2Bank.closeBank();
			sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
		}
		for (PrepItem item : plan)
		{
			if (!item.isEquip()) continue;
			for (int id : item.getIds())
			{
				if (Rs2Equipment.isWearing(id)) break;
				if (Rs2Inventory.hasItem(id))
				{
					Rs2Inventory.wield(id);
					sleepUntil(() -> Rs2Equipment.isWearing(id), 1_500);
					break;
				}
			}
		}

		List<String> stillMissing = new ArrayList<>();
		for (PrepItem item : plan)
		{
			PrepPlanner.refreshCounts(item);
			if (!item.satisfied() && item.getSource() == PrepItem.Source.QUEST && item.isTradeable())
			{
				stillMissing.add(item.getName());
			}
		}
		set(State.READY, stillMissing.isEmpty() ? "Bereit - Quest startet"
			: "Bereit, aber es fehlen: " + String.join(", ", stillMissing));
		Microbot.log("Quest-Vorbereitung: " + message);
	}

	// ------------------------------------------------------------------ Helfer

	private static boolean openBank()
	{
		if (Rs2Bank.isOpen()) return true;
		Rs2Bank.walkToBankAndUseBank();
		return sleepUntil(Rs2Bank::isOpen, 15_000);
	}

	private static int buyPrice(PrepItem item, QuestPrepConfig config)
	{
		double p = item.getUnitPrice() * (1 + config.markupPercent() / 100.0);
		return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.max(p, item.getUnitPrice() + 1)));
	}

	private void set(State s, String msg)
	{
		state = s;
		message = msg == null ? "" : msg;
		Microbot.status = "Quest-Vorbereitung: " + s.getLabel();
	}

	private void fail(String reason)
	{
		set(State.FAILED, reason);
		Microbot.log("Quest-Vorbereitung gestoppt: " + reason);
	}
}
