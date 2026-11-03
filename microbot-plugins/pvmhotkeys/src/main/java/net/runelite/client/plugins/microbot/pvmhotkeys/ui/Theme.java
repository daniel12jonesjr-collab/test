package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import net.runelite.client.ui.FontManager;

import java.awt.Color;
import java.awt.Font;

/** Zentrale Farben und Schriften, damit das ganze Panel einheitlich aussieht. */
final class Theme
{
	static final Color BG = new Color(24, 24, 27);
	static final Color CARD = new Color(36, 36, 41);
	static final Color CARD_HOVER = new Color(42, 42, 48);
	static final Color SURFACE = new Color(48, 48, 55);
	static final Color BORDER = new Color(60, 60, 68);
	static final Color TEXT = new Color(235, 235, 240);
	static final Color MUTED = new Color(150, 150, 160);
	static final Color PRIMARY = new Color(120, 100, 255);
	static final Color DANGER = new Color(230, 80, 80);
	static final Color PRAYER = new Color(110, 200, 255);

	static final int RADIUS = 12;
	static final int GAP = 8;

	private Theme() {}

	static Font title()
	{
		return FontManager.getRunescapeBoldFont().deriveFont(16f);
	}

	static Font body()
	{
		return FontManager.getRunescapeFont();
	}

	static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Color alpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
	}
}
