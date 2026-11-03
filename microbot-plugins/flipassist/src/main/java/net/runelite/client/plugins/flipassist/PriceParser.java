package net.runelite.client.plugins.flipassist;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.client.plugins.flipassist.model.ItemMeta;
import net.runelite.client.plugins.flipassist.model.PriceSnap;
import net.runelite.client.plugins.flipassist.model.Volume;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Wandelt die JSON-Antworten der Wiki-API in Modelle um. Nur Gson -> einzeln testbar. */
public final class PriceParser
{
	private PriceParser() {}


	public static Map<Integer, ItemMeta> mapping(JsonElement json)
	{
		Map<Integer, ItemMeta> out = new HashMap<>(8192);
		for (JsonElement el : json.getAsJsonArray())
		{
			JsonObject o = el.getAsJsonObject();
			int id = intOf(o, "id");
			if (id <= 0) continue;
			out.put(id, new ItemMeta(id,
				o.has("name") ? o.get("name").getAsString() : "Item " + id,
				o.has("members") && o.get("members").getAsBoolean(),
				intOf(o, "limit"),
				intOf(o, "highalch")));
		}
		return Collections.unmodifiableMap(out);
	}

	public static Map<Integer, PriceSnap> latest(JsonElement json)
	{
		JsonObject data = json.getAsJsonObject().getAsJsonObject("data");
		Map<Integer, PriceSnap> out = new HashMap<>(8192);
		for (Map.Entry<String, JsonElement> e : data.entrySet())
		{
			JsonObject o = e.getValue().getAsJsonObject();
			out.put(Integer.parseInt(e.getKey()), new PriceSnap(
				intOf(o, "high"), longOf(o, "highTime"), intOf(o, "low"), longOf(o, "lowTime")));
		}
		return Collections.unmodifiableMap(out);
	}

	public static Map<Integer, Volume> volumes(JsonElement json)
	{
		JsonObject data = json.getAsJsonObject().getAsJsonObject("data");
		Map<Integer, Volume> out = new HashMap<>(8192);
		for (Map.Entry<String, JsonElement> e : data.entrySet())
		{
			JsonObject o = e.getValue().getAsJsonObject();
			out.put(Integer.parseInt(e.getKey()), new Volume(intOf(o, "highPriceVolume"), intOf(o, "lowPriceVolume")));
		}
		return Collections.unmodifiableMap(out);
	}

	/** Felder koennen fehlen oder null sein (z. B. Items ohne Limit oder ohne Handel). */
	private static int intOf(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? 0 : e.getAsInt();
	}

	private static long longOf(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? 0 : e.getAsLong();
	}
}
