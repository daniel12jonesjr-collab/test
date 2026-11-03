package net.runelite.client.plugins.flipassist.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.flipassist.Format;
import net.runelite.client.plugins.flipassist.model.Opportunity;
import net.runelite.client.util.LinkBrowser;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Karte in drei Zeilen:
 *   [Icon]  Name                          NEU  M  *
 *           36.500 -> Alch 38.880          +2.270
 *           ROI 6,2 % . 400/h . 1 min     70x = 158k
 *
 * Linksklick: Name kopieren (fuer die GE-Suche). Rechtsklick: Wiki / Preisverlauf.
 */
class OpportunityCard extends RoundPanel
{
	OpportunityCard(Opportunity o, ItemManager items, boolean isNew, boolean watched, Consumer<Integer> toggleWatch)
	{
		super(new BorderLayout(8, 0), Style.CARD);
		setAccent(o.type == Opportunity.Type.ALCH ? Style.ALCH : Style.FLIP);
		setBorder(new EmptyBorder(7, 10, 7, 12));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Klick: Name kopieren  |  Rechtsklick: Wiki");

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		items.getImage(o.item.id).addTo(icon);
		add(icon, BorderLayout.WEST);

		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);

		// Zeile 1: Name | Tags + Stern
		JPanel tags = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		tags.setOpaque(false);
		if (isNew) tags.add(badge("NEU", Style.NEW));
		if (o.item.members) tags.add(label("M", Style.small(), Style.MUTED));
		JLabel star = label(watched ? "★" : "☆", Style.regular().deriveFont(14f), watched ? Style.STAR : Style.MUTED);
		star.setToolTipText(watched ? "Von Merkliste entfernen" : "Auf Merkliste");
		star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		star.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				e.consume();
				toggleWatch.accept(o.item.id);
			}
		});
		tags.add(star);
		rows.add(row(label(o.item.name, Style.bold(13f), Style.TEXT), tags));

		// Zeile 2: Preise | Gewinn
		String target = o.type == Opportunity.Type.ALCH ? "Alch " : "Verk. ";
		rows.add(row(label(Format.gp(o.buy) + " → " + target + Format.gp(o.sell), Style.small(), Style.MUTED),
			label("+" + Format.gp(o.profit), Style.bold(14f), Style.PROFIT)));

		// Zeile 3: Kennzahlen | Gewinn pro Limit
		JLabel perLimit = label(o.item.limit > 0 ? o.item.limit + "x = " + Format.gp(o.profitPerLimit) : "pro Stk",
			Style.small(), Style.MUTED);
		perLimit.setToolTipText("Gewinn bei vollem 4-h-Kauflimit");
		rows.add(row(label("ROI " + Format.pct(o.roi) + " · " + Format.gp(o.volume) + "/h · " + Format.age(o.ageSeconds),
			Style.small(), Style.MUTED), perLimit));

		add(rows, BorderLayout.CENTER);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem wiki = new JMenuItem("Wiki-Seite");
		wiki.addActionListener(e -> LinkBrowser.browse("https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=" + o.item.id));
		JMenuItem chart = new JMenuItem("Preisverlauf");
		chart.addActionListener(e -> LinkBrowser.browse("https://prices.runescape.wiki/osrs/item/" + o.item.id));
		menu.add(wiki);
		menu.add(chart);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e) { setFill(Style.CARD_HOVER); }

			@Override
			public void mouseExited(MouseEvent e) { setFill(Style.CARD); }

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger()) menu.show(OpportunityCard.this, e.getX(), e.getY());
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					menu.show(OpportunityCard.this, e.getX(), e.getY());
				}
				else if (e.getButton() == MouseEvent.BUTTON1)
				{
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(o.item.name), null);
					setToolTipText("\"" + o.item.name + "\" kopiert");
				}
			}
		});
	}

	/** Eine Zeile: links (wird bei Platzmangel mit ... gekuerzt), rechts (feste Breite). */
	private static JPanel row(JComponent left, JComponent right)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		left.setMinimumSize(new Dimension(0, left.getPreferredSize().height));
		left.setPreferredSize(new Dimension(0, left.getPreferredSize().height));
		p.add(left, BorderLayout.CENTER);
		p.add(right, BorderLayout.EAST);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JLabel label(String text, Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return l;
	}

	private static JLabel badge(String text, Color color)
	{
		JLabel l = label(text, Style.small(), color);
		l.setOpaque(true);
		l.setBackground(Style.alpha(color, 50));
		l.setBorder(new EmptyBorder(0, 4, 0, 4));
		return l;
	}
}
