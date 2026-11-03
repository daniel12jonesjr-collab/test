package net.runelite.client.plugins.microbot.highalcher.market;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.highalcher.HighAlcherConfig;
import net.runelite.client.plugins.microbot.highalcher.NoticeCenter;
import net.runelite.client.plugins.microbot.highalcher.market.model.ItemMeta;
import net.runelite.client.plugins.microbot.highalcher.market.model.Opportunity;
import net.runelite.client.plugins.microbot.highalcher.market.model.PriceSnap;
import net.runelite.client.plugins.microbot.highalcher.market.model.Volume;
import net.runelite.client.plugins.microbot.highalcher.market.ui.BotControls;
import net.runelite.client.plugins.microbot.highalcher.market.ui.MarketPanel;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The market side of the High Alcher, formerly the separate Flip & Alch Assistant. Clicks nothing in the game.
 *
 * Data flow (none of it on the client or Swing thread, except showing it):
 *   PriceService (own thread) -> Analyzer (~2 ms) -> AlertManager -> panel (EDT)
 * Plus the GE buy tracker for the 4-hour buy limits. The plugin calls start/stop and forwards its events here.
 */
@Slf4j
@Singleton
public class MarketService
{
	private static final String WATCH_KEY = "watchIds";

	@Inject private HighAlcherConfig config;
	@Inject private ConfigManager configManager;
	@Inject private PriceService prices;
	@Inject private AlertManager alerts;
	@Inject private ItemManager itemManager;
	@Inject private BuyTracker buys;
	@Inject private Notifier notifier;
	@Inject private NoticeCenter notices;

	private MarketPanel panel;
	private ExecutorService analysis;
	private ScheduledExecutorService limitTimer;
	private volatile Analyzer.Result lastResult;

	/** Builds the panel (its buttons drive {@code bot}) and starts fetching prices. */
	public MarketPanel start(BotControls bot)
	{
		analysis = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "highalcher-analysis");
			t.setDaemon(true);
			return t;
		});
		buys.load();
		panel = new MarketPanel(itemManager, this::toggleWatch,
			() -> prices.refreshNow(config.refreshSeconds(), this::onPrices),
			buys::snapshot, this::buyLimit, this::removeBuy, bot);

		alerts.reset();
		prices.start(config.refreshSeconds(), this::onPrices);

		limitTimer = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "highalcher-limits");
			t.setDaemon(true);
			return t;
		});
		limitTimer.scheduleWithFixedDelay(this::checkFreeLimits, 10, 30, TimeUnit.SECONDS);
		return panel;
	}

	public void stop()
	{
		prices.stop();
		analysis.shutdownNow();
		limitTimer.shutdownNow();
		panel.dispose();
		panel = null;
		lastResult = null;
	}

	/** A High Alcher setting changed. */
	public void onConfigChanged(String key)
	{
		if ("refreshSeconds".equals(key))
		{
			prices.start(config.refreshSeconds(), this::onPrices);
		}
		else
		{
			// Thresholds changed: recompute with the data we have, no new fetch needed
			analysis.execute(() -> analyzeAndShow(false));
		}
	}

	/** Redraws the panel, e.g. after the bot started or stopped. */
	public void refreshPanel()
	{
		if (analysis != null) analysis.execute(() -> analyzeAndShow(false));
	}

	/** PriceService thread: new prices are in. */
	private void onPrices()
	{
		analysis.execute(() -> analyzeAndShow(true));
	}

	private void analyzeAndShow(boolean withAlerts)
	{
		Map<Integer, ItemMeta> meta = prices.getMeta();
		Map<Integer, PriceSnap> snap = prices.getPrices();
		Map<Integer, Volume> vol = prices.getVolumes();
		long now = System.currentTimeMillis() / 1000;

		Analyzer.Result result = snap.isEmpty() ? null : Analyzer.analyze(meta, snap, vol, settings(), now);
		if (result != null)
		{
			lastResult = result;
			if (withAlerts) alerts.process(result, config);
		}

		List<Opportunity> watched = watchedNow(meta, snap, vol, now);
		Set<String> fresh = alerts.getFresh();
		Set<Integer> watchIds = loadWatch();
		long updated = prices.getLastUpdateMillis();
		String error = prices.getLastError();
		int rows = config.maxRows();

		SwingUtilities.invokeLater(() -> {
			if (panel != null) panel.update(lastResult, watched, fresh, watchIds, updated, error, rows);
		});
	}

	private Analyzer.Settings settings()
	{
		Analyzer.Settings s = new Analyzer.Settings();
		s.minAlchProfit = config.marketMinProfit();
		s.minAlchVolume = config.marketMinVolume();
		// Flips are not shown any more
		s.minFlipMargin = Integer.MAX_VALUE;
		s.maxPriceAgeMinutes = config.maxPriceAge();
		s.maxBuyPrice = config.maxBuyPrice() > 0 ? config.maxBuyPrice() : Long.MAX_VALUE;
		s.includeMembers = config.includeMembers();
		s.includeNatureRune = true;
		return s;
	}

	/** Watch list: the same maths without thresholds, so watched items show up even while they are bad. */
	private List<Opportunity> watchedNow(Map<Integer, ItemMeta> meta, Map<Integer, PriceSnap> snap,
		Map<Integer, Volume> vol, long now)
	{
		Set<Integer> ids = loadWatch();
		if (ids.isEmpty() || snap.isEmpty()) return Collections.emptyList();

		Map<Integer, PriceSnap> sub = new HashMap<>();
		for (int id : ids)
		{
			PriceSnap p = snap.get(id);
			if (p != null) sub.put(id, p);
		}
		PriceSnap nat = snap.get(Analyzer.NATURE_RUNE);
		if (nat != null) sub.putIfAbsent(Analyzer.NATURE_RUNE, nat);

		Analyzer.Settings open = settings();
		open.minAlchProfit = Integer.MIN_VALUE;
		open.minAlchVolume = 0;
		open.maxPriceAgeMinutes = 24 * 60;
		open.maxBuyPrice = Long.MAX_VALUE;
		open.includeMembers = true;

		Analyzer.Result r = Analyzer.analyze(meta, sub, vol, open, now);
		List<Opportunity> out = new ArrayList<>();
		for (Opportunity o : r.alchs) if (ids.contains(o.item.id)) out.add(o);
		return out;
	}

	// ---------------------------------------------------------------- Watch list

	private Set<Integer> loadWatch()
	{
		String raw = configManager.getConfiguration(BuyTracker.STORE_GROUP, WATCH_KEY);
		Set<Integer> ids = new LinkedHashSet<>();
		if (raw == null || raw.isEmpty()) return ids;
		for (String s : raw.split(","))
		{
			try { ids.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
		}
		return ids;
	}

	private void toggleWatch(int id)
	{
		Set<Integer> ids = loadWatch();
		if (!ids.remove(id)) ids.add(id);
		configManager.setConfiguration(BuyTracker.STORE_GROUP, WATCH_KEY,
			ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
		analysis.execute(() -> analyzeAndShow(false));
	}

	// ---------------------------------------------------------------- Buy limit tracker

	/** Client thread: any change to a GE offer, also on login. */
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (offer == null || offer.getItemId() <= 0) return;
		String name = itemManager.getItemComposition(offer.getItemId()).getName();
		buys.onOffer(event.getSlot(), offer.getItemId(), offer.getQuantitySold(), offer.getState(), name,
			System.currentTimeMillis());
	}

	/** Every 30 s: reports items whose 4-hour window just ran out. */
	private void checkFreeLimits()
	{
		try
		{
			List<BuyTracker.Entry> free = buys.newlyFree(System.currentTimeMillis());
			if (!config.notifyLimitFree()) return;
			for (BuyTracker.Entry e : free)
			{
				int limit = buyLimit(e.itemId);
				String text = "Kauflimit wieder frei: " + e.name + (limit > 0 ? " (" + limit + " Stück)" : "");
				notifier.notify(text);
				notices.add(text, NoticeCenter.GOOD);
			}
		}
		catch (Exception ex)
		{
			log.debug("High Alcher: buy limit check failed", ex);
		}
	}

	private int buyLimit(int itemId)
	{
		ItemMeta meta = prices.getMeta().get(itemId);
		return meta == null ? 0 : meta.limit;
	}

	private void removeBuy(int itemId)
	{
		buys.remove(itemId);
		// Redraws the panel, so the card disappears right away
		analysis.execute(() -> analyzeAndShow(false));
	}
}
