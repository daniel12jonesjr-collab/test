package net.runelite.client.plugins.microbot.pvmhotkeys;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Spells;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Gemeinsamer Zustand fuer Cooldown- und Freeze-Balken.
 * Schreiber: Script-Thread (onCast) und Client-Thread (onGameTick). Leser: Overlay (Client-Thread).
 * Alle Felder volatile, Werte sind einfache Zahlen -> keine Locks noetig.
 */
@Singleton
public class TimerState
{
	/** Ein Zauber trifft ca. 1-2 Ticks nach dem Wirken; so lange warten wir mit der Splash-Pruefung. */
	private static final int HIT_DELAY_TICKS = 2;

	private final Client client;

	@Getter private volatile int tick;
	@Getter private volatile long tickStartNanos = System.nanoTime();

	@Getter private volatile int lastCastTick = Integer.MIN_VALUE / 2;
	@Getter private volatile String lastSpellName = "";

	// Freeze
	@Getter private volatile String freezeSpellName;
	@Getter private volatile String freezeTargetName;
	@Getter private volatile int freezeStartTick;
	@Getter private volatile int freezeEndTick;
	private volatile int freezeNpcIndex = -1;
	private volatile WorldPoint freezeLocation;

	@Inject
	TimerState(Client client)
	{
		this.client = client;
	}

	/** Script-Thread: nach erfolgreichem Zauber. */
	public void onCast(MagicAction spell, NPC target, int castTick)
	{
		lastCastTick = castTick;
		lastSpellName = spell.getName();

		int freeze = Spells.freezeTicks(spell);
		if (freeze > 0 && target != null)
		{
			// Ziel noch immun oder bereits gefroren? Dann keinen neuen Timer starten.
			if (freezeNpcIndex == target.getIndex() && castTick < freezeEndTick + Spells.IMMUNITY_TICKS)
			{
				return;
			}
			freezeSpellName = spell.getName();
			freezeTargetName = target.getName();
			freezeNpcIndex = target.getIndex();
			freezeStartTick = castTick + HIT_DELAY_TICKS;
			freezeEndTick = freezeStartTick + freeze;
			freezeLocation = null;
		}
	}

	/** Client-Thread, jeden Tick: Zeitbasis + Splash/Tod erkennen. */
	public void onGameTick()
	{
		tick = client.getTickCount();
		tickStartNanos = System.nanoTime();

		if (freezeNpcIndex < 0) return;

		if (tick > freezeEndTick + Spells.IMMUNITY_TICKS)
		{
			clearFreeze();
			return;
		}

		NPC npc = client.getTopLevelWorldView().npcs().byIndex(freezeNpcIndex);
		if (npc == null || npc.isDead())
		{
			clearFreeze();
			return;
		}

		// Nach dem Treffer muss das Ziel stillstehen. Bewegt es sich trotzdem -> Splash oder immun.
		if (tick >= freezeStartTick && tick < freezeEndTick)
		{
			WorldPoint now = npc.getWorldLocation();
			if (freezeLocation == null)
			{
				freezeLocation = now;
			}
			else if (!freezeLocation.equals(now))
			{
				clearFreeze();
			}
		}
	}

	public boolean hasFreeze()
	{
		return freezeNpcIndex >= 0;
	}

	public void clearFreeze()
	{
		freezeNpcIndex = -1;
		freezeLocation = null;
		freezeSpellName = null;
		freezeTargetName = null;
	}

	/** Fortschritt innerhalb des aktuellen Ticks (0..1), fuer fluessige Balken statt 0,6-s-Spruengen. */
	public double tickFraction()
	{
		double f = (System.nanoTime() - tickStartNanos) / 600_000_000.0;
		return Math.max(0, Math.min(1, f));
	}
}
