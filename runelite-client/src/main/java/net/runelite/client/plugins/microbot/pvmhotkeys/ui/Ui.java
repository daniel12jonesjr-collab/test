package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Kleine Helfer fuer einheitliche Buttons und Labels. */
final class Ui
{
	private Ui() {}

	static void styleButton(AbstractButton b, Color bg, Color fg)
	{
		b.setFont(Theme.small());
		b.setFocusPainted(false);
		b.setBackground(bg);
		b.setForeground(fg);
		b.setOpaque(true);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Theme.BORDER, 1, true),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (b.isEnabled()) b.setBackground(bg.brighter());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				b.setBackground(bg);
			}
		});
	}

	static JLabel label(String text, java.awt.Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return l;
	}
}
