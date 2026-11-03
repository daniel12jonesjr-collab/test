package net.runelite.client.plugins.microbot.questhelper.prep;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Abfrage vor dem Einkauf:
 *   - Teleport-Vorschlaege (an/aus) mit abgedeckten Quest-Orten und Kosten
 *   - Einkaufsliste (an/aus) mit Stueck- und Gesamtpreis
 * Die Summe unten rechnet live mit. Nichts wird ohne "Kaufen & Starten" gekauft.
 */
final class PrepDialog
{
	private static final Color BG = new Color(28, 28, 32);
	private static final Color CARD = new Color(40, 40, 46);
	private static final Color TEXT = new Color(235, 235, 240);
	private static final Color MUTED = new Color(150, 150, 160);
	private static final Color ACCENT = new Color(120, 100, 255);
	private static final Color OK = new Color(80, 200, 120);
	private static final NumberFormat GP = NumberFormat.getIntegerInstance(Locale.GERMANY);

	/** Ergebnis der Abfrage. */
	static final class Decision
	{
		boolean cancelled;
		boolean buy;
		final List<TeleportSuggestion> teleports = new ArrayList<>();
		final Set<Integer> approvedBuyIds = new HashSet<>();
	}

	private PrepDialog() {}

	static CompletableFuture<Decision> ask(String quest, List<TeleportSuggestion> teleports, List<PrepItem> shopping)
	{
		CompletableFuture<Decision> result = new CompletableFuture<>();
		SwingUtilities.invokeLater(() -> {
			try
			{
				show(quest, teleports, shopping, result);
			}
			catch (Exception e)
			{
				result.completeExceptionally(e);
			}
		});
		return result;
	}

	private static void show(String quest, List<TeleportSuggestion> teleports, List<PrepItem> shopping,
		CompletableFuture<Decision> result)
	{
		JDialog dialog = new JDialog((java.awt.Frame) null, "Quest-Vorbereitung: " + quest, true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel root = new JPanel(new BorderLayout(0, 10));
		root.setBackground(BG);
		root.setBorder(new EmptyBorder(14, 14, 14, 14));

		JLabel title = label(quest, Font.BOLD, 16f, TEXT);
		root.add(title, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(BG);

		JLabel total = label("", Font.BOLD, 13f, TEXT);
		List<JCheckBox> tpBoxes = new ArrayList<>();
		List<JCheckBox> buyBoxes = new ArrayList<>();
		Runnable recalc = () -> total.setText("Gesamt: " + GP.format(sum(teleports, tpBoxes, shopping, buyBoxes)) + " gp");

		// --- Teleports
		if (!teleports.isEmpty())
		{
			body.add(section("Teleport-Vorschlaege"));
			for (TeleportSuggestion s : teleports)
			{
				StringBuilder need = new StringBuilder();
				for (PrepItem i : s.getItems())
				{
					if (need.length() > 0) need.append(", ");
					need.append(i.getNeeded()).append("x ").append(i.getName());
				}
				String status = s.fullyOwned() ? "alles vorhanden" : "kaufen ~" + GP.format(s.buyCost()) + " gp";
				JCheckBox box = row(s.getName() + "  -  " + s.getCoveredSteps() + " Quest-Orte",
					need + "  |  " + status, s.fullyOwned() ? OK : ACCENT, s.isSelected());
				box.addActionListener(e -> recalc.run());
				tpBoxes.add(box);
				body.add(wrap(box));
			}
		}

		// --- Einkauf
		if (!shopping.isEmpty())
		{
			body.add(section("Einkaufsliste"));
			for (PrepItem i : shopping)
			{
				int n = i.missingAfterBank();
				String detail = n + " x " + GP.format(i.getUnitPrice()) + " gp = " + GP.format((long) n * i.getUnitPrice()) + " gp"
					+ "  |  " + sourceLabel(i.getSource());
				JCheckBox box = row(i.getName(), detail, ACCENT, true);
				box.addActionListener(e -> recalc.run());
				buyBoxes.add(box);
				body.add(wrap(box));
			}
		}

		JScrollPane scroll = new JScrollPane(body);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(BG);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setPreferredSize(new Dimension(520, Math.min(460, 90 + 46 * (teleports.size() + shopping.size()))));
		root.add(scroll, BorderLayout.CENTER);

		// --- Fuss: Summe + Buttons
		JPanel foot = new JPanel(new BorderLayout());
		foot.setBackground(BG);
		foot.add(total, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setBackground(BG);
		JButton cancel = button("Abbrechen", CARD);
		JButton noBuy = button("Ohne Kauf starten", CARD);
		JButton go = button("Kaufen & Starten", ACCENT);
		buttons.add(cancel);
		buttons.add(noBuy);
		buttons.add(go);
		foot.add(buttons, BorderLayout.EAST);
		root.add(foot, BorderLayout.SOUTH);

		Decision d = new Decision();
		d.cancelled = true; // Fenster schliessen = abbrechen

		cancel.addActionListener(e -> dialog.dispose());
		noBuy.addActionListener(e -> {
			collect(d, teleports, tpBoxes, shopping, buyBoxes, false);
			dialog.dispose();
		});
		go.addActionListener(e -> {
			collect(d, teleports, tpBoxes, shopping, buyBoxes, true);
			dialog.dispose();
		});

		recalc.run();
		dialog.setContentPane(root);
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setAlwaysOnTop(true);
		dialog.setVisible(true); // modal: blockiert hier bis geschlossen (nur den EDT-Aufruf, nicht das Spiel)
		result.complete(d);
	}

	private static void collect(Decision d, List<TeleportSuggestion> tps, List<JCheckBox> tpBoxes,
		List<PrepItem> shop, List<JCheckBox> buyBoxes, boolean buy)
	{
		d.cancelled = false;
		d.buy = buy;
		for (int i = 0; i < tps.size(); i++)
		{
			TeleportSuggestion s = tps.get(i);
			s.setSelected(tpBoxes.get(i).isSelected());
			if (s.isSelected())
			{
				d.teleports.add(s);
				for (PrepItem item : s.getItems())
				{
					if (item.getBuyId() > 0) d.approvedBuyIds.add(item.getBuyId());
				}
			}
		}
		for (int i = 0; i < shop.size(); i++)
		{
			if (buyBoxes.get(i).isSelected() && shop.get(i).getBuyId() > 0) d.approvedBuyIds.add(shop.get(i).getBuyId());
		}
	}

	private static long sum(List<TeleportSuggestion> tps, List<JCheckBox> tpBoxes, List<PrepItem> shop, List<JCheckBox> buyBoxes)
	{
		long s = 0;
		for (int i = 0; i < tps.size() && i < tpBoxes.size(); i++)
		{
			if (tpBoxes.get(i).isSelected()) s += tps.get(i).buyCost();
		}
		for (int i = 0; i < shop.size() && i < buyBoxes.size(); i++)
		{
			if (buyBoxes.get(i).isSelected()) s += (long) shop.get(i).missingAfterBank() * Math.max(0, shop.get(i).getUnitPrice());
		}
		return s;
	}

	private static String sourceLabel(PrepItem.Source s)
	{
		switch (s)
		{
			case QUEST: return "Quest-Item";
			case RECOMMENDED: return "empfohlen";
			case GEAR: return "Quest-Gear";
			case TELEPORT: return "Teleport";
			default: return "Loadout";
		}
	}

	// ------------------------------------------------------------------ Bausteine

	private static JCheckBox row(String title, String detail, Color accent, boolean selected)
	{
		JCheckBox box = new JCheckBox("<html><b>" + esc(title) + "</b><br><span style='color:#9a9aa5'>" + esc(detail) + "</span></html>", selected);
		box.setForeground(TEXT);
		box.setBackground(CARD);
		box.setFocusPainted(false);
		box.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			new EmptyBorder(6, 8, 6, 8)));
		box.setBorderPainted(true);
		return box;
	}

	private static Component wrap(JComponent c)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(BG);
		p.setBorder(new EmptyBorder(0, 0, 6, 0));
		p.add(c, BorderLayout.CENTER);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 6));
		return p;
	}

	private static Component section(String text)
	{
		JLabel l = label(text.toUpperCase(), Font.BOLD, 11f, MUTED);
		l.setBorder(new EmptyBorder(8, 2, 6, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel label(String text, int style, float size, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(style, size));
		l.setForeground(color);
		return l;
	}

	private static JButton button(String text, Color bg)
	{
		JButton b = new JButton(text);
		b.setBackground(bg);
		b.setForeground(TEXT);
		b.setFocusPainted(false);
		b.setBorder(new EmptyBorder(7, 12, 7, 12));
		return b;
	}

	private static String esc(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	@SuppressWarnings("unused")
	private static Component gap()
	{
		return Box.createVerticalStrut(6);
	}
}
