package net.runelite.client.plugins.microbot.questhelper.prep;

import net.runelite.client.ui.PluginPanel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/** Status der Vorbereitung, Plan-Uebersicht und Gear-Buttons. Aktualisiert sich jede Sekunde. */
class QuestPrepPanel extends PluginPanel
{
	private static final Color BG = new Color(24, 24, 27);
	private static final Color CARD = new Color(36, 36, 41);
	private static final Color TEXT = new Color(235, 235, 240);
	private static final Color MUTED = new Color(150, 150, 160);
	private static final Color OK = new Color(80, 200, 120);
	private static final Color WARN = new Color(255, 170, 40);
	private static final Color BAD = new Color(230, 80, 80);
	private static final Color ACCENT = new Color(120, 100, 255);

	private final JLabel quest = new JLabel();
	private final JLabel state = new JLabel();
	private final JLabel message = new JLabel();
	private final JLabel gear = new JLabel();
	private final JPanel list = new JPanel();
	private final Timer timer = new Timer(1000, e -> refresh());

	QuestPrepPanel(Runnable onSetGear, Runnable onClearGear, Runnable onRestart)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(BG);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		JLabel title = new JLabel("Quest Prep");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		title.setForeground(TEXT);
		top.add(left(title));

		quest.setForeground(MUTED);
		state.setFont(state.getFont().deriveFont(Font.BOLD, 13f));
		message.setForeground(MUTED);
		top.add(left(quest));
		top.add(Box.createVerticalStrut(6));
		top.add(left(state));
		top.add(left(message));
		top.add(Box.createVerticalStrut(10));

		gear.setForeground(MUTED);
		top.add(left(gear));
		JPanel gearButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		gearButtons.setOpaque(false);
		gearButtons.add(button("Quest-Gear: Set", ACCENT, onSetGear));
		gearButtons.add(button("Gear leeren", CARD, onClearGear));
		top.add(left(gearButtons));
		top.add(Box.createVerticalStrut(6));
		top.add(left(button("Neu vorbereiten", CARD, onRestart)));
		top.add(Box.createVerticalStrut(12));
		add(top, BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.add(list, BorderLayout.NORTH);
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(wrap);
		scroll.setBorder(null);
		scroll.getViewport().setOpaque(false);
		scroll.setOpaque(false);
		add(scroll, BorderLayout.CENTER);
	}

	void start()
	{
		refresh();
		timer.start();
	}

	void stop()
	{
		timer.stop();
	}

	void refresh()
	{
		QuestPrep prep = QuestPrep.get();
		quest.setText(prep.getQuestName() == null ? "Keine Quest gewaehlt" : prep.getQuestName());
		state.setText(prep.getState().getLabel());
		switch (prep.getState())
		{
			case READY: state.setForeground(OK); break;
			case FAILED: state.setForeground(BAD); break;
			case CONFIRM: state.setForeground(WARN); break;
			default: state.setForeground(TEXT);
		}
		message.setText("<html><body style='width:190px'>" + prep.getMessage() + "</body></html>");
		gear.setText("Quest-Gear: " + GearStore.load().size() + " Items gespeichert");

		list.removeAll();
		for (TeleportSuggestion s : prep.getSuggestions())
		{
			list.add(row((s.isSelected() ? "[x] " : "[ ] ") + s.getName(), s.getCoveredSteps() + " Quest-Orte", ACCENT));
		}
		for (PrepItem i : prep.getPlan())
		{
			Color c = i.satisfied() ? OK : i.missingAfterBank() == 0 ? WARN : BAD;
			String detail = i.getHaveOnPlayer() + "/" + i.getNeeded() + " dabei, " + i.getHaveInBank() + " in Bank";
			list.add(row(i.getName(), detail, c));
		}
		list.revalidate();
		list.repaint();
	}

	private static Component row(String title, String detail, Color accent)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(CARD);
		p.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent), new EmptyBorder(4, 8, 4, 6)));
		JLabel t = new JLabel(title);
		t.setForeground(TEXT);
		JLabel d = new JLabel(detail);
		d.setForeground(MUTED);
		d.setFont(d.getFont().deriveFont(11f));
		p.add(t, BorderLayout.NORTH);
		p.add(d, BorderLayout.SOUTH);

		JPanel outer = new JPanel(new BorderLayout());
		outer.setOpaque(false);
		outer.setBorder(new EmptyBorder(0, 0, 4, 0));
		outer.add(p);
		outer.setAlignmentX(Component.LEFT_ALIGNMENT);
		outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
		return outer;
	}

	private static JButton button(String text, Color bg, Runnable action)
	{
		JButton b = new JButton(text);
		b.setBackground(bg);
		b.setForeground(TEXT);
		b.setFocusPainted(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	private static Component left(Component c)
	{
		((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		if (c instanceof JPanel) ((JPanel) c).setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		if (c instanceof JButton) ((JButton) c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return c;
	}
}
