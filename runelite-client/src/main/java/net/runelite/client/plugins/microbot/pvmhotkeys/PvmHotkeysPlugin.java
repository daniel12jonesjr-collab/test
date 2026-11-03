package net.runelite.client.plugins.microbot.pvmhotkeys;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Preset;
import net.runelite.client.plugins.microbot.pvmhotkeys.ui.PanelActions;
import net.runelite.client.plugins.microbot.pvmhotkeys.ui.PvmHotkeysPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PvM Switcher: Presets aus Gear + Gebeten + Aktion, ausgeloest per Hotkey.
 *
 * Threads:
 *  - Tastatur (AWT)   -> nur Preset einreihen
 *  - Script-Executor  -> Gebete, Gear, Aktion
 *  - Client-Thread    -> Ausruestung fuer "Set" lesen
 *  - Swing (EDT)      -> Panel
 */
@PluginDescriptor(
	name = PluginDescriptor.Default + "PvM Switcher",
	description = "Hotkey-Presets: Gear-Switch + Gebete + Zauber/Spec auf Monster",
	tags = {"pvm", "switch", "gear", "prayer", "magic", "hotkey", "microbot"},
	enabledByDefault = false
)
@Slf4j
public class PvmHotkeysPlugin extends Plugin implements PanelActions
{
	@Inject private PvmHotkeysConfig config;
	@Inject private PvmHotkeysScript script;
	@Inject private PresetStore store;
	@Inject private KeyManager keyManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ClientThread clientThread;
	@Inject private Client client;
	@Inject private ItemManager itemManager;
	@Inject private OverlayManager overlayManager;
	@Inject private PvmTimersOverlay timersOverlay;
	@Inject private TimerState timers;

	private PvmHotkeysPanel panel;
	private NavigationButton navButton;

	/** Gedrueckt gehaltene Tasten: verhindert, dass Auto-Repeat das Preset 30x pro Sekunde feuert. */
	private final Set<Integer> held = new HashSet<>();
	private boolean consumeTyped;

	@Provides
	PvmHotkeysConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PvmHotkeysConfig.class);
	}

	private final KeyListener keyListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
			if (consumeTyped) e.consume(); // verhindert, dass die Taste im Chat landet
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			for (Preset p : store.all())
			{
				if (p.hasHotkey() && p.toKeybind().matches(e))
				{
					e.consume();
					consumeTyped = true;
					if (held.add(e.getKeyCode()))
					{
						script.request(p);
					}
					return;
				}
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
			if (held.remove(e.getKeyCode()))
			{
				e.consume();
			}
			consumeTyped = false;
		}

		@Override
		public void focusLost()
		{
			held.clear();
			consumeTyped = false;
		}
	};

	@Override
	protected void startUp()
	{
		store.load();
		panel = new PvmHotkeysPanel(store, itemManager, this);
		navButton = NavigationButton.builder()
			.tooltip("PvM Switcher")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		script.setOnExecuted(panel::flash);
		keyManager.registerKeyListener(keyListener);
		overlayManager.add(timersOverlay);
		script.run(config);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(keyListener);
		overlayManager.remove(timersOverlay);
		timers.clearFreeze();
		script.shutdown();
		script.setOnExecuted(null);
		clientToolbar.removeNavigation(navButton);
		held.clear();
		panel = null;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		timers.onGameTick();
	}

	// ------------------------------------------------------------------ PanelActions (vom EDT)

	@Override
	public void captureGear(Preset preset)
	{
		// Ausruestung nur auf dem Client-Thread lesen, danach zurueck auf den Swing-Thread
		clientThread.invokeLater(() -> {
			ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
			List<Integer> ids = new ArrayList<>();
			if (eq != null)
			{
				for (Item item : eq.getItems())
				{
					if (item != null && item.getId() > 0)
					{
						ids.add(item.getId());
					}
				}
			}
			SwingUtilities.invokeLater(() -> {
				preset.setGear(ids);
				save();
				if (panel != null) panel.rebuild();
				Microbot.log(preset.getName() + ": " + ids.size() + " Items gespeichert");
			});
		});
	}

	@Override
	public void save()
	{
		store.save();
	}

	@Override
	public void delete(Preset preset)
	{
		store.remove(preset);
		panel.rebuild();
	}

	@Override
	public void move(Preset preset, int delta)
	{
		store.move(preset, delta);
		panel.rebuild();
	}

	/** Blitz-Icon fuer die Seitenleiste, zur Laufzeit gezeichnet (keine Bilddatei noetig). */
	private static BufferedImage icon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Polygon bolt = new Polygon(new int[]{9, 3, 8, 6, 13, 8}, new int[]{1, 9, 9, 15, 6, 6}, 6);
		g.setColor(new Color(120, 100, 255));
		g.fillPolygon(bolt);
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(200, 190, 255));
		g.drawPolygon(bolt);
		g.dispose();
		return img;
	}
}
