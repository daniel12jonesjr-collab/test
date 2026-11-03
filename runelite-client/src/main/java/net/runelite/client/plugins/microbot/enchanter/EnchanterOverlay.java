package net.runelite.client.plugins.microbot.enchanter;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;

/**
 * A hand-drawn stats card: item icon with orbiting sparkles, enchanted count, rate, GE profit and a runtime bar.
 * The border flashes every time a piece gets enchanted. Prices come from the GE guide prices, after the 2% tax,
 * with the cost of the non-elemental runes taken off (elemental runes are assumed to come from a staff).
 */
public class EnchanterOverlay extends Overlay {

    private static final int WIDTH = 236;
    private static final int PAD = 12;
    private static final Color BG_TOP = new Color(44, 22, 78, 230);
    private static final Color BG_BOTTOM = new Color(14, 28, 58, 230);
    private static final Color ACCENT = new Color(120, 200, 255);
    private static final Color GOLD = new Color(255, 205, 90);
    private static final Color MUTED = new Color(185, 180, 210);
    private static final Color GOOD = new Color(110, 230, 140);
    private static final Color BAD = new Color(255, 110, 110);
    private static final Set<Runes> ELEMENTAL = Set.of(Runes.AIR, Runes.WATER, Runes.EARTH, Runes.FIRE);
    private static final double GE_TAX = 0.02;

    private final EnchanterScript script;
    private final EnchanterConfig config;
    private final ItemManager itemManager;

    @Inject
    EnchanterOverlay(EnchanterPlugin plugin, EnchanterScript script, EnchanterConfig config, ItemManager itemManager) {
        super(plugin);
        this.script = script;
        this.config = config;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (script.getStartedAt() == 0) return null;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        long now = System.currentTimeMillis();
        long elapsed = now - script.getStartedAt();
        long max = script.getMaxRuntimeMs();
        int done = script.getEnchanted();
        long perHour = elapsed > 30_000 ? done * 3_600_000L / elapsed : 0;
        long each = profitEach();
        long profit = each * done;
        long profitPerHour = each * perHour;

        int height = 196;
        // Background card with a soft drop shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(3, 4, WIDTH, height, 18, 18);
        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, height, BG_BOTTOM));
        g.fillRoundRect(0, 0, WIDTH, height, 18, 18);

        // Border: bright flash right after an enchant, otherwise a slow breathing glow
        boolean flash = now - script.getLastEnchantAt() < 450;
        float glow = flash ? 1f : 0.45f + 0.2f * (float) Math.sin(now / 450.0);
        g.setStroke(new BasicStroke(flash ? 3f : 2f));
        g.setColor(withAlpha(flash ? GOLD : ACCENT, glow));
        g.drawRoundRect(0, 0, WIDTH - 1, height - 1, 18, 18);

        // Icon with sparkles circling around it
        int iconX = PAD, iconY = PAD, iconCenterX = iconX + 18, iconCenterY = iconY + 16;
        int iconId = script.getProductItemId() > 0 ? script.getProductItemId() : script.getSourceItemId();
        if (iconId > 0) {
            BufferedImage icon = itemManager.getImage(iconId);
            if (icon != null) g.drawImage(icon, iconX, iconY, null);
        }
        for (int i = 0; i < 4; i++) {
            double angle = now / 700.0 + i * Math.PI / 2;
            int sx = iconCenterX + (int) (Math.cos(angle) * 22);
            int sy = iconCenterY + (int) (Math.sin(angle) * 22);
            float twinkle = 0.4f + 0.6f * (float) Math.abs(Math.sin(now / 300.0 + i));
            drawSparkle(g, sx, sy, 3 + (i % 2), withAlpha(i % 2 == 0 ? GOLD : ACCENT, twinkle));
        }

        // Title
        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        g.setColor(GOLD);
        g.drawString("Enchanter", 56, 26);
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(MUTED);
        g.drawString(config.itemName() + "  ·  " + prettyMode(), 56, 40);

        // Divider
        g.setPaint(new GradientPaint(PAD, 0, withAlpha(ACCENT, 0f), WIDTH / 2f, 0, withAlpha(ACCENT, 0.8f), true));
        g.fillRect(PAD, 52, WIDTH - 2 * PAD, 1);

        // Stats
        int y = 72;
        y = row(g, "Enchanted", QuantityFormatter.formatNumber(done), Color.WHITE, y);
        y = row(g, "Per hour", QuantityFormatter.formatNumber(perHour), Color.WHITE, y);
        y = row(g, "Profit each", gp(each), each >= 0 ? GOOD : BAD, y);
        y = row(g, "Profit", gp(profit), profit >= 0 ? GOOD : BAD, y);
        y = row(g, "Profit / hour", gp(profitPerHour), profitPerHour >= 0 ? GOLD : BAD, y);

        // Runtime bar
        y += 4;
        int barWidth = WIDTH - 2 * PAD;
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(PAD, y, barWidth, 10, 10, 10);
        String timeText;
        if (max > 0) {
            float part = Math.min(1f, elapsed / (float) max);
            g.setPaint(new GradientPaint(PAD, 0, ACCENT, PAD + barWidth, 0, GOLD));
            g.fillRoundRect(PAD, y, Math.max(10, (int) (barWidth * part)), 10, 10, 10);
            timeText = time(elapsed) + " / " + time(max) + "   (" + time(Math.max(0, max - elapsed)) + " left)";
        } else {
            // No limit: a light that keeps running along the bar
            int pos = (int) ((now / 8) % barWidth);
            g.setPaint(new GradientPaint(PAD + pos - 30, 0, withAlpha(ACCENT, 0f), PAD + pos, 0, ACCENT));
            g.fillRoundRect(PAD + Math.max(0, pos - 30), y, Math.min(30, pos), 10, 10, 10);
            timeText = time(elapsed) + "   (no limit)";
        }
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(MUTED);
        g.drawString(timeText, PAD, y + 24);

        // Status
        g.setColor(withAlpha(Color.WHITE, 0.75f));
        g.drawString(Microbot.status == null ? "" : Microbot.status, PAD, y + 38);

        return new Dimension(WIDTH + 3, height + 4);
    }

    /** Sell price of one product after tax, minus one piece of jewellery and the non-elemental runes of one cast. */
    private long profitEach() {
        int product = script.getProductItemId();
        int source = script.getSourceItemId();
        if (product <= 0 || source <= 0) return 0;
        long sell = Math.round(itemManager.getItemPrice(product) * (1 - GE_TAX));
        return sell - itemManager.getItemPrice(source) - runeCost();
    }

    private long runeCost() {
        Rs2Spells spell;
        try {
            spell = Rs2Spells.valueOf(config.spell().getAction().name());
        } catch (IllegalArgumentException ex) {
            return 0;
        }
        long cost = 0;
        for (Map.Entry<Runes, Integer> rune : spell.getRequiredRunes().entrySet()) {
            if (ELEMENTAL.contains(rune.getKey())) continue;
            cost += (long) itemManager.getItemPrice(rune.getKey().getItemId()) * rune.getValue();
        }
        return cost;
    }

    private static int row(Graphics2D g, String label, String value, Color valueColor, int y) {
        g.setFont(FontManager.getRunescapeFont());
        g.setColor(MUTED);
        g.drawString(label, PAD, y);
        g.setFont(FontManager.getRunescapeBoldFont());
        g.setColor(valueColor);
        int width = g.getFontMetrics().stringWidth(value);
        g.drawString(value, WIDTH - PAD - width, y);
        return y + 17;
    }

    /** Four-pointed star. */
    private static void drawSparkle(Graphics2D g, int x, int y, int size, Color color) {
        Path2D star = new Path2D.Double();
        star.moveTo(x, y - size * 2);
        star.lineTo(x + size / 2.0, y - size / 2.0);
        star.lineTo(x + size * 2, y);
        star.lineTo(x + size / 2.0, y + size / 2.0);
        star.lineTo(x, y + size * 2);
        star.lineTo(x - size / 2.0, y + size / 2.0);
        star.lineTo(x - size * 2, y);
        star.lineTo(x - size / 2.0, y - size / 2.0);
        star.closePath();
        g.setColor(color);
        g.fill(star);
    }

    private String prettyMode() {
        return config.mode() == EnchanterConfig.Mode.CLICK_EACH ? "click each" : "once per inventory";
    }

    private static String gp(long amount) {
        return (amount < 0 ? "-" : "") + QuantityFormatter.quantityToStackSize(Math.abs(amount)) + " gp";
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static Color withAlpha(Color c, float alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, (int) (alpha * 255))));
    }
}
