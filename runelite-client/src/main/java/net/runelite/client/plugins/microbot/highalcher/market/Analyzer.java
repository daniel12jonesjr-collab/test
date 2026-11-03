package net.runelite.client.plugins.microbot.highalcher.market;

import net.runelite.client.plugins.microbot.highalcher.market.model.ItemMeta;
import net.runelite.client.plugins.microbot.highalcher.market.model.Opportunity;
import net.runelite.client.plugins.microbot.highalcher.market.model.PriceSnap;
import net.runelite.client.plugins.microbot.highalcher.market.model.Volume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reine Rechenlogik ohne RuneLite-Abhaengigkeiten -> schnell und einzeln testbar.
 * Ein Durchlauf ueber ~4.000 Items dauert wenige Millisekunden.
 */
public final class Analyzer
{
	public static final int NATURE_RUNE = 561;

	/** GE-Steuer: 2 % vom Verkaufspreis, abgerundet, max. 5 Mio., unter 50 gp steuerfrei. */
	static final double TAX_RATE = 0.02;
	static final int TAX_CAP = 5_000_000;
	static final int TAX_FREE_BELOW = 50;

	/** Grob: so viele High Alchs schafft man pro Stunde. Begrenzt den Alch-Score realistisch. */
	static final int ALCHS_PER_HOUR = 1_200;

	/** Einstellungen fuer einen Durchlauf (aus der Config uebernommen). */
	public static final class Settings
	{
		public int minAlchProfit = 1_500;
		public int minFlipMargin = 1_500;
		public int minAlchVolume = 10;
		public int minFlipVolume = 20;
		public int maxPriceAgeMinutes = 15;
		public long maxBuyPrice = Long.MAX_VALUE;
		public boolean includeMembers = true;
		public boolean includeNatureRune = true;
	}

	public static final class Result
	{
		public final List<Opportunity> alchs;
		public final List<Opportunity> flips;
		public final int natureRunePrice;

		Result(List<Opportunity> alchs, List<Opportunity> flips, int natureRunePrice)
		{
			this.alchs = Collections.unmodifiableList(alchs);
			this.flips = Collections.unmodifiableList(flips);
			this.natureRunePrice = natureRunePrice;
		}
	}

	private Analyzer() {}

	public static int geTax(int sellPrice)
	{
		if (sellPrice < TAX_FREE_BELOW) return 0;
		return (int) Math.min(TAX_CAP, Math.floor(sellPrice * TAX_RATE));
	}

	public static Result analyze(Map<Integer, ItemMeta> meta, Map<Integer, PriceSnap> prices,
		Map<Integer, Volume> volumes, Settings s, long nowSeconds)
	{
		PriceSnap nat = prices.get(NATURE_RUNE);
		int natureCost = s.includeNatureRune && nat != null ? Math.max(nat.high, nat.low) : 0;
		long maxAge = s.maxPriceAgeMinutes * 60L;

		List<Opportunity> alchs = new ArrayList<>();
		List<Opportunity> flips = new ArrayList<>();

		for (Map.Entry<Integer, PriceSnap> e : prices.entrySet())
		{
			ItemMeta item = meta.get(e.getKey());
			if (item == null || (!s.includeMembers && item.members)) continue;

			PriceSnap p = e.getValue();
			Volume v = volumes.get(item.id);
			int volHigh = v == null ? 0 : v.highVolume;
			int volMin = v == null ? 0 : v.min();
			int limit = item.limit > 0 ? item.limit : Integer.MAX_VALUE;

			// ---- Alch: sofort kaufen (high), alchen, Nature Rune bezahlen
			if (item.highAlch > 0 && p.high > 0 && p.high <= s.maxBuyPrice
				&& fresh(p.highTime, nowSeconds, maxAge))
			{
				int profit = item.highAlch - p.high - natureCost;
				if (profit >= s.minAlchProfit && volHigh >= s.minAlchVolume)
				{
					int perHour = Math.min(Math.min(limit, volHigh), ALCHS_PER_HOUR);
					long perLimit = (long) profit * Math.min(limit, 100_000);
					alchs.add(new Opportunity(Opportunity.Type.ALCH, item, p.high, item.highAlch, profit,
						100.0 * profit / (p.high + natureCost), volHigh, perLimit,
						nowSeconds - p.highTime, (double) profit * perHour));
				}
			}

			// ---- Flip: knapp ueber Sofort-Verkauf kaufen, knapp unter Sofort-Kauf verkaufen
			if (p.high > 0 && p.low > 0 && p.low < p.high && p.low + 1 <= s.maxBuyPrice
				&& fresh(p.highTime, nowSeconds, maxAge) && fresh(p.lowTime, nowSeconds, maxAge))
			{
				int buy = p.low + 1;
				int sell = p.high - 1;
				int margin = sell - buy - geTax(sell);
				if (margin >= s.minFlipMargin && volMin >= s.minFlipVolume)
				{
					int tradable = Math.min(limit, volMin);
					long age = nowSeconds - Math.min(p.highTime, p.lowTime);
					flips.add(new Opportunity(Opportunity.Type.FLIP, item, buy, sell, margin,
						100.0 * margin / buy, volMin, (long) margin * Math.min(limit, 100_000),
						age, (double) margin * tradable));
				}
			}
		}

		Comparator<Opportunity> byScore = Comparator.comparingDouble((Opportunity o) -> o.score).reversed();
		alchs.sort(byScore);
		flips.sort(byScore);
		return new Result(alchs, flips, natureCost);
	}

	private static boolean fresh(long time, long now, long maxAge)
	{
		return time > 0 && now - time <= maxAge;
	}
}
