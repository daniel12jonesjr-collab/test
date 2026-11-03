package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Kleine abgerundete Pille. Als Toggle (Editor) oder rein zur Anzeige (Karte). */
class Chip extends JToggleButton
{
	private final Color color;

	Chip(String text, Color color, boolean interactive)
	{
		super(text);
		this.color = color;
		setFont(Theme.small());
		setFocusPainted(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setOpaque(false);
		setBorder(new EmptyBorder(3, 8, 3, 8));
		if (!interactive)
		{
			// Reine Anzeige: bleibt immer "an", Klicks aendern nichts (ohne ausgegraute Schrift)
			setModel(new ToggleButtonModel()
			{
				@Override
				public void setSelected(boolean b)
				{
					super.setSelected(true);
				}
			});
			setSelected(true);
			setFocusable(false);
		}
		addItemListener(e -> setForeground(isSelected() ? color : Theme.MUTED));
		setForeground(isSelected() ? color : Theme.MUTED);
		setCursor(interactive ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		boolean on = isSelected();
		g2.setColor(on ? Theme.alpha(color, 60) : Theme.SURFACE);
		g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
		g2.setColor(on ? color : Theme.BORDER);
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
		g2.dispose();
		super.paintComponent(g);
	}
}
