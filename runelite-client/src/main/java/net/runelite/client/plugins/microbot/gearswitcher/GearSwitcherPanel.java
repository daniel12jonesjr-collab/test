package net.runelite.client.plugins.microbot.gearswitcher;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Side panel: one card per gear set, with the gear shown as item icons. "Use worn gear" stores everything
 * currently equipped as the set, the hotkey chip records the next key press. Everything is written to the
 * plugin config (the set entries are hidden in the regular config panel, this is the place to edit them).
 */
class GearSwitcherPanel extends PluginPanel {

    private static final int SET_COUNT = 3;

    private static final Color BG = new Color(22, 22, 26);
    private static final Color CARD = new Color(34, 34, 40);
    private static final Color SURFACE = new Color(48, 48, 56);
    private static final Color BORDER = new Color(58, 58, 68);
    private static final Color TEXT = new Color(236, 236, 242);
    private static final Color MUTED = new Color(146, 146, 158);
    private static final Color MELEE = new Color(235, 90, 80);
    private static final Color RANGED = new Color(90, 200, 110);
    private static final Color MAGIC = new Color(90, 150, 255);
    private static final Color NEUTRAL = new Color(255, 160, 60);

    private final GearSwitcherConfig config;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final SetCard[] cards = new SetCard[SET_COUNT];
    private final SuppliesCard suppliesCard = new SuppliesCard();

    GearSwitcherPanel(GearSwitcherConfig config, ConfigManager configManager, ItemManager itemManager) {
        super(false);
        this.config = config;
        this.configManager = configManager;
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Gear Switcher");
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
        title.setForeground(TEXT);
        content.add(left(title));
        JLabel subtitle = new JLabel("PvM only, off while a player is involved");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(MUTED);
        content.add(left(subtitle));
        content.add(Box.createVerticalStrut(10));

        content.add(suppliesCard);
        content.add(Box.createVerticalStrut(8));

        for (int i = 0; i < SET_COUNT; i++) {
            cards[i] = new SetCard(i + 1);
            content.add(cards[i]);
            content.add(Box.createVerticalStrut(8));
        }

        JLabel hint = new JLabel("<html>Wear the gear and click <b>Use worn gear</b>. "
                + "Click the key chip and press a key, Esc clears it.</html>");
        hint.setFont(FontManager.getRunescapeSmallFont());
        hint.setForeground(MUTED);
        content.add(left(hint));

        add(content, BorderLayout.NORTH);
        refresh();
    }

    /** Re-reads every set from the config. EDT only. */
    void refresh() {
        suppliesCard.refresh();
        for (SetCard card : cards) card.refresh();
    }

    /** Auto eat and potions: an on/off switch and a one-line summary; details are in the Supplies config section. */
    private class SuppliesCard extends RoundCard {
        private final JButton toggle = new JButton();
        private final JLabel summary = new JLabel();

        SuppliesCard() {
            setLayout(new BorderLayout(0, 6));
            setBorder(new EmptyBorder(9, 12, 9, 10));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel("Supplies");
            title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
            title.setForeground(TEXT);
            header.add(title, BorderLayout.WEST);
            toggle.addActionListener(e -> configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "autoSupplies",
                    !config.autoSupplies()));
            header.add(toggle, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            summary.setFont(FontManager.getRunescapeSmallFont());
            add(summary, BorderLayout.CENTER);
        }

        void refresh() {
            boolean on = config.autoSupplies();
            accent = on ? RANGED : MUTED;
            toggle.setText(on ? "● On" : "○ Off");
            styleButton(toggle, on ? new Color(60, 90, 60) : SURFACE, on ? TEXT : MUTED);
            toggle.setToolTipText(on ? "Stop eating and drinking by itself" : "Eat and drink by itself while fighting monsters");

            List<String> parts = new ArrayList<>();
            if (config.eatAtPercent() > 0) parts.add("Eat at " + config.eatAtPercent() + "%");
            if (config.prayerAt() > 0) parts.add("Prayer at " + config.prayerAt());
            List<String> boosts = new ArrayList<>();
            if (config.boostRanged()) boosts.add("Ranged");
            if (config.boostMelee()) boosts.add("Melee");
            if (config.boostMagic()) boosts.add("Magic");
            if (!boosts.isEmpty()) parts.add("Boost " + String.join(", ", boosts));
            if (config.restoreAfterBrews() > 0) parts.add("Restore every " + config.restoreAfterBrews() + " brews");
            summary.setText("<html>" + (parts.isEmpty() ? "Nothing set" : String.join("  ·  ", parts))
                    + "<br><font color='#92929e'>Food: " + (config.foods().isBlank() ? "any" : config.foods())
                    + "<br>Change it under Supplies in the plugin settings</font></html>");
            summary.setForeground(on ? TEXT : MUTED);
            repaint();
        }
    }

    /** Toolbar icon: a small orange shield. */
    static BufferedImage icon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int[] xs = {2, 14, 14, 8, 2};
        int[] ys = {2, 2, 9, 15, 9};
        g.setColor(NEUTRAL);
        g.fillPolygon(xs, ys, xs.length);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(8, 4, 8, 12);
        g.drawLine(5, 7, 11, 7);
        g.dispose();
        return image;
    }

    private static Component left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    /** Colour of a set, taken from its prayer: red melee, green ranged, blue magic, orange without one. */
    private static Color styleColor(GearSwitcherConfig.OffensivePrayer prayer) {
        switch (prayer) {
            case PIETY: case CHIVALRY: case ULTIMATE_STRENGTH: case INCREDIBLE_REFLEXES:
                return MELEE;
            case RIGOUR: case EAGLE_EYE: case DEAD_EYE: case HAWK_EYE:
                return RANGED;
            case AUGURY: case MYSTIC_MIGHT: case MYSTIC_VIGOUR: case MYSTIC_LORE:
                return MAGIC;
            default:
                return NEUTRAL;
        }
    }

    private static String pretty(Enum<?> value) {
        String s = value.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void styleButton(JButton b, Color bg, Color fg) {
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setFocusPainted(false);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true), new EmptyBorder(5, 8, 5, 8)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Rounded card with a coloured strip on the left. */
    private static class RoundCard extends JPanel {
        Color accent = NEUTRAL;

        RoundCard() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(accent);
            g2.fillRoundRect(0, 0, 4, getHeight() - 1, 4, 4);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** One item icon in a dark rounded tile, name as tooltip. */
    private static class ItemTile extends JLabel {
        ItemTile() {
            setPreferredSize(new Dimension(40, 36));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SURFACE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private class SetCard extends RoundCard {
        private final int number;
        private final JLabel name = new JLabel();
        private final JButton hotkeyButton = new JButton();
        private final JComboBox<GearSwitcherConfig.OffensivePrayer> prayerBox =
                new JComboBox<>(GearSwitcherConfig.OffensivePrayer.values());
        private final JComboBox<GearSwitcherConfig.Spell> spellBox =
                new JComboBox<>(GearSwitcherConfig.Spell.values());
        private final JPanel itemGrid = new JPanel(new GridLayout(0, 4, 4, 4));
        private final JLabel status = new JLabel(" ");
        private boolean listening;
        /** Set while refresh() fills the combo boxes, so that doesn't write back to the config. */
        private boolean updating;

        SetCard(int number) {
            this.number = number;
            setLayout(new BorderLayout(0, 8));
            setBorder(new EmptyBorder(9, 12, 9, 10));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            // Header: name and hotkey chip
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            name.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
            name.setForeground(TEXT);
            header.add(name, BorderLayout.WEST);
            styleButton(hotkeyButton, SURFACE, TEXT);
            hotkeyButton.setToolTipText("Click, then press the key for this set. Esc clears it");
            hotkeyButton.addActionListener(e -> startListening());
            hotkeyButton.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (listening) recordKey(e);
                }
            });
            hotkeyButton.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    listening = false;
                    refresh();
                }
            });
            header.add(hotkeyButton, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            // Middle: gear icons, then prayer and spell
            JPanel middle = new JPanel();
            middle.setOpaque(false);
            middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
            itemGrid.setOpaque(false);
            itemGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            middle.add(itemGrid);
            middle.add(Box.createVerticalStrut(8));
            middle.add(labeled("Prayer", prayerBox, "Offensive prayer turned on with this set"));
            middle.add(Box.createVerticalStrut(4));
            middle.add(labeled("Spell", spellBox, "Spell cast on the monster you are fighting right after the switch"));
            prayerBox.setRenderer(enumRenderer());
            spellBox.setRenderer(enumRenderer());
            prayerBox.addActionListener(e -> {
                if (!updating) {
                    configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Prayer",
                            prayerBox.getSelectedItem());
                }
            });
            spellBox.addActionListener(e -> {
                if (!updating) {
                    configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Spell",
                            spellBox.getSelectedItem());
                }
            });
            add(middle, BorderLayout.CENTER);

            // Footer: buttons and a status line
            JPanel footer = new JPanel(new BorderLayout(0, 4));
            footer.setOpaque(false);
            JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
            buttons.setOpaque(false);
            JButton useWorn = new JButton("Use worn gear");
            styleButton(useWorn, new Color(60, 90, 60), TEXT);
            useWorn.setToolTipText("Store everything you are wearing right now as this set");
            useWorn.addActionListener(e -> useWornGear());
            JButton clear = new JButton("Clear");
            styleButton(clear, SURFACE, MUTED);
            clear.addActionListener(e -> {
                configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Items", "");
                configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "ItemIds", "");
                status.setText(" ");
            });
            buttons.add(useWorn);
            buttons.add(clear);
            footer.add(buttons, BorderLayout.NORTH);
            status.setFont(FontManager.getRunescapeSmallFont());
            status.setForeground(MUTED);
            footer.add(status, BorderLayout.SOUTH);
            add(footer, BorderLayout.SOUTH);
        }

        private JPanel labeled(String text, JComboBox<?> box, String tooltip) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel label = new JLabel(text);
            label.setFont(FontManager.getRunescapeSmallFont());
            label.setForeground(MUTED);
            label.setPreferredSize(new Dimension(42, label.getPreferredSize().height));
            box.setToolTipText(tooltip);
            box.setFont(FontManager.getRunescapeSmallFont());
            row.add(label, BorderLayout.WEST);
            row.add(box, BorderLayout.CENTER);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
            return row;
        }

        void refresh() {
            updating = true;
            GearSwitcherConfig.OffensivePrayer prayer = prayer();
            prayerBox.setSelectedItem(prayer);
            spellBox.setSelectedItem(spell());
            updating = false;

            accent = styleColor(prayer);
            name.setText("Set " + number);
            name.setForeground(accent);

            if (!listening) {
                Keybind key = hotkey();
                hotkeyButton.setText(key == null || Keybind.NOT_SET.equals(key) ? "Set key" : "⌨ " + key);
            }

            itemGrid.removeAll();
            List<String> items = Arrays.stream(items().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            List<Integer> ids = itemIds();
            if (items.isEmpty()) {
                JLabel empty = new JLabel("No gear yet");
                empty.setFont(FontManager.getRunescapeSmallFont());
                empty.setForeground(MUTED);
                itemGrid.add(empty);
            }
            for (int i = 0; i < items.size(); i++) {
                String item = items.get(i);
                ItemTile tile = new ItemTile();
                tile.setToolTipText(item);
                int id = i < ids.size() ? ids.get(i) : lookupId(item);
                if (id > 0) {
                    itemManager.getImage(id).addTo(tile);
                } else {
                    // No icon found (untradeable item without a saved id): show the first letters instead
                    tile.setText(item.length() > 4 ? item.substring(0, 4) : item);
                    tile.setFont(FontManager.getRunescapeSmallFont());
                    tile.setForeground(TEXT);
                }
                itemGrid.add(tile);
            }
            itemGrid.revalidate();
            repaint();
        }

        private int lookupId(String itemName) {
            for (ItemPrice p : itemManager.search(itemName)) {
                if (p.getName().equalsIgnoreCase(itemName)) return p.getId();
            }
            return -1;
        }

        private void startListening() {
            listening = true;
            hotkeyButton.setText("Press a key...");
            hotkeyButton.requestFocusInWindow();
        }

        private void recordKey(KeyEvent e) {
            int code = e.getKeyCode();
            // Wait for the real key when only a modifier is held so far
            if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
                    || code == KeyEvent.VK_META || code == KeyEvent.VK_UNDEFINED) {
                return;
            }
            Keybind key = code == KeyEvent.VK_ESCAPE ? Keybind.NOT_SET : new Keybind(e);
            listening = false;
            e.consume();
            configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Hotkey", key);
            refresh();
        }

        private void useWornGear() {
            List<Rs2ItemModel> worn = Rs2Equipment.items();
            if (worn == null || worn.isEmpty()) {
                status.setText("Nothing worn (logged in?)");
                return;
            }
            List<Rs2ItemModel> distinct = new ArrayList<>();
            for (Rs2ItemModel item : worn) {
                if (distinct.stream().noneMatch(d -> d.getName().equals(item.getName()))) distinct.add(item);
            }
            String names = distinct.stream().map(Rs2ItemModel::getName).collect(Collectors.joining(", "));
            String ids = distinct.stream().map(i -> String.valueOf(i.getId())).collect(Collectors.joining(","));
            configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Items", names);
            // Saved next to the names so the panel can show icons, also for untradeable items
            configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "ItemIds", ids);
            status.setText(distinct.size() + " items saved");
        }

        private List<Integer> itemIds() {
            String raw = configManager.getConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "ItemIds");
            List<Integer> ids = new ArrayList<>();
            if (raw == null || raw.isEmpty()) return ids;
            for (String s : raw.split(",")) {
                try {
                    ids.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {
                    ids.add(-1);
                }
            }
            return ids;
        }

        private Keybind hotkey() {
            switch (number) {
                case 1: return config.set1Hotkey();
                case 2: return config.set2Hotkey();
                default: return config.set3Hotkey();
            }
        }

        private String items() {
            switch (number) {
                case 1: return config.set1Items();
                case 2: return config.set2Items();
                default: return config.set3Items();
            }
        }

        private GearSwitcherConfig.OffensivePrayer prayer() {
            switch (number) {
                case 1: return config.set1Prayer();
                case 2: return config.set2Prayer();
                default: return config.set3Prayer();
            }
        }

        private GearSwitcherConfig.Spell spell() {
            switch (number) {
                case 1: return config.set1Spell();
                case 2: return config.set2Spell();
                default: return config.set3Spell();
            }
        }
    }

    /** Shows enum values as "Ultimate strength" instead of "ULTIMATE_STRENGTH". */
    private static javax.swing.ListCellRenderer<Object> enumRenderer() {
        javax.swing.DefaultListCellRenderer base = new javax.swing.DefaultListCellRenderer();
        return (list, value, index, selected, focus) -> base.getListCellRendererComponent(
                list, value instanceof Enum ? pretty((Enum<?>) value) : value, index, selected, focus);
    }
}
