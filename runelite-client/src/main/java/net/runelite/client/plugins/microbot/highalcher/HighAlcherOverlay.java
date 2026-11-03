package net.runelite.client.plugins.microbot.highalcher;

import net.runelite.api.Experience;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * A hand-drawn stats card in the Enchanter style, in fire colours: item icon with embers rising around it,
 * casts, XP and profit with hourly rates, and a progress bar to the next Magic level.
 * The border flashes gold every time an alch goes through.
 */
public class HighAlcherOverlay extends Overlay {

    private static final int WIDTH = 236;
    private static final int PAD = 12;
    private static final Color BG_TOP = new Color(70, 24, 16, 230);
    private static final Color BG_BOTTOM = new Color(24, 14, 32, 230);
    private static final Color ACCENT = new Color(255, 140, 60);
    private static final Color GOLD = new Color(255, 205, 90);
    private static final Color MUTED = new Color(215, 190, 175);
    private static final Color GOOD = new Color(110, 230, 140);
    private static final Color BAD = new Color(255, 110, 110);

    private final HighAlcherScript script;
    private final ItemManager itemManager;

    private final HighAlcherConfig config;

    @Inject
    HighAlcherOverlay(HighAlcherPlugin plugin, HighAlcherScript script, ItemManager itemManager, HighAlcherConfig config) {
        super(plugin);
        this.script = script;
        this.itemManager = itemManager;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D g) {
        // Only while the bot runs; the plugin itself can be on just for the side panel
        if (script.getStartedAt() == 0 || !script.isRunning()) return null;
        HighAlcherConfig.OverlayStyle style = config.overlayStyle();
        if (style == HighAlcherConfig.OverlayStyle.HIDDEN) return null;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        long now = System.currentTimeMillis();
        long elapsed = script.activeMs(); // paused time does not count
        int done = script.getAlched();
        long perHour = rate(done, elapsed);
        int xpGained = script.getStartXp() < 0 ? 0 : script.getXp() - script.getStartXp();
        long xpPerHour = rate(xpGained, elapsed);
        long profit = script.getProfit();
        long profitPerHour = rate(profit, elapsed);

        if (style == HighAlcherConfig.OverlayStyle.COMPACT) {
            return renderCompact(g, now, done, xpPerHour, profit);
        }

        int height = 250;
        // Background card with a soft drop shadow
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(3, 4, WIDTH, height, 18, 18);
        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, height, BG_BOTTOM));
        g.fillRoundRect(0, 0, WIDTH, height, 18, 18);

        // Border: bright flash right after an alch, otherwise a slow breathing glow
        boolean flash = now - script.getLastAlchAt() < 450;
        float glow = flash ? 1f : 0.45f + 0.2f * (float) Math.sin(now / 450.0);
        g.setStroke(new BasicStroke(flash ? 3f : 2f));
        g.setColor(withAlpha(flash ? GOLD : ACCENT, glow));
        g.drawRoundRect(0, 0, WIDTH - 1, height - 1, 18, 18);

        // Icon with embers rising around it
        int iconX = PAD, iconY = PAD, iconCenterX = iconX + 18, iconCenterY = iconY + 16;
        if (script.getCurrentItemId() > 0) {
            BufferedImage icon = itemManager.getImage(script.getCurrentItemId());
            if (icon != null) g.drawImage(icon, iconX, iconY, null);
        }
        for (int i = 0; i < 6; i++) {
            double phase = ((now / 1400.0) + i / 6.0) % 1.0;
            int ex = iconCenterX - 16 + (i * 7) % 32 + (int) (Math.sin(now / 300.0 + i) * 2);
            int ey = iconCenterY + 18 - (int) (phase * 40);
            float alpha = (float) (1 - phase);
            float size = 2.5f + (i % 3);
            g.setColor(withAlpha(i % 2 == 0 ? GOLD : ACCENT, alpha * 0.9f));
            g.fill(new Ellipse2D.Float(ex, ey, size, size));
        }

        // Title
        g.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        g.setColor(GOLD);
        g.drawString("High Alcher", 56, 26);
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(MUTED);
        String sub = script.getCurrentItemName().isEmpty() ? "Waiting for the first cast"
                : script.getCurrentItemName() + "  ·  " + signedGp(script.getCurrentProfitEach()) + " each";
        g.drawString(sub, 56, 40);

        // Divider
        g.setPaint(new GradientPaint(PAD, 0, withAlpha(ACCENT, 0f), WIDTH / 2f, 0, withAlpha(ACCENT, 0.8f), true));
        g.fillRect(PAD, 52, WIDTH - 2 * PAD, 1);

        // Stats
        int y = 72;
        y = row(g, "Alched", QuantityFormatter.formatNumber(done) + "  (" + QuantityFormatter.formatNumber(perHour) + "/h)", Color.WHITE, y);
        y = row(g, "XP gained", QuantityFormatter.quantityToStackSize(xpGained) + "  (" + QuantityFormatter.quantityToStackSize(xpPerHour) + "/h)", Color.WHITE, y);
        y = row(g, "Profit", signedGp(profit), profit >= 0 ? GOOD : BAD, y);
        y = row(g, "Profit / hour", signedGp(profitPerHour), profitPerHour >= 0 ? GOLD : BAD, y);
        y = row(g, "Items left", QuantityFormatter.formatNumber(script.getItemsLeft()), Color.WHITE, y);
        y = row(g, "Natures left", QuantityFormatter.formatNumber(script.getNaturesLeft()),
                script.getNaturesLeft() < 50 ? BAD : Color.WHITE, y);
        long max = script.getMaxRuntimeMs();
        y = row(g, script.isPaused() ? "Runtime (paused)" : "Runtime",
                max > 0 ? time(elapsed) + " / " + time(max) : time(elapsed), script.isPaused() ? ACCENT : Color.WHITE, y);

        // Level bar: progress to the next Magic level
        y += 4;
        int xp = script.getXp();
        int level = Experience.getLevelForXp(xp);
        int barWidth = WIDTH - 2 * PAD;
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(PAD, y, barWidth, 10, 10, 10);
        String levelText;
        if (level < Experience.MAX_REAL_LEVEL) {
            int from = Experience.getXpForLevel(level);
            int to = Experience.getXpForLevel(level + 1);
            float part = Math.max(0f, Math.min(1f, (xp - from) / (float) (to - from)));
            g.setPaint(new GradientPaint(PAD, 0, ACCENT, PAD + barWidth, 0, GOLD));
            g.fillRoundRect(PAD, y, Math.max(10, (int) (barWidth * part)), 10, 10, 10);
            int left = to - xp;
            String eta = xpPerHour > 0 ? time(left * 3_600_000L / xpPerHour) : "--:--:--";
            levelText = "Magic " + level + " → " + (level + 1) + "   " + QuantityFormatter.quantityToStackSize(left)
                    + " xp   (" + eta + ")";
        } else {
            g.setPaint(new GradientPaint(PAD, 0, ACCENT, PAD + barWidth, 0, GOLD));
            g.fillRoundRect(PAD, y, barWidth, 10, 10, 10);
            levelText = "Magic 99  ·  " + QuantityFormatter.quantityToStackSize(xp) + " xp";
        }
        g.setFont(FontManager.getRunescapeSmallFont());
        g.setColor(MUTED);
        g.drawString(levelText, PAD, y + 24);

        // Status
        g.setColor(withAlpha(Color.WHITE, 0.75f));
        g.drawString(Microbot.status == null ? "" : Microbot.status, PAD, y + 38);

        return new Dimension(WIDTH + 3, height + 4);
    }

    /**
     * One slim bar: item icon, "123 alchs · 78k xp/h · +12k gp" and a thin level bar along the bottom.
     * The border flashes gold on every alch like the full card, and turns orange while paused.
     */
    private Dimension renderCompact(Graphics2D g, long now, int done, long xpPerHour, long profit) {
        int width = 220;
        int height = 30;
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRoundRect(2, 3, width, height, 14, 14);
        g.setPaint(new GradientPaint(0, 0, BG_TOP, width, 0, BG_BOTTOM));
        g.fillRoundRect(0, 0, width, height, 14, 14);

        boolean flash = now - script.getLastAlchAt() < 450;
        g.setStroke(new BasicStroke(flash ? 2.5f : 1.5f));
        g.setColor(script.isPaused() ? ACCENT : withAlpha(flash ? GOLD : ACCENT, flash ? 1f : 0.55f));
        g.drawRoundRect(0, 0, width - 1, height - 1, 14, 14);

        if (script.getCurrentItemId() > 0) {
            BufferedImage icon = itemManager.getImage(script.getCurrentItemId());
            if (icon != null) g.drawImage(icon.getScaledInstance(24, 22, java.awt.Image.SCALE_SMOOTH), 5, 3, null);
        }

        g.setFont(FontManager.getRunescapeSmallFont());
        int x = 34;
        int y = 15;
        if (script.isPaused()) {
            g.setColor(ACCENT);
            g.drawString("PAUSED", x, y);
            x += g.getFontMetrics().stringWidth("PAUSED  ");
        }
        g.setColor(Color.WHITE);
        String left = QuantityFormatter.formatNumber(done) + " alchs  ·  "
                + QuantityFormatter.quantityToStackSize(xpPerHour) + " xp/h  ·  ";
        g.drawString(left, x, y);
        x += g.getFontMetrics().stringWidth(left);
        g.setColor(profit >= 0 ? GOOD : BAD);
        g.drawString(signedGp(profit), x, y);

        // Level progress as a thin line at the bottom
        int xp = script.getXp();
        int level = Experience.getLevelForXp(xp);
        float part = 1f;
        if (level < Experience.MAX_REAL_LEVEL) {
            int from = Experience.getXpForLevel(level);
            int to = Experience.getXpForLevel(level + 1);
            part = Math.max(0f, Math.min(1f, (xp - from) / (float) (to - from)));
        }
        int barX = 34;
        int barW = width - barX - 8;
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(barX, 21, barW, 4, 4, 4);
        g.setPaint(new GradientPaint(barX, 0, ACCENT, barX + barW, 0, GOLD));
        g.fillRoundRect(barX, 21, Math.max(4, (int) (barW * part)), 4, 4, 4);
        return new Dimension(width + 2, height + 3);
    }

    private static long rate(long amount, long elapsedMs) {
        return elapsedMs > 30_000 ? amount * 3_600_000L / elapsedMs : 0;
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

    private static String signedGp(long amount) {
        return (amount < 0 ? "-" : "+") + QuantityFormatter.quantityToStackSize(Math.abs(amount)) + " gp";
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static Color withAlpha(Color c, float alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, (int) (alpha * 255))));
    }
}
