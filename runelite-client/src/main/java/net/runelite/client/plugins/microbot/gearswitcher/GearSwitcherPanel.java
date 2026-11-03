package net.runelite.client.plugins.microbot.gearswitcher;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Side panel: one card per gear set. "Use worn gear" stores everything currently equipped as the set,
 * the hotkey button records the next key press. Everything is written to the normal plugin config,
 * so the regular config panel and this one always show the same values.
 */
class GearSwitcherPanel extends PluginPanel {

    private static final int SET_COUNT = 3;

    private final GearSwitcherConfig config;
    private final ConfigManager configManager;
    private final SetCard[] cards = new SetCard[SET_COUNT];

    GearSwitcherPanel(GearSwitcherConfig config, ConfigManager configManager) {
        this.config = config;
        this.configManager = configManager;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Gear Switcher");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        content.add(left(title));
        JLabel subtitle = new JLabel("PvM only, off while a player is involved");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        content.add(left(subtitle));
        content.add(Box.createVerticalStrut(10));

        for (int i = 0; i < SET_COUNT; i++) {
            cards[i] = new SetCard(i + 1);
            content.add(cards[i]);
            content.add(Box.createVerticalStrut(8));
        }

        JLabel hint = new JLabel("<html>Wear the gear, click <b>Use worn gear</b>. "
                + "Click the hotkey button and press a key, Esc clears it.</html>");
        hint.setFont(FontManager.getRunescapeSmallFont());
        hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        content.add(left(hint));

        add(content, BorderLayout.NORTH);
        refresh();
    }

    /** Re-reads every set from the config. EDT only. */
    void refresh() {
        for (SetCard card : cards) card.refresh();
    }

    /** Toolbar icon: a small orange shield. */
    static BufferedImage icon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int[] xs = {2, 14, 14, 8, 2};
        int[] ys = {2, 2, 9, 15, 9};
        g.setColor(ColorScheme.BRAND_ORANGE);
        g.fillPolygon(xs, ys, xs.length);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(8, 4, 8, 12);
        g.drawLine(5, 7, 11, 7);
        g.dispose();
        return image;
    }

    private static Component left(JLabel label) {
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private class SetCard extends JPanel {
        private final int number;
        private final JButton hotkeyButton = new JButton();
        private final JComboBox<GearSwitcherConfig.OffensivePrayer> prayerBox =
                new JComboBox<>(GearSwitcherConfig.OffensivePrayer.values());
        private final JPanel itemList = new JPanel();
        private final JLabel status = new JLabel();
        private boolean listening;
        /** Set while refresh() fills the combo box, so that doesn't write back to the config. */
        private boolean updating;

        SetCard(int number) {
            this.number = number;
            setLayout(new BorderLayout(0, 6));
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            // Header: name and hotkey
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel name = new JLabel("Set " + number);
            name.setFont(FontManager.getRunescapeBoldFont());
            name.setForeground(Color.WHITE);
            header.add(name, BorderLayout.WEST);
            hotkeyButton.setFocusable(true);
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

            // Middle: prayer and items
            JPanel middle = new JPanel();
            middle.setOpaque(false);
            middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
            prayerBox.setToolTipText("Offensive prayer turned on with this set");
            prayerBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, prayerBox.getPreferredSize().height));
            prayerBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            prayerBox.addActionListener(e -> {
                if (!updating) {
                    configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Prayer",
                            prayerBox.getSelectedItem());
                }
            });
            middle.add(prayerBox);
            middle.add(Box.createVerticalStrut(6));
            itemList.setOpaque(false);
            itemList.setLayout(new BoxLayout(itemList, BoxLayout.Y_AXIS));
            itemList.setAlignmentX(Component.LEFT_ALIGNMENT);
            middle.add(itemList);
            add(middle, BorderLayout.CENTER);

            // Footer: buttons and a status line
            JPanel footer = new JPanel(new BorderLayout(0, 4));
            footer.setOpaque(false);
            JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
            buttons.setOpaque(false);
            JButton useWorn = new JButton("Use worn gear");
            useWorn.setToolTipText("Store everything you are wearing right now as this set");
            useWorn.addActionListener(e -> useWornGear());
            JButton clear = new JButton("Clear");
            clear.addActionListener(e -> {
                configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Items", "");
                status.setText(" ");
            });
            buttons.add(useWorn);
            buttons.add(clear);
            footer.add(buttons, BorderLayout.NORTH);
            status.setFont(FontManager.getRunescapeSmallFont());
            status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            status.setText(" ");
            footer.add(status, BorderLayout.SOUTH);
            add(footer, BorderLayout.SOUTH);
        }

        void refresh() {
            updating = true;
            prayerBox.setSelectedItem(prayer());
            updating = false;

            if (!listening) {
                Keybind key = hotkey();
                hotkeyButton.setText(key == null || Keybind.NOT_SET.equals(key) ? "Set hotkey" : key.toString());
            }

            itemList.removeAll();
            List<String> items = Arrays.stream(items().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            if (items.isEmpty()) {
                JLabel empty = new JLabel("No items yet");
                empty.setFont(FontManager.getRunescapeSmallFont());
                empty.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                itemList.add(empty);
            }
            for (String item : items) {
                JLabel label = new JLabel("• " + item);
                label.setFont(FontManager.getRunescapeSmallFont());
                label.setForeground(ColorScheme.TEXT_COLOR);
                itemList.add(label);
            }
            itemList.revalidate();
            itemList.repaint();
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
            String names = worn.stream().map(Rs2ItemModel::getName).distinct().collect(Collectors.joining(", "));
            configManager.setConfiguration(GearSwitcherPlugin.CONFIG_GROUP, "set" + number + "Items", names);
            status.setText(worn.size() + " items saved");
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
    }
}
