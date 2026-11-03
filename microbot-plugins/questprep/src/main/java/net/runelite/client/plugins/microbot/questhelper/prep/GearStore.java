package net.runelite.client.plugins.microbot.questhelper.prep;

import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Speichert die per "Set" erfasste Quest-Ausruestung als Item-ID-Liste in der Config. */
final class GearStore
{
	private GearStore() {}

	static List<Integer> load()
	{
		List<Integer> ids = new ArrayList<>();
		String raw = Microbot.getConfigManager().getConfiguration(QuestPrepConfig.GROUP, QuestPrepConfig.GEAR_KEY);
		if (raw == null || raw.isBlank()) return ids;
		for (String s : raw.split(","))
		{
			try { ids.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
		}
		return ids;
	}

	static void save(List<Integer> ids)
	{
		Microbot.getConfigManager().setConfiguration(QuestPrepConfig.GROUP, QuestPrepConfig.GEAR_KEY,
			ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
	}

	static void clear()
	{
		save(new ArrayList<>());
	}
}
