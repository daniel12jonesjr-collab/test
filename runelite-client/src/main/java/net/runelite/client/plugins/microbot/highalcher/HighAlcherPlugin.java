package net.runelite.client.plugins.microbot.highalcher;

import com.google.inject.Provides;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.highalcher.market.MarketService;
import net.runelite.client.plugins.microbot.highalcher.market.ui.BotControls;
import net.runelite.client.plugins.microbot.highalcher.market.ui.MarketPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * High Alcher in one plugin: the bot that alchs the inventory, plus a side panel with live wiki prices,
 * items worth alching, the GE buy limit tracker (Käufe tab) and alerts.
 * Turning the plugin on only opens the panel; the bot starts and stops with the button at the top of it.
 */
@PluginDescriptor(
        name = PluginDescriptor.Default + "High Alcher",
        description = "Alchs the inventory, plus live alch prices and a GE buy limit tracker in the side panel",
        tags = {"magic", "alch", "high alchemy", "money", "ge", "grand exchange", "price", "microbot"},
        enabledByDefault = false
)
public class HighAlcherPlugin extends Plugin {

    static final String CONFIG_GROUP = "highalcher";

    @Inject
    private HighAlcherScript script;

    @Inject
    private HighAlcherConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private HighAlcherOverlay overlay;

    @Inject
    private MarketService market;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private NoticeOverlay noticeOverlay;

    @Inject
    private NoticeCenter notices;

    @Inject
    private Paster paster;

    @Inject
    private KeyManager keyManager;

    private NavigationButton nav;

    @Provides
    HighAlcherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HighAlcherConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        overlayManager.add(noticeOverlay);
        paster.start();
        keyManager.registerKeyListener(paster);
        script.setOnStopped(market::refreshPanel);
        MarketPanel panel = market.start(new Controls());
        nav = NavigationButton.builder().tooltip("High Alcher").icon(icon()).priority(6).panel(panel).build();
        clientToolbar.addNavigation(nav);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        overlayManager.remove(noticeOverlay);
        keyManager.unregisterKeyListener(paster);
        paster.stop();
        script.setOnStopped(null);
        script.shutdown();
        clientToolbar.removeNavigation(nav);
        market.stop();
    }

    /** The panel's start / pause buttons and its Meldungen tab. */
    private class Controls implements BotControls {
        @Override
        public void toggleRunning() {
            if (script.isRunning()) {
                script.shutdown();
                notices.add("Stopped from the panel", NoticeCenter.WARN);
            } else {
                script.run(config);
                notices.add("Started", NoticeCenter.GOOD);
            }
            market.refreshPanel();
        }

        @Override
        public boolean isRunning() {
            return script.isRunning();
        }

        @Override
        public void togglePause() {
            if (!script.isRunning()) return;
            script.setPaused(!script.isPaused());
            notices.add(script.isPaused() ? "Paused" : "Resumed", NoticeCenter.INFO);
        }

        @Override
        public boolean isPaused() {
            return script.isRunning() && script.isPaused();
        }

        @Override
        public List<NoticeCenter.Notice> notices() {
            return notices.all();
        }

        @Override
        public void clearNotices() {
            notices.clear();
        }

        @Override
        public boolean typeIntoSearch(String name) {
            return paster.typeIntoSearch(name);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (CONFIG_GROUP.equals(event.getGroup())) market.onConfigChanged(event.getKey());
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        market.onGrandExchangeOfferChanged(event);
    }

    /** Orange coin with a flame-coloured "A". */
    private static BufferedImage icon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(255, 150, 50));
        g.fillOval(1, 1, 14, 14);
        g.setColor(new Color(90, 30, 10));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.drawString("A", 4, 12);
        g.dispose();
        return img;
    }
}
