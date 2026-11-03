package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Panel mit abgerundeten Ecken, optionaler Akzentleiste links und Glow-Rahmen. */
class RoundedPanel extends JPanel
{
	private Color fill;
	private Color accent;
	private Color glow;
	private final int radius;

	RoundedPanel(LayoutManager layout, Color fill, int radius)
	{
		super(layout);
		this.fill = fill;
		this.radius = radius;
		setOpaque(false);
	}

	void setFill(Color fill)
	{
		this.fill = fill;
		repaint();
	}

	void setAccent(Color accent)
	{
		this.accent = accent;
		repaint();
	}

	void setGlow(Color glow)
	{
		this.glow = glow;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();

		g2.setColor(fill);
		g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);

		if (accent != null)
		{
			g2.setColor(accent);
			g2.fillRoundRect(0, 0, 4, h - 1, 4, 4);
		}
		if (glow != null)
		{
			g2.setColor(glow);
			g2.setStroke(new BasicStroke(2f));
			g2.drawRoundRect(1, 1, w - 3, h - 3, radius, radius);
		}
		g2.dispose();
		super.paintComponent(g);
	}
}
