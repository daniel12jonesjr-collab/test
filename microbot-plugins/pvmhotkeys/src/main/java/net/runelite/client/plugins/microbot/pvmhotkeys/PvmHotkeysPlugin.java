package net.runelite.client.plugins.microbot.pvmhotkeys;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;

/**
 * PvM-Hotkeys: pro Slot eine Taste -> ein Zauber auf das aktuelle Ziel.
 *
 * Ablauf:  Tastendruck (AWT-Thread) -> Slot in Warteschlange -> Script-Thread -> Pruefungen -> castOn(Ziel)
 * Im Key-Listener passiert absichtlich NICHTS ausser dem Einreihen, sonst friert der Client ein.
 */
@PluginDescriptor(
	name = PluginDescriptor.Default + "PvM Hotkeys",
	description = "Loest per Hotkey konfigurierte Zauber auf das aktuelle Ziel aus",
	tags = {"pvm", "magic", "hotkey", "spell", "microbot"},
	enabledByDefault = false
)
@Slf4j
public class PvmHotkeysPlugin extends Plugin
{
	@Inject
	private PvmHotkeysConfig config;

	@Inject
	private PvmHotkeysScript script;

	@Inject
	private KeyManager keyManager;

	@Provides
	PvmHotkeysConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PvmHotkeysConfig.class);
	}

	private final HotkeyListener slot1Listener = new HotkeyListener(() -> config.hotkey1())
	{
		@Override
		public void hotkeyPressed()
		{
			script.request(config.spell1());
		}
	};

	private final HotkeyListener slot2Listener = new HotkeyListener(() -> config.hotkey2())
	{
		@Override
		public void hotkeyPressed()
		{
			script.request(config.spell2());
		}
	};

	private final HotkeyListener slot3Listener = new HotkeyListener(() -> config.hotkey3())
	{
		@Override
		public void hotkeyPressed()
		{
			script.request(config.spell3());
		}
	};

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(slot1Listener);
		keyManager.registerKeyListener(slot2Listener);
		keyManager.registerKeyListener(slot3Listener);
		script.run(config);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(slot1Listener);
		keyManager.unregisterKeyListener(slot2Listener);
		keyManager.unregisterKeyListener(slot3Listener);
		script.shutdown();
	}
}
