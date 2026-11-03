package net.runelite.client.plugins.microbot.highalcher.market.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.highalcher.market.BuyTracker;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Karte fuer ein gekauftes Item:
 *   [Icon]  Name                                   ✕
 *           Gekauft 50 / 70                  noch 20
 *           [=========-----]       frei in 1:23:45
 */
class BuyCard extends RoundPanel
{
	BuyCard(BuyTracker.Entry e, int limit, ItemManager items, Consumer<Integer> remove)
	{
		super(new BorderLayout(8, 0), Style.CARD);
		long now = System.currentTimeMillis();
		boolean free = now >= e.freeAt();
		setAccent(free ? Style.PROFIT : Style.ALCH);
		setBorder(new EmptyBorder(7, 10, 7, 12));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		items.getImage(e.itemId).addTo(icon);
		add(icon, BorderLayout.WEST);

		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);

		// Zeile 1: Name | Loeschen
		JLabel delete = label("✕", Style.regular().deriveFont(13f), Style.MUTED);
		delete.setToolTipText("Aus der Liste nehmen und dieses Item nicht mehr verfolgen");
		delete.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		delete.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent ev) { delete.setForeground(Style.ERROR); }

			@Override
			public void mouseExited(MouseEvent ev) { delete.setForeground(Style.MUTED); }

			@Override
			public void mouseReleased(MouseEvent ev) { remove.accept(e.itemId); }
		});
		rows.add(row(label(e.name, Style.bold(13f), Style.TEXT), delete));

		// Zeile 2: gekauft / Limit | noch frei
		int bought = free ? 0 : e.bought;
		String limitText = limit > 0 ? " / " + limit : "";
		String left = limit <= 0 ? "Limit ?" : free ? "alles frei" : "noch " + Math.max(0, limit - bought);
		rows.add(row(label("Gekauft " + bought + limitText, Style.small(), Style.MUTED),
			label(left, Style.bold(13f), free || limit - bought > 0 ? Style.PROFIT : Style.ERROR)));

		// Zeile 3: Balken (verstrichene Zeit im Fenster) | Countdown
		float part = free ? 1f : Math.min(1f, (now - e.windowStart) / (float) BuyTracker.WINDOW_MS);
		rows.add(row(new Bar(part, free ? Style.PROFIT : Style.ALCH),
			label(free ? "Limit wieder frei" : "frei in " + countdown(e.freeAt() - now), Style.small(),
				free ? Style.PROFIT : Style.MUTED)));

		add(rows, BorderLayout.CENTER);
	}

	private static String countdown(long ms)
	{
		long s = Math.max(0, ms / 1000);
		return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}

	private static JPanel row(JComponent left, JComponent right)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		left.setMinimumSize(new Dimension(0, left.getPreferredSize().height));
		left.setPreferredSize(new Dimension(0, left.getPreferredSize().height));
		p.add(left, BorderLayout.CENTER);
		p.add(right, BorderLayout.EAST);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JLabel label(String text, Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return l;
	}

	/** Duenner, abgerundeter Fortschrittsbalken. */
	private static final class Bar extends JComponent
	{
		private final float part;
		private final Color color;

		Bar(float part, Color color)
		{
			this.part = part;
			this.color = color;
			setPreferredSize(new Dimension(0, 12));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int y = (getHeight() - 6) / 2;
			g2.setColor(Style.SURFACE);
			g2.fillRoundRect(0, y, getWidth(), 6, 6, 6);
			g2.setColor(color);
			g2.fillRoundRect(0, y, Math.max(6, (int) (getWidth() * part)), 6, 6, 6);
			g2.dispose();
		}
	}
}
