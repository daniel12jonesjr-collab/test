package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Preset;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.PresetAction;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Spells;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Eine Karte pro Preset.
 *  - Kopf: Name + Hotkey-Badge
 *  - Aktion (farbig), Gear-Icons, Gebets-Chips
 *  - Buttons: Set | Bearbeiten | hoch/runter | loeschen
 *  - Ausklappbarer Editor
 */
class PresetCard extends RoundedPanel
{
	/** Gebete, die fuer PvM-Switches relevant sind (Kurzname fuer die Chips). */
	static final Map<Rs2PrayerEnum, String> PRAYERS = new LinkedHashMap<>();

	static
	{
		PRAYERS.put(Rs2PrayerEnum.PROTECT_MAGIC, "Prot Mage");
		PRAYERS.put(Rs2PrayerEnum.PROTECT_RANGE, "Prot Range");
		PRAYERS.put(Rs2PrayerEnum.PROTECT_MELEE, "Prot Melee");
		PRAYERS.put(Rs2PrayerEnum.AUGURY, "Augury");
		PRAYERS.put(Rs2PrayerEnum.RIGOUR, "Rigour");
		PRAYERS.put(Rs2PrayerEnum.PIETY, "Piety");
		PRAYERS.put(Rs2PrayerEnum.MYSTIC_MIGHT, "Mystic Might");
		PRAYERS.put(Rs2PrayerEnum.EAGLE_EYE, "Eagle Eye");
		PRAYERS.put(Rs2PrayerEnum.CHIVALRY, "Chivalry");
		PRAYERS.put(Rs2PrayerEnum.PROTECT_ITEM, "Prot Item");
		PRAYERS.put(Rs2PrayerEnum.REDEMPTION, "Redemption");
		PRAYERS.put(Rs2PrayerEnum.RAPID_HEAL, "Rapid Heal");
	}

	private final Preset preset;
	private final ItemManager itemManager;
	private final PanelActions actions;
	private final Runnable relayout;
	private boolean editing;
	private Timer flashTimer;

	PresetCard(Preset preset, ItemManager itemManager, PanelActions actions, boolean editing, Runnable relayout)
	{
		super(null, Theme.CARD, Theme.RADIUS);
		this.preset = preset;
		this.itemManager = itemManager;
		this.actions = actions;
		this.editing = editing;
		this.relayout = relayout;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(new EmptyBorder(10, 14, 10, 10));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { setFill(Theme.CARD_HOVER); }

			@Override
			public void mouseExited(MouseEvent e) { setFill(Theme.CARD); }
		});

		build();
	}

	boolean isEditing()
	{
		return editing;
	}

	String presetId()
	{
		return preset.getId();
	}

	/** Kurzes Aufleuchten, wenn der Hotkey ausgeloest wurde. */
	void flash()
	{
		if (flashTimer != null) flashTimer.stop();
		final int[] step = {0};
		flashTimer = new Timer(16, e -> {
			step[0]++;
			int a = 255 - step[0] * 12;
			if (a <= 0)
			{
				setGlow(null);
				((Timer) e.getSource()).stop();
			}
			else
			{
				setGlow(Theme.alpha(preset.getAction().getAccent(), a));
			}
		});
		flashTimer.start();
	}

	private void build()
	{
		removeAll();
		setAccent(preset.getAction().getAccent());

		add(header());
		add(Box.createVerticalStrut(4));
		add(actionLine());
		add(Box.createVerticalStrut(Theme.GAP));
		add(gearGrid());

		if (!preset.getPrayers().isEmpty())
		{
			add(Box.createVerticalStrut(Theme.GAP));
			add(prayerRow());
		}

		add(Box.createVerticalStrut(Theme.GAP));
		add(buttonRow());

		if (editing)
		{
			add(Box.createVerticalStrut(Theme.GAP));
			add(editor());
		}
		revalidate();
		repaint();
	}

	// ------------------------------------------------------------------ Anzeige

	private JComponent header()
	{
		JPanel row = transparent(new BorderLayout(6, 0));
		JLabel name = Ui.label(preset.getName(), Theme.title(), Theme.TEXT);
		row.add(name, BorderLayout.CENTER);

		JLabel badge = Ui.label(preset.hasHotkey() ? preset.toKeybind().toString() : "kein Hotkey",
			Theme.small(), preset.hasHotkey() ? Theme.TEXT : Theme.MUTED);
		badge.setOpaque(true);
		badge.setBackground(Theme.SURFACE);
		badge.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(preset.hasHotkey() ? Theme.PRIMARY : Theme.BORDER, 1, true),
			new EmptyBorder(2, 6, 2, 6)));
		row.add(badge, BorderLayout.EAST);
		return row;
	}

	private JComponent actionLine()
	{
		PresetAction a = preset.getAction();
		String text;
		switch (a)
		{
			case CAST_SPELL:
				MagicAction s = preset.spellOrNull();
				int freeze = Spells.freezeTicks(s);
				text = "Zauber: " + (s == null ? "-" : s.getName())
					+ (freeze > 0 ? "  |  Freeze " + Math.round(freeze * 0.6) + "s" : "");
				break;
			case SPECIAL_ATTACK:
				text = "Special Attack (" + preset.getSpecPercent() + "%)";
				break;
			case ATTACK:
				text = "Angriff auf Ziel";
				break;
			default:
				text = "Nur Gear + Gebet";
		}
		JPanel row = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.add(Ui.label(text, Theme.body(), a.getAccent()));
		return row;
	}

	private JComponent gearGrid()
	{
		if (preset.getGear().isEmpty())
		{
			JPanel row = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
			row.add(Ui.label("Keine Ausruestung - Gear anziehen und \"Set\" druecken", Theme.small(), Theme.MUTED));
			return row;
		}
		JPanel grid = transparent(new GridLayout(0, 5, 4, 4));
		for (int id : preset.getGear())
		{
			JLabel icon = new JLabel();
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			icon.setPreferredSize(new Dimension(36, 32));
			icon.setOpaque(true);
			icon.setBackground(Theme.SURFACE);
			icon.setToolTipText("Item-ID " + id);
			itemManager.getImage(id).addTo(icon);
			grid.add(icon);
		}
		return grid;
	}

	private JComponent prayerRow()
	{
		JPanel row = transparent(new WrapLayout(FlowLayout.LEFT, 4, 4));
		for (Rs2PrayerEnum p : preset.prayerEnums())
		{
			row.add(new Chip(PRAYERS.getOrDefault(p, p.getName()), Theme.PRAYER, false));
		}
		return row;
	}

	private JComponent buttonRow()
	{
		JPanel row = transparent(new GridLayout(1, 0, 4, 0));

		JButton set = new JButton("Set");
		Ui.styleButton(set, Theme.PRIMARY, Theme.TEXT);
		set.setToolTipText("Aktuell angelegte Ausruestung fuer dieses Preset speichern");
		set.addActionListener(e -> actions.captureGear(preset));
		row.add(set);

		JButton edit = new JButton(editing ? "Fertig" : "Bearbeiten");
		Ui.styleButton(edit, Theme.SURFACE, Theme.TEXT);
		edit.addActionListener(e -> {
			editing = !editing;
			build();
			relayout.run();
		});
		row.add(edit);

		JButton up = new JButton("^");
		Ui.styleButton(up, Theme.SURFACE, Theme.MUTED);
		up.setToolTipText("Nach oben");
		up.addActionListener(e -> actions.move(preset, -1));
		row.add(up);

		JButton down = new JButton("v");
		Ui.styleButton(down, Theme.SURFACE, Theme.MUTED);
		down.setToolTipText("Nach unten");
		down.addActionListener(e -> actions.move(preset, 1));
		row.add(down);

		return row;
	}

	// ------------------------------------------------------------------ Editor

	private JComponent editor()
	{
		RoundedPanel box = new RoundedPanel(null, Theme.BG, Theme.RADIUS);
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBorder(new EmptyBorder(10, 10, 10, 10));
		box.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Name
		box.add(section("Name"));
		JTextField name = new JTextField(preset.getName());
		name.setFont(Theme.body());
		name.addActionListener(e -> commitName(name));
		name.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e) { commitName(name); }
		});
		box.add(full(name));

		// Hotkey
		box.add(section("Hotkey"));
		box.add(full(new HotkeyButton(preset.toKeybind(), kb -> {
			preset.setKeybind(kb);
			actions.save();
			build();
		})));

		// Aktion
		box.add(section("Aktion"));
		JComboBox<PresetAction> action = new JComboBox<>(PresetAction.values());
		action.setSelectedItem(preset.getAction());
		action.addActionListener(e -> {
			preset.setAction((PresetAction) action.getSelectedItem());
			actions.save();
			build();
			relayout.run();
		});
		box.add(full(action));

		if (preset.getAction() == PresetAction.CAST_SPELL)
		{
			box.add(section("Zauber"));
			JComboBox<MagicAction> spell = new JComboBox<>(Spells.options().toArray(new MagicAction[0]));
			spell.setMaximumRowCount(16);
			spell.setRenderer(new javax.swing.DefaultListCellRenderer()
			{
				@Override
				public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean sel, boolean focus)
				{
					super.getListCellRendererComponent(list, value, index, sel, focus);
					MagicAction a = (MagicAction) value;
					setText(Spells.label(a));
					if (!sel && Spells.freezeTicks(a) > 0) setForeground(new java.awt.Color(170, 110, 255));
					return this;
				}
			});
			spell.setSelectedItem(preset.spellOrNull());
			spell.addActionListener(e -> {
				MagicAction s = (MagicAction) spell.getSelectedItem();
				preset.setSpell(s == null ? null : s.name());
				actions.save();
				build();
			});
			box.add(full(spell));
		}
		else if (preset.getAction() == PresetAction.SPECIAL_ATTACK)
		{
			box.add(section("Spec-Energie (%)"));
			JSpinner spec = new JSpinner(new SpinnerNumberModel(preset.getSpecPercent(), 5, 100, 5));
			spec.addChangeListener(e -> {
				preset.setSpecPercent((Integer) spec.getValue());
				actions.save();
			});
			box.add(full(spec));
		}

		// Gebete
		box.add(section("Gebete"));
		JPanel chips = transparent(new WrapLayout(FlowLayout.LEFT, 4, 4));
		for (Map.Entry<Rs2PrayerEnum, String> entry : PRAYERS.entrySet())
		{
			Chip chip = new Chip(entry.getValue(), Theme.PRAYER, true);
			chip.setSelected(preset.getPrayers().contains(entry.getKey().name()));
			chip.addActionListener(e -> {
				// Neue Liste statt Aendern der alten: der Script-Thread liest evtl. gerade die alte
				java.util.List<String> next = new java.util.ArrayList<>(preset.getPrayers());
				if (chip.isSelected()) next.add(entry.getKey().name());
				else next.remove(entry.getKey().name());
				preset.setPrayers(next);
				actions.save();
			});
			chips.add(chip);
		}
		box.add(chips);

		// Gear leeren + Loeschen
		box.add(Box.createVerticalStrut(Theme.GAP));
		JPanel danger = transparent(new GridLayout(1, 2, 4, 0));
		JButton clearGear = new JButton("Gear leeren");
		Ui.styleButton(clearGear, Theme.SURFACE, Theme.MUTED);
		clearGear.addActionListener(e -> {
			preset.setGear(new java.util.ArrayList<>());
			actions.save();
			build();
			relayout.run();
		});
		danger.add(clearGear);

		JButton delete = new JButton("Loeschen");
		Ui.styleButton(delete, Theme.alpha(Theme.DANGER, 200), Theme.TEXT);
		delete.addActionListener(e -> actions.delete(preset));
		danger.add(delete);
		box.add(danger);

		return box;
	}

	private void commitName(JTextField field)
	{
		String n = field.getText().trim();
		if (!n.isEmpty() && !n.equals(preset.getName()))
		{
			preset.setName(n);
			actions.save();
			build();
		}
	}

	// ------------------------------------------------------------------ Layout-Helfer

	private static JPanel transparent(java.awt.LayoutManager layout)
	{
		JPanel p = new JPanel(layout);
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JComponent section(String title)
	{
		JPanel row = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setBorder(new EmptyBorder(8, 0, 4, 0));
		row.add(Ui.label(title.toUpperCase(), Theme.small(), Theme.MUTED));
		return row;
	}

	private static JComponent full(JComponent c)
	{
		JPanel wrap = transparent(new BorderLayout());
		wrap.add(c, BorderLayout.CENTER);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return wrap;
	}
}
