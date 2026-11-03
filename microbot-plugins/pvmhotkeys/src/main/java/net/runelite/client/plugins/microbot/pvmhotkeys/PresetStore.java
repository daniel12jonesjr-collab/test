package net.runelite.client.plugins.microbot.pvmhotkeys;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Preset;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Haelt alle Presets und speichert sie als JSON in der RuneLite-Config.
 * CopyOnWriteArrayList: Der Key-Listener liest sehr oft, das Panel schreibt selten.
 */
@Slf4j
@Singleton
public class PresetStore
{
	private static final String KEY = "presets";
	private static final Type LIST_TYPE = new TypeToken<ArrayList<Preset>>() {}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private final List<Preset> presets = new CopyOnWriteArrayList<>();

	@Inject
	PresetStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void load()
	{
		presets.clear();
		String json = configManager.getConfiguration(PvmHotkeysConfig.GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			List<Preset> loaded = gson.fromJson(json, LIST_TYPE);
			if (loaded != null)
			{
				presets.addAll(loaded);
			}
		}
		catch (Exception e)
		{
			log.warn("PvM Hotkeys: gespeicherte Presets konnten nicht gelesen werden", e);
		}
	}

	public void save()
	{
		configManager.setConfiguration(PvmHotkeysConfig.GROUP, KEY, gson.toJson(new ArrayList<>(presets), LIST_TYPE));
	}

	public List<Preset> all()
	{
		return Collections.unmodifiableList(presets);
	}

	public Preset add()
	{
		Preset p = new Preset();
		p.setName("Preset " + (presets.size() + 1));
		presets.add(p);
		save();
		return p;
	}

	public void remove(Preset p)
	{
		presets.remove(p);
		save();
	}

	public void move(Preset p, int delta)
	{
		int i = presets.indexOf(p);
		int j = i + delta;
		if (i < 0 || j < 0 || j >= presets.size()) return;
		presets.remove(i);
		presets.add(j, p);
		save();
	}
}
