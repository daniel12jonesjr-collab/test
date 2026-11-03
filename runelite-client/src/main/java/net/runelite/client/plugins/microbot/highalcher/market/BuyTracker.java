package net.runelite.client.plugins.microbot.highalcher.market;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Merkt sich, was du im GE gekauft hast, fuer das 4-Stunden-Kauflimit.
 *
 * Das Limit-Fenster startet mit dem ersten Kauf eines Items und gilt 4 Stunden, danach ist das volle Limit wieder frei.
 * Gezaehlt wird ueber GrandExchangeOfferChanged: pro GE-Slot merken wir uns Item und gefuellte Menge, jede Zunahme
 * ist ein Kauf. Alles wird in der Config gespeichert, damit es Neustarts und Logouts uebersteht.
 */
@Singleton
public class BuyTracker
{
	public static final long WINDOW_MS = TimeUnit.HOURS.toMillis(4);
	private static final int SLOTS = 8;
	/** Saved under the old Flip & Alch group, so buys and the watch list survive the merge. */
	public static final String STORE_GROUP = "flipassist";
	private static final String LOG_KEY = "buyLog";
	private static final String SLOTS_KEY = "buySlots";
	private static final String IGNORE_KEY = "buyIgnore";

	/** Ein verfolgtes Item im aktuellen Limit-Fenster. */
	public static final class Entry
	{
		public int itemId;
		public String name;
		/** Im aktuellen Fenster gekauft. */
		public int bought;
		/** Zeitpunkt des ersten Kaufs im Fenster (ms). */
		public long windowStart;
		/** Schon gemeldet, dass das Limit wieder frei ist. */
		public boolean notified;

		public long freeAt()
		{
			return windowStart + WINDOW_MS;
		}
	}

	private final ConfigManager configManager;
	private final Gson gson;

	private Map<Integer, Entry> entries = new LinkedHashMap<>();
	private Set<Integer> ignored = new LinkedHashSet<>();
	/** Pro GE-Slot: Item-ID und bisher gefuellte Menge, um aus jedem Update die neu gekauften Stueck zu errechnen. */
	private int[][] slots = new int[SLOTS][2];

	@Inject
	BuyTracker(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public synchronized void load()
	{
		Map<Integer, Entry> loaded = read(LOG_KEY, new TypeToken<LinkedHashMap<Integer, Entry>>() {});
		entries = loaded == null ? new LinkedHashMap<>() : loaded;
		int[][] loadedSlots = read(SLOTS_KEY, new TypeToken<int[][]>() {});
		slots = loadedSlots == null || loadedSlots.length != SLOTS ? new int[SLOTS][2] : loadedSlots;
		String raw = configManager.getConfiguration(STORE_GROUP, IGNORE_KEY);
		ignored = new LinkedHashSet<>();
		if (raw != null && !raw.isEmpty())
		{
			for (String s : raw.split(","))
			{
				try { ignored.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignoredValue) {}
			}
		}
	}

	/** Client-Thread: ein GE-Slot hat sich geaendert. */
	public synchronized void onOffer(int slot, int itemId, int filled, GrandExchangeOfferState state, String name, long now)
	{
		if (slot < 0 || slot >= SLOTS) return;
		boolean buy = state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
		// EMPTY kommt auch beim Einloggen vor den echten Angeboten, deshalb den Slot dabei nicht vergessen
		if (!buy) return;

		int[] s = slots[slot];
		// Anderes Item im Slot oder neues Angebot (Menge kleiner als zuletzt): von vorn zaehlen
		int delta = s[0] == itemId && filled >= s[1] ? filled - s[1] : filled;
		s[0] = itemId;
		s[1] = filled;
		if (delta > 0 && !ignored.contains(itemId))
		{
			add(itemId, name, delta, now);
		}
		save();
	}

	private void add(int itemId, String name, int quantity, long now)
	{
		Entry e = entries.get(itemId);
		if (e == null || now >= e.freeAt())
		{
			e = new Entry();
			e.itemId = itemId;
			e.name = name;
			e.windowStart = now;
			entries.put(itemId, e);
		}
		e.bought += quantity;
	}

	/** Entfernt das Item aus der Liste und verfolgt es nicht mehr (z. B. aus Versehen gekauft). */
	public synchronized void remove(int itemId)
	{
		entries.remove(itemId);
		ignored.add(itemId);
		save();
	}

	/** Items, deren Limit gerade wieder frei geworden ist. Jedes wird nur einmal zurueckgegeben. */
	public synchronized List<Entry> newlyFree(long now)
	{
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries.values())
		{
			if (!e.notified && now >= e.freeAt())
			{
				e.notified = true;
				out.add(e);
			}
		}
		if (!out.isEmpty()) save();
		return out;
	}

	/** Kopie fuer das Panel, das zuerst frei werdende Item oben. */
	public synchronized List<Entry> snapshot()
	{
		return entries.values().stream()
			.map(e -> {
				Entry c = new Entry();
				c.itemId = e.itemId;
				c.name = e.name;
				c.bought = e.bought;
				c.windowStart = e.windowStart;
				c.notified = e.notified;
				return c;
			})
			.sorted(Comparator.comparingLong(Entry::freeAt))
			.collect(Collectors.toList());
	}

	private void save()
	{
		configManager.setConfiguration(STORE_GROUP, LOG_KEY, gson.toJson(entries));
		configManager.setConfiguration(STORE_GROUP, SLOTS_KEY, gson.toJson(slots));
		configManager.setConfiguration(STORE_GROUP, IGNORE_KEY,
			ignored.stream().map(String::valueOf).collect(Collectors.joining(",")));
	}

	private <T> T read(String key, TypeToken<T> type)
	{
		String raw = configManager.getConfiguration(STORE_GROUP, key);
		if (raw == null || raw.isEmpty()) return null;
		try
		{
			return gson.fromJson(raw, type.getType());
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}
}
