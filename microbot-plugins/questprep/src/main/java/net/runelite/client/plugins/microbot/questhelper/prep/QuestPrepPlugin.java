package net.runelite.client.plugins.microbot.questhelper.prep;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Stellt Einstellungen + Panel fuer die Quest-Vorbereitung bereit.
 * Die eigentliche Arbeit macht QuestScript ueber QuestPrep / StepGuard / QuestTravel.
 */
@PluginDescriptor(
	name = PluginDescriptor.Default + "Quest Prep",
	description = "Vorbereitung fuer den Quest Helper: Einkauf mit Abfrage, Teleport-Vorschlaege, Loadout, Stamina/Food",
	tags = {"quest", "prep", "teleport", "stamina", "microbot"},
	enabledByDefault = false
)
public class QuestPrepPlugin extends Plugin
{
	@Inject private QuestPrepConfig config;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ClientThread clientThread;
	@Inject private Client client;

	private QuestPrepPanel panel;
	private NavigationButton nav;

	@Provides
	QuestPrepConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(QuestPrepConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new QuestPrepPanel(this::captureGear, GearStore::clear, () -> QuestPrep.get().restart());
		nav = NavigationButton.builder().tooltip("Quest Prep").icon(icon()).priority(8).panel(panel).build();
		clientToolbar.addNavigation(nav);
		panel.start();
	}

	@Override
	protected void shutDown()
	{
		panel.stop();
		clientToolbar.removeNavigation(nav);
	}

	/** "Set": aktuell getragene Ausruestung als Quest-Gear speichern. */
	private void captureGear()
	{
		clientThread.invokeLater(() -> {
			List<Integer> ids = new ArrayList<>();
			ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
			if (eq != null)
			{
				for (Item i : eq.getItems())
				{
					if (i != null && i.getId() > 0) ids.add(i.getId());
				}
			}
			GearStore.save(ids);
			Microbot.log("Quest-Gear gespeichert: " + ids.size() + " Items");
			SwingUtilities.invokeLater(panel::refresh);
		});
	}

	private static BufferedImage icon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(80, 200, 120));
		g.fillRoundRect(2, 1, 12, 14, 3, 3);
		g.setColor(new Color(28, 28, 32));
		g.fillRect(5, 4, 6, 1);
		g.fillRect(5, 7, 6, 1);
		g.fillRect(5, 10, 4, 1);
		g.dispose();
		return img;
	}
}
