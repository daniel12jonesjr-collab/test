package net.runelite.client.plugins.microbot.highalcher.market.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Abgerundete Karte mit optionaler farbiger Akzentleiste links. */
class RoundPanel extends JPanel
{
	private Color fill;
	private Color accent;

	RoundPanel(LayoutManager layout, Color fill)
	{
		super(layout);
		this.fill = fill;
		setOpaque(false);
	}

	void setFill(Color c)
	{
		fill = c;
		repaint();
	}

	void setAccent(Color c)
	{
		accent = c;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(fill);
		g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
		if (accent != null)
		{
			g2.setColor(accent);
			g2.fillRoundRect(0, 0, 4, getHeight() - 1, 4, 4);
		}
		g2.dispose();
		super.paintComponent(g);
	}
}
