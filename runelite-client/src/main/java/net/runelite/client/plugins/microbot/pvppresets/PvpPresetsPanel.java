package net.runelite.client.plugins.microbot.pvppresets;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

/** Seitenpanel: Presets oben, Auto-Sustain unten. */
class PvpPresetsPanel extends PluginPanel {
    interface SustainActions {
        void toggled(boolean on);
        void eatAt(int pct);
        void prayerAt(int pts);
        void combo(boolean on);
        void buff(String skill, int minBoost);
    }

    interface OpenerActions {
        void changed();
        void fire();
        void armed(boolean on);
    }

    private final JCheckBox armBox = new JCheckBox("Auto-Opener scharf");

    private final JPanel list = new Theme.WidthTrackingPanel();
    private final ItemManager itemManager;
    private final JToggleButton sustainToggle = new JToggleButton();

    PvpPresetsPanel(ItemManager itemManager) {
        super(false);
        this.itemManager = itemManager;
        setBackground(Theme.BG);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    void rebuild(List<ClientPreset> presets, Function<Integer, String> hotkeyText, PresetCard.Actions actions,
                 Runnable addPreset, SustainState sustain, SustainActions sustainActions,
                 OpenerState opener, String openerHotkey, OpenerActions openerActions) {
        list.removeAll();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.add(Theme.label("PvP Presets", new Font("SansSerif", Font.BOLD, 16), Theme.TEXT), BorderLayout.WEST);
        JButton add = Theme.button("+ Neu", Theme.ACCENT);
        add.addActionListener(e -> addPreset.run());
        add.setEnabled(presets.size() < 8);
        header.add(add, BorderLayout.EAST);
        header.setBorder(new EmptyBorder(0, 0, 10, 0));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        list.add(header);

        list.add(openerCard(opener, openerHotkey, presets, openerActions));

        for (ClientPreset p : presets) {
            PresetCard card = new PresetCard(p, hotkeyText.apply(p.id), itemManager, actions);
            card.setAlignmentX(LEFT_ALIGNMENT);
            list.add(card);
        }

        list.add(sustainCard(sustain, sustainActions));
        list.revalidate();
        list.repaint();
    }

    void setSustainEnabled(boolean on) {
        sustainToggle.setSelected(on);
        styleToggle();
    }

    private JPanel sustainCard(SustainState s, SustainActions a) {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Theme.label("Auto-Sustain", Theme.TITLE, Theme.TEXT), BorderLayout.WEST);
        sustainToggle.setSelected(s.enabled);
        sustainToggle.setFocusPainted(false);
        sustainToggle.setFont(Theme.SMALL);
        for (var l : sustainToggle.getActionListeners()) sustainToggle.removeActionListener(l);
        sustainToggle.addActionListener(e -> { styleToggle(); a.toggled(sustainToggle.isSelected()); });
        styleToggle();
        head.add(sustainToggle, BorderLayout.EAST);
        head.setAlignmentX(LEFT_ALIGNMENT);
        card.add(head);
        card.add(Box.createVerticalStrut(8));

        card.add(spinnerRow("Essen bei HP %", s.eatAt, 0, 99, a::eatAt));
        card.add(spinnerRow("Restore bei Prayer", s.prayerAt, 0, 99, a::prayerAt));
        JCheckBox combo = new JCheckBox("Combo-Eat (Karambwan)", s.combo);
        combo.setOpaque(false);
        combo.setForeground(Theme.TEXT);
        combo.setFont(Theme.BODY);
        combo.addActionListener(e -> a.combo(combo.isSelected()));
        combo.setAlignmentX(LEFT_ALIGNMENT);
        card.add(combo);
        card.add(Box.createVerticalStrut(6));
        card.add(Theme.label("Dauer-Buffs (min. Boost, 0 = aus)", Theme.SMALL, Theme.MUTED));
        card.add(Box.createVerticalStrut(4));
        s.buffs.forEach((skill, v) -> card.add(spinnerRow(cap(skill), v, 0, 20, n -> a.buff(skill, n))));
        return card;
    }

    private JPanel openerCard(OpenerState o, String hotkey, List<ClientPreset> presets, OpenerActions a) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.setBorder(new EmptyBorder(0, 0, 8, 0));
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setAlignmentX(LEFT_ALIGNMENT);
        head.add(Theme.label("⚡ Opener", Theme.TITLE, Theme.ACCENT), BorderLayout.WEST);
        JLabel hk = Theme.label(hotkey, Theme.SMALL, Theme.MUTED);
        hk.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(2, 6, 2, 6)));
        head.add(hk, BorderLayout.EAST);
        card.add(head);
        card.add(Box.createVerticalStrut(2));
        card.add(Theme.label("Hotkey: Ziel unter der Maus", Theme.SMALL, Theme.MUTED));
        card.add(Theme.label("Auto: erster angreifbarer Gegner", Theme.SMALL, Theme.MUTED));
        card.add(Box.createVerticalStrut(6));

        armBox.setOpaque(false);
        armBox.setToolTipText("Feuert den Opener auf den ersten angreifbaren Gegner. Geht erst aus, wenn der Teleblock sitzt.");
        armBox.setFont(Theme.BODY);
        armBox.setFocusable(false);
        armBox.setAlignmentX(LEFT_ALIGNMENT);
        for (var l : armBox.getActionListeners()) armBox.removeActionListener(l);
        armBox.addActionListener(e -> { styleArm(); a.armed(armBox.isSelected()); });
        styleArm();
        card.add(armBox);
        card.add(Box.createVerticalStrut(8));

        String[] presetNames = new String[presets.size() + 1];
        presetNames[0] = "—";
        for (int i = 0; i < presets.size(); i++) presetNames[i + 1] = presets.get(i).id + " · " + presets.get(i).name;

        card.add(comboRow("Setup davor", presetNames, indexOf(presets, o.gearPreset),
                i -> { o.gearPreset = i == 0 ? 0 : presets.get(i - 1).id; a.changed(); }));
        card.add(comboRow("1. Spell", PresetCard.SPELLS, o.spell1, v -> { o.spell1 = v; a.changed(); }));
        card.add(comboRow("2. Spell", PresetCard.SPELLS, o.spell2, v -> { o.spell2 = v; a.changed(); }));
        card.add(comboRow("3. Spell", PresetCard.SPELLS, o.spell3, v -> { o.spell3 = v; a.changed(); }));
        card.add(comboRow("Danach", presetNames, indexOf(presets, o.followPreset),
                i -> { o.followPreset = i == 0 ? 0 : presets.get(i - 1).id; a.changed(); }));
        card.add(Box.createVerticalStrut(8));

        JButton fire = Theme.button("Opener auslösen", Theme.ACCENT);
        fire.setAlignmentX(LEFT_ALIGNMENT);
        fire.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        fire.addActionListener(e -> a.fire());
        card.add(fire);
        wrap.add(card, BorderLayout.CENTER);
        return wrap;
    }

    /** Vom Plugin aufgerufen, wenn der Server den Auto-Opener ausgelöst hat. */
    void setArmed(boolean on) {
        armBox.setSelected(on);
        styleArm();
    }

    private void styleArm() {
        armBox.setForeground(armBox.isSelected() ? new Color(0xff, 0xaa, 0x33) : Theme.TEXT);
    }

    private static int indexOf(List<ClientPreset> presets, int id) {
        for (int i = 0; i < presets.size(); i++) if (presets.get(i).id == id) return i + 1;
        return 0;
    }

    private static JPanel comboRow(String text, String[] items, String selected, java.util.function.Consumer<String> onChange) {
        JComboBox<String> c = Theme.combo(items);
        c.setSelectedItem(selected);
        c.addActionListener(e -> onChange.accept((String) c.getSelectedItem()));
        return rowOf(text, c);
    }

    private static JPanel comboRow(String text, String[] items, int selectedIndex, java.util.function.IntConsumer onChange) {
        JComboBox<String> c = Theme.combo(items);
        c.setSelectedIndex(selectedIndex);
        c.addActionListener(e -> onChange.accept(c.getSelectedIndex()));
        return rowOf(text, c);
    }

    private static JPanel rowOf(String text, JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(new EmptyBorder(1, 0, 1, 0));
        JLabel l = Theme.label(text, Theme.SMALL, Theme.MUTED);
        l.setPreferredSize(new Dimension(70, 20));
        p.add(l, BorderLayout.WEST);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private void styleToggle() {
        boolean on = sustainToggle.isSelected();
        sustainToggle.setText(on ? "AN" : "AUS");
        sustainToggle.setBackground(on ? Theme.GOOD : Theme.CARD_HI);
        sustainToggle.setForeground(on ? Color.BLACK : Theme.MUTED);
    }

    private static JPanel spinnerRow(String text, int value, int min, int max, java.util.function.IntConsumer onChange) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(new EmptyBorder(1, 0, 1, 0));
        p.add(Theme.label(text, Theme.BODY, Theme.TEXT), BorderLayout.CENTER);
        JSpinner sp = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
        sp.setPreferredSize(new Dimension(52, 22));
        sp.addChangeListener(e -> onChange.accept((Integer) sp.getValue()));
        p.add(sp, BorderLayout.EAST);
        return p;
    }

    private static String cap(String s) { return s.charAt(0) + s.substring(1).toLowerCase(); }
}
