package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.pvmhotkeys.PresetStore;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Preset;
import net.runelite.client.ui.PluginPanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Seitenpanel: Kopfbereich mit Statistik + "Neues Preset", darunter die Preset-Karten.
 * Alle Methoden hier laufen auf dem Swing-Thread (EDT).
 */
public class PvmHotkeysPanel extends PluginPanel
{
	private final PresetStore store;
	private final ItemManager itemManager;
	private final PanelActions actions;

	private final JPanel list = new JPanel();
	private final JLabel stats = new JLabel();
	private final Map<String, PresetCard> cards = new HashMap<>();
	private final Set<String> editing = new HashSet<>();

	public PvmHotkeysPanel(PresetStore store, ItemManager itemManager, PanelActions actions)
	{
		super(false);
		this.store = store;
		this.itemManager = itemManager;
		this.actions = actions;

		setLayout(new BorderLayout());
		setBackground(Theme.BG);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		add(header(), BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);

		JPanel listWrap = new JPanel(new BorderLayout());
		listWrap.setOpaque(false);
		listWrap.add(list, BorderLayout.NORTH);

		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(listWrap);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scroll, BorderLayout.CENTER);

		rebuild();
	}

	private JPanel header()
	{
		JPanel h = new JPanel();
		h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
		h.setOpaque(false);
		h.setBorder(new EmptyBorder(0, 0, 10, 0));

		JLabel title = Ui.label("PvM Switcher", Theme.title().deriveFont(20f), Theme.TEXT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		h.add(title);

		stats.setFont(Theme.small());
		stats.setForeground(Theme.MUTED);
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		h.add(stats);
		h.add(Box.createVerticalStrut(10));

		JButton add = new JButton("+  Neues Preset");
		Ui.styleButton(add, Theme.PRIMARY, Theme.TEXT);
		add.setFont(Theme.body());
		add.setAlignmentX(Component.LEFT_ALIGNMENT);
		add.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		add.addActionListener(e -> {
			Preset p = store.add();
			editing.add(p.getId());
			rebuild();
		});
		h.add(add);
		return h;
	}

	/** Baut alle Karten neu. Bei wenigen Presets schnell genug und immer konsistent. */
	public void rebuild()
	{
		// Offene Editoren merken, damit sie nach dem Neuaufbau offen bleiben
		for (PresetCard c : cards.values())
		{
			if (c.isEditing()) editing.add(c.presetId());
			else editing.remove(c.presetId());
		}
		list.removeAll();
		cards.clear();

		int withKey = 0;
		for (Preset p : store.all())
		{
			if (p.hasHotkey()) withKey++;
			PresetCard card = new PresetCard(p, itemManager, actions, editing.contains(p.getId()), this::relayout);
			cards.put(p.getId(), card);
			list.add(card);
			list.add(Box.createVerticalStrut(Theme.GAP));
		}

		if (store.all().isEmpty())
		{
			JLabel empty = Ui.label("<html><center>Noch keine Presets.<br>Leg dein erstes an!</center></html>",
				Theme.body(), Theme.MUTED);
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			empty.setBorder(new EmptyBorder(20, 0, 0, 0));
			list.add(empty);
		}

		stats.setText(store.all().size() + " Presets  |  " + withKey + " mit Hotkey");
		relayout();
	}

	private void relayout()
	{
		list.revalidate();
		list.repaint();
	}

	/** Vom Plugin aufgerufen (beliebiger Thread), wenn ein Preset ausgeloest wurde. */
	public void flash(Preset preset)
	{
		SwingUtilities.invokeLater(() -> {
			PresetCard c = cards.get(preset.getId());
			if (c != null) c.flash();
		});
	}
}
