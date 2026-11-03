package net.runelite.client.plugins.flipassist;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.runelite.client.plugins.flipassist.model.ItemMeta;
import net.runelite.client.plugins.flipassist.model.Opportunity;
import net.runelite.client.plugins.flipassist.model.PriceSnap;
import net.runelite.client.plugins.flipassist.model.Volume;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnalyzerTest
{
	private static final long NOW = 1_800_000_000L;

	private final Map<Integer, ItemMeta> meta = new HashMap<>();
	private final Map<Integer, PriceSnap> prices = new HashMap<>();
	private final Map<Integer, Volume> volumes = new HashMap<>();

	private void item(int id, boolean members, int limit, int alch, int high, int low, long age, int vol)
	{
		meta.put(id, new ItemMeta(id, "Item " + id, members, limit, alch));
		prices.put(id, new PriceSnap(high, NOW - age, low, NOW - age));
		volumes.put(id, new Volume(vol, vol));
	}

	private Analyzer.Result run(Analyzer.Settings s)
	{
		meta.put(561, new ItemMeta(561, "Nature rune", false, 18000, 0));
		prices.put(561, new PriceSnap(110, NOW - 30, 105, NOW - 30));
		return Analyzer.analyze(meta, prices, volumes, s, NOW);
	}

	private static boolean has(Iterable<Opportunity> list, int id)
	{
		for (Opportunity o : list) if (o.item.id == id) return true;
		return false;
	}

	@Test
	public void geTax()
	{
		assertEquals(20_999, Analyzer.geTax(1_049_999));
		assertEquals(0, Analyzer.geTax(49));
		assertEquals(5_000_000, Analyzer.geTax(2_000_000_000));
	}

	@Test
	public void alchThresholdIncludesNatureRune()
	{
		item(1, true, 70, 20_000, 18_000, 17_800, 60, 200); // 20000-18000-110 = 1890
		item(2, true, 70, 20_000, 18_400, 18_000, 60, 200); // 1490 -> zu wenig
		Analyzer.Result r = run(new Analyzer.Settings());
		assertTrue(has(r.alchs, 1));
		assertFalse(has(r.alchs, 2));
		assertEquals(1890, r.alchs.get(0).profit);
	}

	@Test
	public void ignoresStalePricesAndLowVolume()
	{
		item(3, true, 70, 30_000, 20_000, 19_000, 3600, 200);
		item(4, true, 70, 30_000, 20_000, 19_000, 60, 2);
		Analyzer.Result r = run(new Analyzer.Settings());
		assertFalse(has(r.alchs, 3));
		assertFalse(has(r.alchs, 4));
	}

	@Test
	public void flipMarginAfterTax()
	{
		item(10, true, 8, 0, 1_050_000, 1_000_000, 100, 50);
		item(11, true, 100, 0, 101_000, 100_000, 60, 500); // Steuer frisst Marge
		Analyzer.Result r = run(new Analyzer.Settings());
		assertTrue(has(r.flips, 10));
		assertFalse(has(r.flips, 11));
		assertEquals(28_999, r.flips.get(0).profit);
	}

	@Test
	public void membersFilter()
	{
		item(1, true, 70, 20_000, 18_000, 17_800, 60, 200);
		item(5, false, 100, 25_000, 20_000, 19_900, 60, 500);
		Analyzer.Settings s = new Analyzer.Settings();
		s.includeMembers = false;
		Analyzer.Result r = run(s);
		assertEquals(1, r.alchs.size());
		assertEquals(5, r.alchs.get(0).item.id);
	}

	@Test
	public void parsesWikiFormatWithNulls()
	{
		Gson g = new Gson();
		Map<Integer, ItemMeta> m = PriceParser.mapping(g.fromJson(
			"[{\"id\":1127,\"members\":false,\"limit\":70,\"highalch\":38880,\"name\":\"Rune platebody\"},"
				+ "{\"id\":13190,\"members\":false,\"name\":\"Old school bond\"}]", JsonElement.class));
		Map<Integer, PriceSnap> p = PriceParser.latest(g.fromJson(
			"{\"data\":{\"13190\":{\"high\":null,\"highTime\":null,\"low\":7000000,\"lowTime\":1700000000}}}", JsonElement.class));
		assertEquals(38_880, m.get(1127).highAlch);
		assertEquals(0, m.get(13190).limit);
		assertEquals(0, p.get(13190).high);
	}

	@Test
	public void formatting()
	{
		assertEquals("1.890", Format.gp(1890));
		assertEquals("132k", Format.gp(132_300));
		assertEquals("1,25M", Format.gp(1_250_000));
	}
}
