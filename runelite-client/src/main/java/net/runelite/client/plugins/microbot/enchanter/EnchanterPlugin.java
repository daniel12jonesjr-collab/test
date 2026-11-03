package net.runelite.client.plugins.microbot.enchanter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Enchanter",
        description = "Enchants jewellery from the bank, e.g. sapphire rings into rings of recoil",
        tags = {"magic", "enchant", "money", "recoil", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class EnchanterPlugin extends Plugin {

    @Inject
    private EnchanterScript script;

    @Inject
    private EnchanterConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private EnchanterOverlay overlay;

    @Provides
    EnchanterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EnchanterConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        script.shutdown();
    }
}
