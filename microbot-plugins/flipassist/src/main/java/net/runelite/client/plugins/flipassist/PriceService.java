package net.runelite.client.plugins.flipassist;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.flipassist.model.ItemMeta;
import net.runelite.client.plugins.flipassist.model.PriceSnap;
import net.runelite.client.plugins.flipassist.model.Volume;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Holt Preise von der OSRS-Wiki-API (https://prices.runescape.wiki).
 * Laeuft komplett auf einem eigenen Hintergrund-Thread -> Client und Panel ruckeln nie.
 *
 *   /mapping  Stammdaten (Name, Limit, Alch-Wert)   alle 6 h
 *   /1h       Volumen der letzten Stunde            alle 5 min
 *   /latest   aktuelle Preise                       jedes Update (Standard 60 s)
 *
 * Bei Fehlern bleiben die letzten Daten erhalten und die Wartezeit verdoppelt sich (max. 10 min).
 */
@Slf4j
@Singleton
public class PriceService
{
	private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/";
	/** Die Wiki bittet um einen aussagekraeftigen User-Agent. */
	private static final String USER_AGENT = "Flip & Alch Assistant - RuneLite plugin";
	private static final long MAPPING_EVERY_MS = TimeUnit.HOURS.toMillis(6);
	private static final long VOLUME_EVERY_MS = TimeUnit.MINUTES.toMillis(5);

	private final OkHttpClient http;
	private final Gson gson;

	@Getter private volatile Map<Integer, ItemMeta> meta = Collections.emptyMap();
	@Getter private volatile Map<Integer, PriceSnap> prices = Collections.emptyMap();
	@Getter private volatile Map<Integer, Volume> volumes = Collections.emptyMap();
	@Getter private volatile long lastUpdateMillis;
	@Getter private volatile String lastError;

	private long mappingAt;
	private long volumeAt;
	private int failures;
	private ScheduledExecutorService executor;

	@Inject
	PriceService(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	public synchronized void start(int refreshSeconds, Runnable onUpdate)
	{
		stop();
		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "flipassist-prices");
			t.setDaemon(true);
			return t;
		});
		scheduleNext(0, refreshSeconds, onUpdate);
	}

	public synchronized void stop()
	{
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
	}

	/** Sofort aktualisieren (Button im Panel). */
	public synchronized void refreshNow(int refreshSeconds, Runnable onUpdate)
	{
		start(refreshSeconds, onUpdate);
	}

	private void scheduleNext(long delaySeconds, int refreshSeconds, Runnable onUpdate)
	{
		ScheduledExecutorService ex = executor;
		if (ex == null || ex.isShutdown()) return;
		ex.schedule(() -> {
			boolean ok = cycle();
			if (ok) onUpdate.run();
			long next = ok ? refreshSeconds : Math.min(600, refreshSeconds * (1L << Math.min(failures, 4)));
			scheduleNext(next, refreshSeconds, onUpdate);
		}, delaySeconds, TimeUnit.SECONDS);
	}

	private boolean cycle()
	{
		long now = System.currentTimeMillis();
		try
		{
			if (meta.isEmpty() || now - mappingAt > MAPPING_EVERY_MS)
			{
				meta = PriceParser.mapping(get("mapping"));
				mappingAt = now;
			}
			if (volumes.isEmpty() || now - volumeAt > VOLUME_EVERY_MS)
			{
				volumes = PriceParser.volumes(get("1h"));
				volumeAt = now;
			}
			prices = PriceParser.latest(get("latest"));
			lastUpdateMillis = System.currentTimeMillis();
			lastError = null;
			failures = 0;
			return true;
		}
		catch (Exception e)
		{
			failures++;
			lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			log.debug("Flip Assistant: Preisabruf fehlgeschlagen", e);
			return false;
		}
	}

	private JsonElement get(String endpoint) throws IOException
	{
		Request req = new Request.Builder()
			.url(BASE + endpoint)
			.header("User-Agent", USER_AGENT)
			.build();
		try (Response res = http.newCall(req).execute())
		{
			ResponseBody body = res.body();
			if (!res.isSuccessful() || body == null)
			{
				throw new IOException("HTTP " + res.code() + " bei /" + endpoint);
			}
			try (Reader reader = body.charStream())
			{
				return gson.fromJson(reader, JsonElement.class);
			}
		}
	}
}
