package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import net.runelite.client.config.Keybind;

import javax.swing.JButton;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Klicken -> "Taste druecken..." -> naechste Taste wird gespeichert.
 * ESC oder Backspace loescht den Hotkey.
 */
class HotkeyButton extends JButton
{
	private Keybind value;
	private boolean listening;

	HotkeyButton(Keybind initial, Consumer<Keybind> onChange)
	{
		this.value = initial;
		Ui.styleButton(this, Theme.SURFACE, Theme.TEXT);
		refresh();

		addActionListener(e -> {
			listening = true;
			refresh();
			requestFocusInWindow();
		});

		addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				listening = false;
				refresh();
			}
		});

		addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (!listening) return;
				int code = e.getKeyCode();
				if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT || code == KeyEvent.VK_META)
				{
					return; // auf die eigentliche Taste warten
				}
				e.consume();
				value = (code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_BACK_SPACE) ? Keybind.NOT_SET : new Keybind(e);
				listening = false;
				refresh();
				onChange.accept(value);
			}
		});
	}

	private void refresh()
	{
		if (listening)
		{
			setText("Taste druecken...");
			setForeground(Theme.PRIMARY);
		}
		else
		{
			boolean set = value != null && value != Keybind.NOT_SET;
			setText(set ? "Hotkey: " + value : "Hotkey setzen");
			setForeground(set ? Theme.TEXT : Theme.MUTED);
		}
	}
}
