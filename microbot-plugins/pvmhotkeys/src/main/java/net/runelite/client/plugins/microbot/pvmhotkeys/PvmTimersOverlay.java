package net.runelite.client.plugins.microbot.pvmhotkeys;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Spells;

/**
 * Cooldown-Balken:
 *   - Zauber: Angriffstempo bis zum naechsten Zauber
 *   - Freeze: Restdauer von Entangle/Snare/Bind/Eis-Zaubern auf dem Ziel
 *   - Immun:  die 3 s danach, in denen das Ziel nicht erneut eingefroren werden kann
 * Balken laufen innerhalb eines Ticks fluessig weiter (interpoliert), statt alle 0,6 s zu springen.
 */
public class PvmTimersOverlay extends OverlayPanel
{
	private static final Color CAST = new Color(88, 140, 255);
	private static final Color READY = new Color(80, 200, 120);
	private static final Color FREEZE = new Color(170, 110, 255);
	private static final Color IMMUNE = new Color(140, 140, 150);
	private static final Color TRACK = new Color(255, 255, 255, 40);

	private final TimerState timers;
	private final PvmHotkeysConfig config;

	@Inject
	PvmTimersOverlay(TimerState timers, PvmHotkeysConfig config)
	{
		this.timers = timers;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showTimers()) return null;

		double now = timers.getTick() + timers.tickFraction();

		// --- Zauber-Cooldown
		int cd = config.minTicksBetweenCasts();
		double sinceCast = now - timers.getLastCastTick();
		boolean ready = sinceCast >= cd;
		panelComponent.getChildren().add(LineComponent.builder()
			.left(timers.getLastSpellName().isEmpty() ? "Zauber" : timers.getLastSpellName())
			.right(ready ? "BEREIT" : String.format("%.1fs", (cd - sinceCast) * 0.6))
			.rightColor(ready ? READY : Color.WHITE)
			.build());
		panelComponent.getChildren().add(bar(ready ? 1 : sinceCast / cd, ready ? READY : CAST));

		// --- Freeze + Immunitaet
		if (timers.hasFreeze())
		{
			String name = timers.getFreezeSpellName();
			String target = timers.getFreezeTargetName();
			double start = timers.getFreezeStartTick();
			double end = timers.getFreezeEndTick();

			if (now < start)
			{
				panelComponent.getChildren().add(line(name + " fliegt...", target, FREEZE));
			}
			else if (now < end)
			{
				double left = end - now;
				panelComponent.getChildren().add(line(name, String.format("%.1fs", left * 0.6), FREEZE));
				panelComponent.getChildren().add(bar(left / (end - start), FREEZE));
			}
			else
			{
				double immuneLeft = end + Spells.IMMUNITY_TICKS - now;
				panelComponent.getChildren().add(line("Immun", String.format("%.1fs", Math.max(0, immuneLeft) * 0.6), IMMUNE));
				panelComponent.getChildren().add(bar(immuneLeft / Spells.IMMUNITY_TICKS, IMMUNE));
			}
		}

		return super.render(g);
	}

	private static LineComponent line(String left, String right, Color color)
	{
		return LineComponent.builder().left(left).leftColor(color).right(right == null ? "" : right).build();
	}

	private static ProgressBarComponent bar(double fraction, Color color)
	{
		ProgressBarComponent b = new ProgressBarComponent();
		b.setMinimum(0);
		b.setMaximum(1000);
		b.setValue(Math.max(0, Math.min(1, fraction)) * 1000);
		b.setForegroundColor(color);
		b.setBackgroundColor(TRACK);
		b.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
		b.setCenterLabel("");
		b.setPreferredSize(new Dimension(140, 6));
		return b;
	}
}
