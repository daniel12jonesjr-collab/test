package net.runelite.client.plugins.microbot.highalcher.market.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.highalcher.NoticeCenter;
import net.runelite.client.plugins.microbot.highalcher.market.Analyzer;
import net.runelite.client.plugins.microbot.highalcher.market.BuyTracker;
import net.runelite.client.plugins.microbot.highalcher.market.Format;
import net.runelite.client.plugins.microbot.highalcher.market.model.Opportunity;
import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Dashboard:
 *   Kopf     Titel, Live-Punkt + "vor 12 s", Nature-Rune-Preis, Aktualisieren
 *   Tabs     Alch (n) | Flip (n) | Merkliste (n)
 *   Filter   Suche + Sortierung
 *   Liste    Karten (max. N, damit es fluessig bleibt)
 *
 * Alle Methoden laufen auf dem Swing-Thread. Daten kommen als fertige, unveraenderliche Listen.
 */
public class MarketPanel extends PluginPanel
{
	private enum Tab { ALCH, WATCH, BUYS, NOTICES }

	private enum Sort
	{
		SCORE("Empfohlen", Comparator.comparingDouble((Opportunity o) -> o.score).reversed()),
		PROFIT("Gewinn/Stk", Comparator.comparingInt((Opportunity o) -> o.profit).reversed()),
		PER_LIMIT("Gewinn/Limit", Comparator.comparingLong((Opportunity o) -> o.profitPerLimit).reversed()),
		ROI("ROI", Comparator.comparingDouble((Opportunity o) -> o.roi).reversed()),
		VOLUME("Volumen", Comparator.comparingInt((Opportunity o) -> o.volume).reversed());

		final String label;
		final Comparator<Opportunity> cmp;

		Sort(String label, Comparator<Opportunity> cmp)
		{
			this.label = label;
			this.cmp = cmp;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final ItemManager itemManager;
	private final Consumer<Integer> toggleWatch;
	private final Runnable refresh;
	private final Supplier<List<BuyTracker.Entry>> buys;
	private final IntUnaryOperator limitOf;
	private final Consumer<Integer> removeBuy;

	private final StatusDot dot = new StatusDot();
	private final JLabel status = new JLabel("Lade Preise...");
	private final JLabel nature = new JLabel();
	private final TabButton alchTab = new TabButton();
	private final TabButton watchTab = new TabButton();
	private final TabButton buysTab = new TabButton();
	private final TabButton noticesTab = new TabButton();
	private final JTextField search = new PlaceholderField("Item suchen...");
	private final JComboBox<Sort> sort = new JComboBox<>(Sort.values());
	private final JPanel list = new JPanel();

	private Tab tab = Tab.ALCH;
	private List<Opportunity> alchs = Collections.emptyList();
	private List<Opportunity> watched = Collections.emptyList();
	private Set<String> fresh = Collections.emptySet();
	private Set<Integer> watchIds = Collections.emptySet();
	private long lastUpdate;
	private String error;
	private int maxRows = 40;
	// The Käufe tab is redrawn every second as well, so its countdowns keep running
	private final Timer ticker = new Timer(1000, e -> onTick());
	private int lastNoticeCount;
	private final BotControls bot;
	private final JButton botButton = new JButton();
	private final JButton pauseButton = new JButton();
	private final JLabel botState = new JLabel();

	public MarketPanel(ItemManager itemManager, Consumer<Integer> toggleWatch, Runnable refresh,
		Supplier<List<BuyTracker.Entry>> buys, IntUnaryOperator limitOf, Consumer<Integer> removeBuy,
		BotControls bot)
	{
		super(false);
		this.itemManager = itemManager;
		this.toggleWatch = toggleWatch;
		this.refresh = refresh;
		this.buys = buys;
		this.limitOf = limitOf;
		this.removeBuy = removeBuy;
		this.bot = bot;

		// Start / stop and pause / resume for the alching bot, right under the title
		for (JButton b : new JButton[]{botButton, pauseButton})
		{
			b.setFocusPainted(false);
			b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		}
		botButton.addActionListener(e -> {
			bot.toggleRunning();
			updateBotButton();
		});
		pauseButton.addActionListener(e -> {
			bot.togglePause();
			updateBotButton();
		});
		botState.setFont(Style.small());
		updateBotButton();

		setLayout(new BorderLayout());
		setBackground(Style.BG);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.add(header());
		top.add(Box.createVerticalStrut(8));
		top.add(controls());
		top.add(Box.createVerticalStrut(10));
		top.add(tabs());
		top.add(Box.createVerticalStrut(8));
		top.add(filters());
		top.add(Box.createVerticalStrut(8));
		add(top, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		// Scrollable, das die Breite des Viewports uebernimmt -> Karten werden nie breiter als das Panel
		WidthTrackingPanel wrap = new WidthTrackingPanel();
		wrap.add(list, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(wrap);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(18);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scroll, BorderLayout.CENTER);

		// Nur das Status-Label tickt jede Sekunde - die Liste wird nur bei neuen Daten neu gebaut
		ticker.start();
		render();
	}

	public void dispose()
	{
		ticker.stop();
	}

	// ---------------------------------------------------------------- Daten von aussen

	public void update(Analyzer.Result result, List<Opportunity> watchedNow, Set<String> freshKeys,
		Set<Integer> watchIdsNow, long lastUpdateMillis, String lastError, int maxRowsNow)
	{
		if (result != null)
		{
			alchs = result.alchs;
			nature.setText("Nature Rune: " + Format.gp(result.natureRunePrice) + " gp");
		}
		watched = watchedNow;
		fresh = freshKeys;
		watchIds = watchIdsNow;
		lastUpdate = lastUpdateMillis;
		error = lastError;
		maxRows = maxRowsNow;
		updateStatus();
		render();
	}

	// ---------------------------------------------------------------- Aufbau

	private JPanel header()
	{
		JPanel h = new JPanel(new BorderLayout());
		h.setOpaque(false);
		h.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);
		JLabel title = new JLabel("High Alcher");
		title.setFont(Style.bold(20f));
		title.setForeground(Style.TEXT);
		left.add(title);

		JPanel statusRow = new JPanel(new BorderLayout(5, 0));
		statusRow.setOpaque(false);
		statusRow.add(dot, BorderLayout.WEST);
		status.setFont(Style.small());
		status.setForeground(Style.MUTED);
		statusRow.add(status, BorderLayout.CENTER);
		statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		left.add(statusRow);

		nature.setFont(Style.small());
		nature.setForeground(Style.MUTED);
		left.add(nature);
		h.add(left, BorderLayout.CENTER);

		JButton reload = new JButton("↻");
		Style.button(reload, Style.SURFACE, Style.TEXT);
		reload.setFont(Style.regular().deriveFont(16f));
		reload.setToolTipText("Jetzt aktualisieren");
		reload.addActionListener(e -> {
			status.setText("Aktualisiere...");
			refresh.run();
		});
		JPanel reloadWrap = new JPanel(new BorderLayout());
		reloadWrap.setOpaque(false);
		reloadWrap.add(reload, BorderLayout.NORTH);
		h.add(reloadWrap, BorderLayout.EAST);
		return h;
	}

	private JPanel tabs()
	{
		JPanel row = new JPanel(new GridLayout(1, 4, 4, 0));
		row.setOpaque(false);
		ButtonGroup group = new ButtonGroup();
		for (JToggleButton b : new JToggleButton[]{alchTab, watchTab, buysTab, noticesTab})
		{
			group.add(b);
			row.add(b);
		}
		alchTab.setSelected(true);
		alchTab.addActionListener(e -> { tab = Tab.ALCH; render(); });
		watchTab.addActionListener(e -> { tab = Tab.WATCH; render(); });
		buysTab.addActionListener(e -> { tab = Tab.BUYS; render(); });
		buysTab.setToolTipText("Gekaufte Items mit 4-Stunden-Kauflimit");
		noticesTab.addActionListener(e -> { tab = Tab.NOTICES; render(); });
		noticesTab.setToolTipText("Alle Meldungen des High Alcher");
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		row.setPreferredSize(new Dimension(0, 28));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private JPanel filters()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		search.setFont(Style.small());
		search.setBackground(Style.SURFACE);
		search.setForeground(Style.TEXT);
		search.setCaretColor(Style.TEXT);
		search.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Style.BORDER, 1, true), new EmptyBorder(4, 6, 4, 6)));
		search.setToolTipText("Item suchen");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { render(); }
			public void removeUpdate(DocumentEvent e) { render(); }
			public void changedUpdate(DocumentEvent e) { render(); }
		});
		row.add(search, BorderLayout.CENTER);
		sort.setFont(Style.small());
		sort.addActionListener(e -> render());
		row.add(sort, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	// ---------------------------------------------------------------- Rendern

	private void render()
	{
		alchTab.setText("Alch (" + alchs.size() + ")");
		watchTab.setText("★ (" + watched.size() + ")");
		List<BuyTracker.Entry> bought = buys.get();
		buysTab.setText("Käufe (" + bought.size() + ")");
		List<NoticeCenter.Notice> notes = bot.notices();
		noticesTab.setText("🔔 (" + notes.size() + ")");
		if (tab == Tab.NOTICES)
		{
			renderNotices(notes);
			return;
		}
		if (tab == Tab.BUYS)
		{
			renderBuys(bought);
			return;
		}

		List<Opportunity> source = tab == Tab.ALCH ? alchs : watched;
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		List<Opportunity> view = new ArrayList<>(source.size());
		for (Opportunity o : source)
		{
			if (q.isEmpty() || o.item.name.toLowerCase(Locale.ROOT).contains(q)) view.add(o);
		}
		Sort s = (Sort) sort.getSelectedItem();
		if (s != null) view.sort(s.cmp);

		java.util.Map<Integer, BuyTracker.Entry> boughtById = new java.util.HashMap<>();
		for (BuyTracker.Entry e : bought) boughtById.put(e.itemId, e);

		list.removeAll();
		int shown = 0;
		for (Opportunity o : view)
		{
			if (shown++ >= maxRows) break;
			list.add(new OpportunityCard(o, itemManager, fresh.contains(o.key()), watchIds.contains(o.item.id), toggleWatch,
				boughtById.get(o.item.id), bot::typeIntoSearch));
			list.add(Box.createVerticalStrut(6));
		}
		if (view.isEmpty())
		{
			String msg = tab == Tab.WATCH ? "Noch nichts gemerkt.<br>Klick auf den Stern einer Karte."
				: lastUpdate == 0 ? "Lade Preise..." : "Gerade keine Gelegenheit ueber deinen Schwellen.";
			JLabel empty = new JLabel("<html><center>" + msg + "</center></html>");
			empty.setFont(Style.regular());
			empty.setForeground(Style.MUTED);
			empty.setBorder(new EmptyBorder(24, 8, 0, 8));
			list.add(empty);
		}
		else if (view.size() > maxRows)
		{
			JLabel more = new JLabel("+ " + (view.size() - maxRows) + " weitere (Suche nutzen)");
			more.setFont(Style.small());
			more.setForeground(Style.MUTED);
			list.add(more);
		}
		list.revalidate();
		list.repaint();
	}

	/** Meldungen tab: every High Alcher notice of this session, newest first, with a button to clear them. */
	private void renderNotices(List<NoticeCenter.Notice> notes)
	{
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		java.text.SimpleDateFormat clock = new java.text.SimpleDateFormat("HH:mm:ss");
		list.removeAll();
		if (!notes.isEmpty())
		{
			JButton clear = new JButton("Alle löschen");
			Style.button(clear, Style.SURFACE, Style.MUTED);
			clear.setAlignmentX(Component.LEFT_ALIGNMENT);
			clear.addActionListener(e -> {
				bot.clearNotices();
				render();
			});
			list.add(clear);
			list.add(Box.createVerticalStrut(6));
		}
		int shown = 0;
		for (NoticeCenter.Notice n : notes)
		{
			if (!q.isEmpty() && !n.text.toLowerCase(Locale.ROOT).contains(q)) continue;
			RoundPanel card = new RoundPanel(new BorderLayout(8, 0), Style.CARD);
			card.setAccent(n.color);
			card.setBorder(new EmptyBorder(6, 10, 6, 10));
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel time = new JLabel(clock.format(new java.util.Date(n.at)));
			time.setFont(Style.small());
			time.setForeground(Style.MUTED);
			JLabel text = new JLabel("<html>" + n.text.replace("<", "&lt;") + "</html>");
			text.setFont(Style.small());
			text.setForeground(Style.TEXT);
			card.add(time, BorderLayout.WEST);
			card.add(text, BorderLayout.CENTER);
			card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 4));
			list.add(card);
			list.add(Box.createVerticalStrut(4));
			shown++;
		}
		if (shown == 0)
		{
			JLabel empty = new JLabel("<html><center>Noch keine Meldungen.<br>Start, Stopp, Pause, neue Alch-Items"
				+ "<br>und freie Kauflimits landen hier.</center></html>");
			empty.setFont(Style.regular());
			empty.setForeground(Style.MUTED);
			empty.setBorder(new EmptyBorder(24, 8, 0, 8));
			list.add(empty);
		}
		list.revalidate();
		list.repaint();
	}

	/** Käufe-Tab: eine Karte pro gekauftem Item, das zuerst frei werdende oben. */
	private void renderBuys(List<BuyTracker.Entry> bought)
	{
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		list.removeAll();
		int shown = 0;
		for (BuyTracker.Entry e : bought)
		{
			if (!q.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(q)) continue;
			list.add(new BuyCard(e, limitOf.applyAsInt(e.itemId), itemManager, removeBuy));
			list.add(Box.createVerticalStrut(6));
			shown++;
		}
		if (shown == 0)
		{
			JLabel empty = new JLabel("<html><center>Noch nichts gekauft.<br>Käufe im GE erscheinen hier automatisch"
				+ "<br>mit Kauflimit und 4-Stunden-Countdown.</center></html>");
			empty.setFont(Style.regular());
			empty.setForeground(Style.MUTED);
			empty.setBorder(new EmptyBorder(24, 8, 0, 8));
			list.add(empty);
		}
		list.revalidate();
		list.repaint();
	}

	private void onTick()
	{
		updateStatus();
		updateBotButton();
		// Käufe: countdowns tick. Meldungen and the 🔔 count: only redrawn when a new notice came in
		int count = bot.notices().size();
		if (tab == Tab.BUYS || count != lastNoticeCount) render();
		lastNoticeCount = count;
	}

	/** Control card: state pill on top, then Start/Stop (wide) and Pause/Resume side by side. */
	private JPanel controls()
	{
		RoundPanel card = new RoundPanel(new BorderLayout(0, 6), Style.CARD);
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

		JPanel stateRow = new JPanel(new BorderLayout());
		stateRow.setOpaque(false);
		JLabel label = new JLabel("Bot");
		label.setFont(Style.small());
		label.setForeground(Style.MUTED);
		stateRow.add(label, BorderLayout.WEST);
		stateRow.add(botState, BorderLayout.EAST);
		card.add(stateRow, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new BorderLayout(6, 0));
		buttons.setOpaque(false);
		buttons.add(botButton, BorderLayout.CENTER);
		pauseButton.setPreferredSize(new Dimension(86, 30));
		buttons.add(pauseButton, BorderLayout.EAST);
		card.add(buttons, BorderLayout.CENTER);
		return card;
	}

	private void updateBotButton()
	{
		boolean on = bot.isRunning();
		boolean paused = bot.isPaused();
		botButton.setText(on ? "■  Stop" : "▶  Start alching");
		Style.button(botButton, on ? Style.ERROR : Style.PROFIT, Style.BG);
		botButton.setFont(Style.bold(14f));
		botButton.setToolTipText(on ? "Stops the High Alcher bot" : "Alchs your inventory with the High Alcher settings");

		pauseButton.setText(paused ? "▶  Resume" : "❚❚  Pause");
		Style.button(pauseButton, paused ? Style.ALCH : Style.SURFACE, paused ? Style.BG : Style.TEXT);
		pauseButton.setFont(Style.bold(13f));
		pauseButton.setEnabled(on);
		pauseButton.setToolTipText(on ? (paused ? "Carry on alching" : "Hold the bot, it keeps its stats and settings")
			: "Start the bot first");

		botState.setText(!on ? "● Stopped" : paused ? "● Paused" : "● Running");
		botState.setForeground(!on ? Style.MUTED : paused ? Style.ALCH : Style.PROFIT);
	}

	private void updateStatus()
	{
		if (lastUpdate == 0)
		{
			dot.setColor(error != null ? Style.ERROR : Style.STALE);
			status.setText(error != null ? "Fehler: " + error : "Lade Preise...");
			return;
		}
		long ago = (System.currentTimeMillis() - lastUpdate) / 1000;
		boolean stale = ago > 180;
		dot.setColor(error != null ? Style.ERROR : stale ? Style.STALE : Style.LIVE);
		status.setText((error != null ? "Fehler, zeige alte Daten  |  " : "Live  |  ") + "vor " + Format.age(ago));
	}

	/** Kleiner runder Status-Punkt. */
	private static final class StatusDot extends JPanel
	{
		private Color color = Style.STALE;

		StatusDot()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(10, 10));
		}

		void setColor(Color c)
		{
			color = c;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int y = (getHeight() - 8) / 2;
			g2.setColor(Style.alpha(color, 70));
			g2.fillOval(0, y - 1, 10, 10);
			g2.setColor(color);
			g2.fillOval(1, y, 8, 8);
			g2.dispose();
		}
	}

	/** Tab mit eigenem, dunklem Look (unabhaengig vom Look-and-Feel). */
	private static final class TabButton extends JToggleButton
	{
		TabButton()
		{
			setFont(Style.small());
			setFocusPainted(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			boolean on = isSelected();
			g2.setColor(on ? Style.CARD_HOVER : Style.SURFACE);
			g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
			if (on)
			{
				g2.setColor(Style.TEXT);
				g2.fillRoundRect(getWidth() / 2 - 10, getHeight() - 3, 20, 2, 2, 2);
			}
			g2.dispose();
			setForeground(on ? Style.TEXT : Style.MUTED);
			super.paintComponent(g);
		}
	}

	/** Textfeld mit grauem Platzhalter. */
	private static final class PlaceholderField extends JTextField
	{
		private final String hint;

		PlaceholderField(String hint)
		{
			this.hint = hint;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (getText().isEmpty() && !isFocusOwner())
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g2.setColor(Style.MUTED);
				g2.setFont(getFont());
				java.awt.Insets in = getInsets();
				g2.drawString(hint, in.left, in.top + g2.getFontMetrics().getAscent());
				g2.dispose();
			}
		}
	}

	/** Container im ScrollPane, der immer genau so breit ist wie der sichtbare Bereich. */
	private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable
	{
		WidthTrackingPanel()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 18; }
		public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height; }
		public boolean getScrollableTracksViewportWidth() { return true; }
		public boolean getScrollableTracksViewportHeight() { return false; }
	}
}
