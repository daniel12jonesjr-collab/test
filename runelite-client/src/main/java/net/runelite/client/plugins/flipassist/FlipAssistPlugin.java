package net.runelite.client.plugins.flipassist;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.flipassist.model.ItemMeta;
import net.runelite.client.plugins.flipassist.model.Opportunity;
import net.runelite.client.plugins.flipassist.model.PriceSnap;
import net.runelite.client.plugins.flipassist.model.Volume;
import net.runelite.client.plugins.flipassist.ui.FlipAssistPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Flip & Alch Assistant - reines Analyse-Werkzeug, klickt nichts im Spiel.
 *
 * Datenfluss (nichts davon auf dem Client- oder Swing-Thread, ausser dem Anzeigen):
 *   PriceService (eigener Thread) -> Analyzer (~2 ms) -> AlertManager -> Panel (EDT)
 */
@Slf4j
@PluginDescriptor(
	name = "Flip & Alch Assistant",
	description = "Live-Preise der Wiki: profitable High Alchs und Flips mit Benachrichtigung",
	tags = {"flip", "alch", "money", "ge", "grand exchange", "profit", "price"}
)
public class FlipAssistPlugin extends Plugin
{
	@Inject private FlipAssistConfig config;
	@Inject private ConfigManager configManager;
	@Inject private PriceService prices;
	@Inject private AlertManager alerts;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ItemManager itemManager;

	private FlipAssistPanel panel;
	private NavigationButton nav;
	private ExecutorService analysis;
	private volatile Analyzer.Result lastResult;

	@Provides
	FlipAssistConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(FlipAssistConfig.class);
	}

	@Override
	protected void startUp()
	{
		analysis = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "flipassist-analysis");
			t.setDaemon(true);
			return t;
		});
		panel = new FlipAssistPanel(itemManager, this::toggleWatch,
			() -> prices.refreshNow(config.refreshSeconds(), this::onPrices));
		nav = NavigationButton.builder().tooltip("Flip & Alch").icon(icon()).priority(6).panel(panel).build();
		clientToolbar.addNavigation(nav);

		alerts.reset();
		prices.start(config.refreshSeconds(), this::onPrices);
	}

	@Override
	protected void shutDown()
	{
		prices.stop();
		analysis.shutdownNow();
		clientToolbar.removeNavigation(nav);
		panel.dispose();
		panel = null;
		lastResult = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!FlipAssistConfig.GROUP.equals(e.getGroup())) return;
		if ("refreshSeconds".equals(e.getKey()))
		{
			prices.start(config.refreshSeconds(), this::onPrices);
		}
		else
		{
			// Schwellen geaendert: mit vorhandenen Daten neu rechnen, kein neuer Abruf noetig
			analysis.execute(() -> analyzeAndShow(false));
		}
	}

	/** PriceService-Thread: neue Preise da. */
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
		s.minAlchProfit = config.minAlchProfit();
		s.minFlipMargin = config.minFlipMargin();
		s.minAlchVolume = config.minAlchVolume();
		s.minFlipVolume = config.minFlipVolume();
		s.maxPriceAgeMinutes = config.maxPriceAge();
		s.maxBuyPrice = config.maxBuyPrice() > 0 ? config.maxBuyPrice() : Long.MAX_VALUE;
		s.includeMembers = config.includeMembers();
		s.includeNatureRune = config.includeNatureRune();
		return s;
	}

	/** Merkliste: dieselbe Rechnung, aber ohne Schwellen -> man sieht auch gerade schlechte Werte. */
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
		open.minFlipMargin = Integer.MIN_VALUE;
		open.minAlchVolume = 0;
		open.minFlipVolume = 0;
		open.maxPriceAgeMinutes = 24 * 60;
		open.maxBuyPrice = Long.MAX_VALUE;
		open.includeMembers = true;

		Analyzer.Result r = Analyzer.analyze(meta, sub, vol, open, now);
		List<Opportunity> out = new ArrayList<>();
		for (Opportunity o : r.alchs) if (ids.contains(o.item.id)) out.add(o);
		for (Opportunity o : r.flips) if (ids.contains(o.item.id)) out.add(o);
		return out;
	}

	// ---------------------------------------------------------------- Merkliste

	private Set<Integer> loadWatch()
	{
		String raw = configManager.getConfiguration(FlipAssistConfig.GROUP, FlipAssistConfig.WATCH_KEY);
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
		configManager.setConfiguration(FlipAssistConfig.GROUP, FlipAssistConfig.WATCH_KEY,
			ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
		analysis.execute(() -> analyzeAndShow(false));
	}

	private static BufferedImage icon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(255, 190, 60));
		g.fillOval(1, 1, 14, 14);
		g.setColor(new Color(120, 80, 10));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		g.drawString("$", 5, 12);
		g.dispose();
		return img;
	}
}
