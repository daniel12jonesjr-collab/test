package net.runelite.client.plugins.microbot.pvppresets;

import net.runelite.client.game.ItemManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/** Eine Preset-Karte: Kopfzeile, Gear-Vorschau, Prayer/Spell, Aktionen. */
class PresetCard extends JPanel {
    static final String[] PRAYERS = {"NONE", "PIETY", "CHIVALRY", "ULTIMATE_STRENGTH", "INCREDIBLE_REFLEXES",
            "RIGOUR", "EAGLE_EYE", "HAWK_EYE", "AUGURY", "MYSTIC_MIGHT", "MYSTIC_LORE"};
    static final String[] SPELLS = {"NONE", "BIND", "SNARE", "ENTANGLE", "ICE_RUSH", "ICE_BURST", "ICE_BLITZ",
            "ICE_BARRAGE", "BLOOD_BLITZ", "BLOOD_BARRAGE", "TELE_BLOCK"};

    interface Actions {
        void apply(ClientPreset p);
        void captureGear(ClientPreset p);
        void prayerChanged(ClientPreset p);
        void spellChanged(ClientPreset p);
        void renamed(ClientPreset p);
        void delete(ClientPreset p);
    }

    private final ClientPreset preset;
    private final ItemManager itemManager;
    private final JPanel gearGrid = new JPanel(new GridLayout(0, 6, 3, 3));

    PresetCard(ClientPreset preset, String hotkey, ItemManager itemManager, Actions actions) {
        this.preset = preset;
        this.itemManager = itemManager;
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Kopfzeile: Badge #n · Name · Hotkey-Chip
        JPanel head = row();
        JLabel badge = Theme.label(String.valueOf(preset.id), Theme.TITLE, Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(Theme.ACCENT);
        badge.setBorder(new EmptyBorder(2, 7, 2, 7));
        JTextField name = new JTextField(preset.name, 8);
        name.setFont(Theme.TITLE);
        name.setForeground(Theme.TEXT);
        name.setCaretColor(Theme.TEXT);
        name.setOpaque(false);
        name.setBorder(new EmptyBorder(0, 8, 0, 8));
        Runnable commitName = () -> { preset.name = name.getText().trim(); actions.renamed(preset); };
        name.addActionListener(e -> commitName.run());
        name.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { commitName.run(); } });
        JLabel hk = Theme.label(hotkey, Theme.SMALL, Theme.MUTED);
        hk.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(2, 6, 2, 6)));
        head.add(badge, BorderLayout.WEST);
        head.add(name, BorderLayout.CENTER);
        head.add(hk, BorderLayout.EAST);
        card.add(head);
        card.add(Box.createVerticalStrut(8));

        // Gear-Vorschau
        gearGrid.setOpaque(false);
        gearGrid.setAlignmentX(LEFT_ALIGNMENT);
        refreshGear();
        card.add(gearGrid);
        card.add(Box.createVerticalStrut(8));

        // Prayer + Spell
        JComboBox<String> pray = Theme.combo(PRAYERS);
        pray.setSelectedItem(preset.prayer);
        pray.addActionListener(e -> { preset.prayer = (String) pray.getSelectedItem(); actions.prayerChanged(preset); });
        JComboBox<String> spell = Theme.combo(SPELLS);
        spell.setSelectedItem(preset.spell);
        spell.addActionListener(e -> { preset.spell = (String) spell.getSelectedItem(); actions.spellChanged(preset); });
        card.add(labeled("Prayer", pray));
        card.add(Box.createVerticalStrut(4));
        card.add(labeled("Spell", spell));
        card.add(Box.createVerticalStrut(8));

        // Aktionen
        JPanel btns = new JPanel(new BorderLayout(6, 0));
        btns.setOpaque(false);
        JPanel main = new JPanel(new GridLayout(1, 2, 6, 0));
        main.setOpaque(false);
        JButton apply = Theme.button("Anziehen", Theme.GOOD);
        JButton set = Theme.button("Set gear", Theme.ACCENT);
        set.setToolTipText("Aktuelles Gear + aktive Prayer übernehmen (Set current gear)");
        JButton del = Theme.button("✕", Theme.DANGER);
        del.setToolTipText("Preset löschen");
        del.setPreferredSize(new Dimension(28, 24));
        apply.addActionListener(e -> actions.apply(preset));
        set.addActionListener(e -> actions.captureGear(preset));
        del.addActionListener(e -> actions.delete(preset));
        main.add(apply); main.add(set);
        btns.add(main, BorderLayout.CENTER);
        btns.add(del, BorderLayout.EAST);
        btns.setAlignmentX(LEFT_ALIGNMENT);
        card.add(btns);

        add(card, BorderLayout.CENTER);
    }

    void refreshGear() {
        gearGrid.removeAll();
        if (preset.gear.isEmpty()) {
            gearGrid.setLayout(new BorderLayout());
            gearGrid.add(Theme.label("Noch kein Gear – „Set gear“ drücken", Theme.SMALL, Theme.MUTED));
        } else {
            gearGrid.setLayout(new GridLayout(0, 6, 3, 3));
            for (int id : preset.gear) {
                JLabel icon = new JLabel();
                icon.setPreferredSize(new Dimension(32, 32));
                icon.setHorizontalAlignment(SwingConstants.CENTER);
                itemManager.getImage(id).addTo(icon);
                icon.setToolTipText(String.valueOf(id));
                gearGrid.add(icon);
            }
        }
        gearGrid.revalidate();
        gearGrid.repaint();
    }

    private static JPanel row() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel labeled(String text, JComponent c) {
        JPanel p = row();
        JLabel l = Theme.label(text, Theme.SMALL, Theme.MUTED);
        l.setPreferredSize(new Dimension(70, 20));
        p.add(l, BorderLayout.WEST);
        p.add(c, BorderLayout.CENTER);
        return p;
    }
}
