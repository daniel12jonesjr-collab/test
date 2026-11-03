package net.runelite.client.plugins.microbot.highalcher.market.ui;

import net.runelite.client.ui.FontManager;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/** Farben und Schriften des Panels an einer Stelle. */
final class Style
{
	static final Color BG = new Color(22, 22, 26);
	static final Color CARD = new Color(34, 34, 40);
	static final Color CARD_HOVER = new Color(42, 42, 50);
	static final Color SURFACE = new Color(48, 48, 56);
	static final Color BORDER = new Color(58, 58, 68);
	static final Color TEXT = new Color(236, 236, 242);
	static final Color MUTED = new Color(146, 146, 158);
	static final Color PROFIT = new Color(72, 210, 120);
	static final Color ALCH = new Color(255, 170, 60);
	static final Color FLIP = new Color(96, 150, 255);
	static final Color STAR = new Color(255, 205, 70);
	static final Color LIVE = new Color(72, 210, 120);
	static final Color STALE = new Color(255, 170, 60);
	static final Color ERROR = new Color(235, 80, 80);
	static final Color NEW = new Color(170, 110, 255);

	private Style() {}

	static Font bold(float size)
	{
		return FontManager.getRunescapeBoldFont().deriveFont(size);
	}

	static Font regular()
	{
		return FontManager.getRunescapeFont();
	}

	static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Color alpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	static void button(AbstractButton b, Color bg, Color fg)
	{
		b.setFont(small());
		b.setFocusPainted(false);
		b.setBackground(bg);
		b.setForeground(fg);
		b.setOpaque(true);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER, 1, true),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}
}
