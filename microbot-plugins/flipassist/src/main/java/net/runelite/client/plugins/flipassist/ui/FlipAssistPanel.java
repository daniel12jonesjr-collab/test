package net.runelite.client.plugins.flipassist.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.flipassist.Analyzer;
import net.runelite.client.plugins.flipassist.Format;
import net.runelite.client.plugins.flipassist.model.Opportunity;
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

/**
 * Dashboard:
 *   Kopf     Titel, Live-Punkt + "vor 12 s", Nature-Rune-Preis, Aktualisieren
 *   Tabs     Alch (n) | Flip (n) | Merkliste (n)
 *   Filter   Suche + Sortierung
 *   Liste    Karten (max. N, damit es fluessig bleibt)
 *
 * Alle Methoden laufen auf dem Swing-Thread. Daten kommen als fertige, unveraenderliche Listen.
 */
public class FlipAssistPanel extends PluginPanel
{
	private enum Tab { ALCH, FLIP, WATCH }

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

	private final StatusDot dot = new StatusDot();
	private final JLabel status = new JLabel("Lade Preise...");
	private final JLabel nature = new JLabel();
	private final TabButton alchTab = new TabButton();
	private final TabButton flipTab = new TabButton();
	private final TabButton watchTab = new TabButton();
	private final JTextField search = new PlaceholderField("Item suchen...");
	private final JComboBox<Sort> sort = new JComboBox<>(Sort.values());
	private final JPanel list = new JPanel();

	private Tab tab = Tab.ALCH;
	private List<Opportunity> alchs = Collections.emptyList();
	private List<Opportunity> flips = Collections.emptyList();
	private List<Opportunity> watched = Collections.emptyList();
	private Set<String> fresh = Collections.emptySet();
	private Set<Integer> watchIds = Collections.emptySet();
	private long lastUpdate;
	private String error;
	private int maxRows = 40;
	private final Timer ticker = new Timer(1000, e -> updateStatus());

	public FlipAssistPanel(ItemManager itemManager, Consumer<Integer> toggleWatch, Runnable refresh)
	{
		super(false);
		this.itemManager = itemManager;
		this.toggleWatch = toggleWatch;
		this.refresh = refresh;

		setLayout(new BorderLayout());
		setBackground(Style.BG);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.add(header());
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
			flips = result.flips;
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
		JLabel title = new JLabel("Flip & Alch");
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
		JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
		row.setOpaque(false);
		ButtonGroup group = new ButtonGroup();
		for (JToggleButton b : new JToggleButton[]{alchTab, flipTab, watchTab})
		{
			group.add(b);
			row.add(b);
		}
		alchTab.setSelected(true);
		alchTab.addActionListener(e -> { tab = Tab.ALCH; render(); });
		flipTab.addActionListener(e -> { tab = Tab.FLIP; render(); });
		watchTab.addActionListener(e -> { tab = Tab.WATCH; render(); });
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
		flipTab.setText("Flip (" + flips.size() + ")");
		watchTab.setText("★ (" + watched.size() + ")");

		List<Opportunity> source = tab == Tab.ALCH ? alchs : tab == Tab.FLIP ? flips : watched;
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		List<Opportunity> view = new ArrayList<>(source.size());
		for (Opportunity o : source)
		{
			if (q.isEmpty() || o.item.name.toLowerCase(Locale.ROOT).contains(q)) view.add(o);
		}
		Sort s = (Sort) sort.getSelectedItem();
		if (s != null) view.sort(s.cmp);

		list.removeAll();
		int shown = 0;
		for (Opportunity o : view)
		{
			if (shown++ >= maxRows) break;
			list.add(new OpportunityCard(o, itemManager, fresh.contains(o.key()), watchIds.contains(o.item.id), toggleWatch));
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
