package net.runelite.client.plugins.microbot.simplewoodcutter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Simple Woodcutter",
        description = "Chops the nearest tree and drops logs when the inventory is full",
        tags = {"woodcutting", "microbot", "skilling", "example"},
        enabledByDefault = false
)
@Slf4j
public class SimpleWoodcutterPlugin extends Plugin {

    @Inject
    private SimpleWoodcutterScript script;

    @Inject
    private SimpleWoodcutterConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SimpleWoodcutterOverlay overlay;

    @Provides
    SimpleWoodcutterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SimpleWoodcutterConfig.class);
    }

    @Override
    protected void startUp() {
        log.info("Simple Woodcutter started");
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Simple Woodcutter stopped");
        overlayManager.remove(overlay);
        script.shutdown();
    }
}
