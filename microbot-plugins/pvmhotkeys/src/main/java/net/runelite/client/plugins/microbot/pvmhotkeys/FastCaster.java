package net.runelite.client.plugins.microbot.pvmhotkeys;

import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schneller Ersatz fuer Rs2Magic.castOn().
 *
 * Rs2Magic.castOn() macht pro Zauber:
 *   - feste Pause 150-300 ms
 *   - canCast(): Tab-Wechsel, Einstellungs-Check, Widget-Suche ueber alle Zauber
 *   - sleepUntil(widgetSelected) mit grobem Abfrage-Intervall
 *   - Kamera drehen, falls Ziel nicht sichtbar
 *
 * Hier:
 *   - Widget-ID pro Zauber wird einmal gesucht und dann gemerkt
 *   - keine feste Pause
 *   - "Zauber ausgewaehlt" wird alle 5 ms geprueft (max. 250 ms)
 *   - keine Kamera-Bewegung (Menue-Aktion braucht kein sichtbares Ziel)
 * Runen, Level und Zauberbuch prueft der Aufrufer vorher (billige Varbit-/Cache-Abfragen).
 */
final class FastCaster
{
	private static final long SELECT_TIMEOUT_MS = 250;
	private static final long POLL_MS = 5;

	private static final Map<MagicAction, Integer> WIDGET_IDS = new ConcurrentHashMap<>();

	private FastCaster() {}

	static boolean castOn(MagicAction spell, Rs2NpcModel target) throws InterruptedException
	{
		if (spell == null || target == null) return false;

		int widgetId = widgetId(spell);
		if (widgetId == -1)
		{
			// Zauberbuch-Interface noch nicht geladen -> einmal Magie-Tab oeffnen, dann gemerkt
			Rs2Tab.switchTo(InterfaceTab.MAGIC);
			long until = System.currentTimeMillis() + 300;
			while (widgetId == -1 && System.currentTimeMillis() < until)
			{
				Thread.sleep(POLL_MS * 2);
				widgetId = widgetId(spell);
			}
			if (widgetId == -1) return false;
		}

		final int id = widgetId;
		Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget w = Rs2Widget.getWidget(id);
			return w == null || w.isHidden() ? null : w.getBounds();
		}).orElse(new Rectangle(1, 1));

		// Schritt 1: Zauber auswaehlen
		Microbot.doInvoke(new NewMenuEntry()
				.option("Cast")
				.param0(-1)
				.param1(id)
				.opcode(MenuAction.WIDGET_TARGET.getId())
				.identifier(1)
				.itemId(-1)
				.target(spell.getName()),
			bounds);

		// Schritt 2: warten bis der Client den Zauber als ausgewaehlt fuehrt (meist 1 Frame)
		long deadline = System.currentTimeMillis() + SELECT_TIMEOUT_MS;
		while (!Microbot.getClient().isWidgetSelected())
		{
			if (System.currentTimeMillis() > deadline) return false;
			Thread.sleep(POLL_MS);
		}

		// Schritt 3: auf das Monster anwenden (Rs2Npc nimmt automatisch WIDGET_TARGET_ON_NPC)
		return Rs2Npc.interact(target);
	}

	private static int widgetId(MagicAction spell)
	{
		Integer cached = WIDGET_IDS.get(spell);
		if (cached != null) return cached;

		int id = Microbot.getClientThread().runOnClientThreadOptional(spell::getWidgetId).orElse(-1);
		if (id != -1) WIDGET_IDS.put(spell, id);
		return id;
	}

	/** Nach Zauberbuch-Wechsel aufrufen: Widget-IDs koennen sich aendern. */
	static void clearCache()
	{
		WIDGET_IDS.clear();
	}
}
