package net.runelite.client.plugins.microbot.highalcher;

import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * High Alcher messages as small notices stacked above the chatbox, newest at the bottom. Each one fades out
 * after "Notice visible for" seconds, so the game chat itself stays free of them.
 */
public class NoticeOverlay extends Overlay {

    private static final int MAX_SHOWN = 4;
    private static final int HEIGHT = 20;
    private static final int GAP = 4;
    private static final int MAX_WIDTH = 340;
    private static final long FADE_MS = 1500;
    private static final Color BG = new Color(20, 20, 26);

    private final NoticeCenter notices;
    private final HighAlcherConfig config;

    @Inject
    NoticeOverlay(HighAlcherPlugin plugin, NoticeCenter notices, HighAlcherConfig config) {
        super(plugin);
        this.notices = notices;
        this.config = config;
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!config.showNotices()) return null;
        long visibleMs = config.noticeSeconds() * 1000L;
        List<NoticeCenter.Notice> shown = notices.recent(MAX_SHOWN, visibleMs);
        if (shown.isEmpty()) return null;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();

        int width = 0;
        for (NoticeCenter.Notice n : shown) {
            width = Math.max(width, Math.min(MAX_WIDTH, fm.stringWidth(n.text) + 26));
        }
        long now = System.currentTimeMillis();
        int total = shown.size() * HEIGHT + (shown.size() - 1) * GAP;

        // Oldest on top, newest right above the chatbox
        int y = 0;
        for (int i = shown.size() - 1; i >= 0; i--) {
            NoticeCenter.Notice n = shown.get(i);
            long left = visibleMs - (now - n.at);
            float alpha = left < FADE_MS ? Math.max(0f, left / (float) FADE_MS) : 1f;

            g.setColor(withAlpha(BG, 0.85f * alpha));
            g.fillRoundRect(0, y, width, HEIGHT, 10, 10);
            g.setColor(withAlpha(n.color, alpha));
            g.fillRoundRect(0, y, 4, HEIGHT, 4, 4);
            g.fillOval(10, y + HEIGHT / 2 - 3, 6, 6);
            g.setColor(withAlpha(Color.WHITE, alpha));
            g.drawString(clip(n.text, fm, width - 26), 22, y + HEIGHT / 2 + fm.getAscent() / 2 - 1);
            y += HEIGHT + GAP;
        }
        return new Dimension(width, total);
    }

    private static String clip(String text, FontMetrics fm, int max) {
        if (fm.stringWidth(text) <= max) return text;
        String s = text;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static Color withAlpha(Color c, float alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, (int) (alpha * 255))));
    }
}
