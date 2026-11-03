package net.runelite.client.plugins.flipassist;

import net.runelite.api.ChatMessageType;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.plugins.flipassist.model.Opportunity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Meldet NEUE gute Gelegenheiten - ohne Spam:
 *  - beim ersten Update nur eine Zusammenfassung
 *  - danach nur Items, die neu dazugekommen sind oder deutlich besser wurden (+25 %)
 *  - pro Item eine Abklingzeit, pro Update eine Obergrenze
 */
@Singleton
public class AlertManager
{
	private final Notifier notifier;
	private final ChatMessageManager chat;

	private final Map<String, Long> lastAlertAt = new HashMap<>();
	private final Map<String, Integer> lastAlertProfit = new HashMap<>();
	private Set<String> previous = new HashSet<>();
	private boolean first = true;

	/** Keys, die beim letzten Update neu waren (fuer das "NEU"-Badge im Panel). */
	private volatile Set<String> fresh = new HashSet<>();

	@Inject
	AlertManager(Notifier notifier, ChatMessageManager chat)
	{
		this.notifier = notifier;
		this.chat = chat;
	}

	public Set<String> getFresh()
	{
		return fresh;
	}

	public synchronized void reset()
	{
		lastAlertAt.clear();
		lastAlertProfit.clear();
		previous = new HashSet<>();
		fresh = new HashSet<>();
		first = true;
	}

	public synchronized void process(Analyzer.Result result, FlipAssistConfig config)
	{
		List<Opportunity> all = new ArrayList<>(result.alchs);
		all.addAll(result.flips);

		Set<String> current = new HashSet<>();
		Set<String> newOnes = new HashSet<>();
		for (Opportunity o : all)
		{
			current.add(o.key());
			if (!previous.contains(o.key())) newOnes.add(o.key());
		}
		previous = current;
		fresh = first ? new HashSet<>() : newOnes;

		if (first)
		{
			first = false;
			if (!all.isEmpty())
			{
				send(config, "Flip & Alch: " + result.alchs.size() + " Alch- und " + result.flips.size()
					+ " Flip-Gelegenheiten gefunden", null);
			}
			all.forEach(o -> { lastAlertAt.put(o.key(), System.currentTimeMillis()); lastAlertProfit.put(o.key(), o.profit); });
			return;
		}

		long now = System.currentTimeMillis();
		long cooldown = config.alertCooldown() * 60_000L;
		int sent = 0;

		// Liste ist nach Score sortiert -> die besten zuerst melden
		for (Opportunity o : merged(result))
		{
			if (sent >= config.maxAlertsPerUpdate()) break;
			Long at = lastAlertAt.get(o.key());
			Integer before = lastAlertProfit.get(o.key());
			boolean isNew = at == null || now - at > cooldown;
			boolean muchBetter = before != null && o.profit >= before * 1.25;
			if (!isNew && !muchBetter) continue;

			lastAlertAt.put(o.key(), now);
			lastAlertProfit.put(o.key(), o.profit);
			send(config, text(o), o);
			sent++;
		}
	}

	private static List<Opportunity> merged(Analyzer.Result r)
	{
		List<Opportunity> m = new ArrayList<>(r.alchs);
		m.addAll(r.flips);
		m.sort((a, b) -> Double.compare(b.score, a.score));
		return m;
	}

	static String text(Opportunity o)
	{
		String kind = o.type == Opportunity.Type.ALCH ? "Alch" : "Flip";
		return kind + ": " + o.item.name + "  +" + Format.gp(o.profit) + " gp/Stk"
			+ (o.item.limit > 0 ? "  (Limit " + o.item.limit + " = " + Format.gp(o.profitPerLimit) + ")" : "");
	}

	private void send(FlipAssistConfig config, String message, Opportunity o)
	{
		if (config.notifyDesktop())
		{
			notifier.notify(message);
		}
		if (config.notifyChat())
		{
			String body = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(message)
				.build();
			chat.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE).runeLiteFormattedMessage(body).build());
		}
	}
}
