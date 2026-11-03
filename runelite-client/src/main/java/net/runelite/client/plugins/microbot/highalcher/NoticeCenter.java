package net.runelite.client.plugins.microbot.highalcher;

import javax.inject.Singleton;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * All High Alcher messages in one place instead of the game chat: the notice overlay above the chatbox shows
 * the newest ones for a few seconds, the Meldungen tab in the side panel keeps the history of this session.
 */
@Singleton
public class NoticeCenter {

    private static final int MAX_KEPT = 100;

    public static final Color INFO = new Color(236, 236, 242);
    public static final Color GOOD = new Color(110, 230, 140);
    public static final Color WARN = new Color(255, 170, 60);

    /** One message. Immutable, safe to hand to the overlay and the panel. */
    public static final class Notice {
        public final long at;
        public final String text;
        public final Color color;

        Notice(long at, String text, Color color) {
            this.at = at;
            this.text = text;
            this.color = color;
        }
    }

    private final Deque<Notice> notices = new ArrayDeque<>();

    public synchronized void add(String text, Color color) {
        notices.addFirst(new Notice(System.currentTimeMillis(), text, color));
        while (notices.size() > MAX_KEPT) notices.removeLast();
    }

    /** Newest first. */
    public synchronized List<Notice> all() {
        return new ArrayList<>(notices);
    }

    /** The newest few that are younger than {@code maxAgeMs}, newest first. */
    public synchronized List<Notice> recent(int max, long maxAgeMs) {
        long now = System.currentTimeMillis();
        List<Notice> out = new ArrayList<>();
        for (Notice n : notices) {
            if (out.size() >= max || now - n.at > maxAgeMs) break;
            out.add(n);
        }
        return out;
    }

    public synchronized void clear() {
        notices.clear();
    }
}
