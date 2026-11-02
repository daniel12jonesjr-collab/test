package net.runelite.client.plugins.microbot.simplewoodcutter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

/** Shows how long the script has been running and how much of the max runtime is left. */
public class SimpleWoodcutterOverlay extends OverlayPanel {

    private final SimpleWoodcutterScript script;

    @Inject
    SimpleWoodcutterOverlay(SimpleWoodcutterPlugin plugin, SimpleWoodcutterScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(180, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Simple Woodcutter")
                .color(Color.GREEN)
                .build());

        long elapsed = script.isRunning() ? System.currentTimeMillis() - script.getStartedAt() : 0;
        long max = script.getMaxRuntimeMs();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Runtime")
                .right(format(elapsed) + (max > 0 ? " / " + format(max) : ""))
                .build());
        if (max > 0) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Remaining")
                    .right(format(Math.max(0, max - elapsed)))
                    .build());
        }
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(Microbot.status)
                .build());
        return super.render(graphics);
    }

    private static String format(long ms) {
        long seconds = ms / 1000;
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
}
