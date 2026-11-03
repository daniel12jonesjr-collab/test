package net.runelite.client.plugins.microbot.pvmhotkeys;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Schneller Gear-Switch.
 *
 * Rs2Inventory.interact() fragt PRO ITEM mehrmals den Client-Thread ab (Bank/Shop/GE/Deposit-Box offen?,
 * Widget suchen, Bounds lesen ...). Jede Abfrage wartet bis zum naechsten Client-Frame.
 * Bei 8 Items summiert sich das spuerbar.
 *
 * Hier: EIN Client-Thread-Durchgang sammelt alles fuer alle Items, danach werden die Klicks
 * ohne Pause hintereinander abgeschickt. Der Server verarbeitet sie im selben Tick.
 */
final class FastEquip
{
	private static final String[] EQUIP_ACTIONS = {"wield", "wear", "equip"};

	private FastEquip() {}

	/** Ergebnis eines Switches, fuer Chat-Meldungen. */
	static final class Result
	{
		int clicked;
		int alreadyWorn;
		int missing;
	}

	/** Ein vorbereiteter Klick. */
	private static final class Click
	{
		final int slot;
		final int itemId;
		final int identifier;
		final String option;
		final String target;
		final Rectangle bounds;

		Click(int slot, int itemId, int identifier, String option, String target, Rectangle bounds)
		{
			this.slot = slot;
			this.itemId = itemId;
			this.identifier = identifier;
			this.option = option;
			this.target = target;
			this.bounds = bounds;
		}
	}

	static Result equip(List<Integer> gear)
	{
		Result result = new Result();
		if (gear == null || gear.isEmpty()) return result;

		// Nur wechseln, wenn noetig: der Tab-Wechsel selbst ist eine Menue-Aktion
		if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
		{
			Rs2Tab.switchTo(InterfaceTab.INVENTORY);
		}

		List<Click> clicks = Microbot.getClientThread()
			.runOnClientThreadOptional(() -> plan(Microbot.getClient(), gear, result))
			.orElse(Collections.emptyList());

		for (Click c : clicks)
		{
			Microbot.doInvoke(new NewMenuEntry()
					.option(c.option)
					.param0(c.slot)
					.param1(ComponentID.INVENTORY_CONTAINER)
					.opcode(MenuAction.CC_OP.getId())
					.identifier(c.identifier)
					.itemId(c.itemId)
					.target(c.target),
				c.bounds);
			result.clicked++;
		}
		return result;
	}

	/** Laeuft auf dem Client-Thread: liest Ausruestung + Inventar-Widgets in einem Rutsch. */
	private static List<Click> plan(Client client, List<Integer> gear, Result result)
	{
		// Eine Auswahl (z. B. Zauber) wuerde aus "Wear" ein "Cast on" machen -> vorher aufheben
		if (client.isWidgetSelected())
		{
			client.setWidgetSelected(false);
		}

		Set<Integer> worn = new HashSet<>();
		ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
		if (eq != null)
		{
			for (Item i : eq.getItems())
			{
				if (i != null && i.getId() > 0) worn.add(i.getId());
			}
		}

		Widget inv = client.getWidget(ComponentID.INVENTORY_CONTAINER);
		Widget[] slots = inv == null ? null : inv.getDynamicChildren();

		List<Click> clicks = new ArrayList<>();
		Set<Integer> usedSlots = new HashSet<>();

		for (int id : gear)
		{
			if (worn.contains(id))
			{
				result.alreadyWorn++;
				continue;
			}
			Click click = slots == null ? null : findClick(slots, id, usedSlots);
			if (click == null)
			{
				result.missing++;
				continue;
			}
			usedSlots.add(click.slot);
			clicks.add(click);
		}
		return clicks;
	}

	private static Click findClick(Widget[] slots, int itemId, Set<Integer> usedSlots)
	{
		for (Widget w : slots)
		{
			if (w == null || w.getItemId() != itemId || usedSlots.contains(w.getIndex())) continue;

			String[] actions = w.getActions();
			if (actions == null) continue;

			for (int i = 0; i < actions.length; i++)
			{
				String a = actions[i];
				if (a == null) continue;
				String clean = a.replaceAll("<[^>]*>", "");
				for (String e : EQUIP_ACTIONS)
				{
					if (clean.equalsIgnoreCase(e))
					{
						Rectangle b = w.isHidden() ? new Rectangle(1, 1) : w.getBounds();
						return new Click(w.getIndex(), itemId, i + 1, clean, w.getName(), b);
					}
				}
			}
		}
		return null;
	}
}
