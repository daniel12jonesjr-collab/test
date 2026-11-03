package net.runelite.client.plugins.microbot.pvmhotkeys.model;

import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Alle Zauber, die ein Preset ausloesen kann, plus Freeze-Dauern.
 * Microbot verteilt sie auf zwei Enums (Kampfzauber vs. Bind/Snare/Entangle), hier sind sie vereint.
 */
public final class Spells
{
	/** Nach jedem Freeze ist das Ziel 5 Ticks (3 s) immun gegen neue Freezes. */
	public static final int IMMUNITY_TICKS = 5;

	private static final Map<MagicAction, Spell> BY_ACTION = new EnumMap<>(MagicAction.class);
	private static final Map<MagicAction, Integer> FREEZE_TICKS = new EnumMap<>(MagicAction.class);
	private static final List<MagicAction> OPTIONS;

	static
	{
		// Runen-Daten aus Microbot (fuer den Runen-Check vor dem Zaubern)
		BY_ACTION.put(MagicAction.BIND, Rs2Spells.BIND);
		BY_ACTION.put(MagicAction.SNARE, Rs2Spells.SNARE);
		BY_ACTION.put(MagicAction.ENTANGLE, Rs2Spells.ENTANGLE);
		for (Rs2CombatSpells s : Rs2CombatSpells.values())
		{
			BY_ACTION.putIfAbsent(s.getMagicAction(), s);
		}

		// Dauer in Ticks (0,6 s)
		FREEZE_TICKS.put(MagicAction.BIND, 8);          //  5 s
		FREEZE_TICKS.put(MagicAction.SNARE, 16);        // 10 s
		FREEZE_TICKS.put(MagicAction.ENTANGLE, 25);     // 15 s
		FREEZE_TICKS.put(MagicAction.ICE_RUSH, 8);      //  5 s
		FREEZE_TICKS.put(MagicAction.ICE_BURST, 16);    // 10 s
		FREEZE_TICKS.put(MagicAction.ICE_BLITZ, 25);    // 15 s
		FREEZE_TICKS.put(MagicAction.ICE_BARRAGE, 33);  // 20 s

		// Kuratierte Liste fuers Standard-Zauberbuch: nur was im PvM wirklich genutzt wird
		List<MagicAction> opts = new ArrayList<>();
		// Festhalten (z. B. gegen Bosse, die weglaufen/teleportieren)
		add(opts, MagicAction.SNARE, MagicAction.ENTANGLE);
		// Schaden: Waves und Surges
		add(opts, MagicAction.WIND_WAVE, MagicAction.WATER_WAVE, MagicAction.EARTH_WAVE, MagicAction.FIRE_WAVE,
			MagicAction.WIND_SURGE, MagicAction.WATER_SURGE, MagicAction.EARTH_SURGE, MagicAction.FIRE_SURGE);
		// God Spells (mit Charge stark)
		add(opts, MagicAction.SARADOMIN_STRIKE, MagicAction.CLAWS_OF_GUTHIX, MagicAction.FLAMES_OF_ZAMORAK);
		OPTIONS = Collections.unmodifiableList(opts);
	}

	private static void add(List<MagicAction> list, MagicAction... actions)
	{
		for (MagicAction a : actions)
		{
			if (!list.contains(a)) list.add(a);
		}
	}

	/** Anzeige im Dropdown, z. B. "Fire Surge  (95)" oder "Ice Barrage  (94, Ancient)". */
	public static String label(MagicAction a)
	{
		if (a == null) return "-";
		String book = a.getSpellbook() == Rs2Spellbook.MODERN ? "" : ", Ancient";
		return a.getName() + "  (" + a.getLevel() + book + ")";
	}

	private Spells() {}

	public static List<MagicAction> options()
	{
		return OPTIONS;
	}

	public static Spell spell(MagicAction action)
	{
		return action == null ? null : BY_ACTION.get(action);
	}

	/** 0 = kein Freeze-Zauber. */
	public static int freezeTicks(MagicAction action)
	{
		return action == null ? 0 : FREEZE_TICKS.getOrDefault(action, 0);
	}
}
