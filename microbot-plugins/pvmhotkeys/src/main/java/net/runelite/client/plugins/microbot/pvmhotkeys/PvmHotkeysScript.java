package net.runelite.client.plugins.microbot.pvmhotkeys;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PvmHotkeysScript extends Script
{
	/** Wie oft der Script-Thread nach neuen Hotkey-Anfragen schaut (ms). Klein = schnelle Reaktion. */
	private static final long POLL_MS = 30;

	/**
	 * Immer nur EINE wartende Anfrage: drueckst du schnell zweimal, gewinnt die letzte Taste.
	 * AtomicReference, weil der AWT-Thread schreibt und der Script-Thread liest.
	 */
	private final AtomicReference<Rs2CombatSpells> pending = new AtomicReference<>();

	private PvmHotkeysConfig config;
	private int lastCastTick = Integer.MIN_VALUE / 2;

	/** Wird vom Hotkey-Listener (AWT-Thread) aufgerufen. Darf nur einreihen, nie blockieren. */
	public void request(Rs2CombatSpells spell)
	{
		if (spell != null && isRunning())
		{
			pending.set(spell);
		}
	}

	public boolean run(PvmHotkeysConfig config)
	{
		this.config = config;
		pending.set(null);

		// Bewusst NICHT super.run() als Gate: das setzt bei jedem Durchlauf Run-Energie/Config
		// und ist fuer eine 30ms-Schleife zu schwer. Wir pruefen nur, was wir wirklich brauchen.
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try
			{
				Rs2CombatSpells spell = pending.getAndSet(null);
				if (spell == null) return;
				if (!Microbot.isLoggedIn() || Microbot.pauseAllScripts.get()) return;

				handle(spell);
			}
			catch (Exception ex)
			{
				if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
				log.warn("PvM Hotkeys: Fehler beim Zaubern", ex);
			}
		}, 0, POLL_MS, TimeUnit.MILLISECONDS);
		return true;
	}

	private void handle(Rs2CombatSpells spell)
	{
		// 1) Angriffstempo: zu fruehe Eingaben verwerfen statt Zauber zu verschwenden.
		int tick = currentTick();
		int ticksSince = tick - lastCastTick;
		if (ticksSince < config.minTicksBetweenCasts())
		{
			feedback(spell.getName() + ": noch " + (config.minTicksBetweenCasts() - ticksSince) + " Tick(s) Cooldown");
			return;
		}

		// 2) Ziel bestimmen.
		Actor target = findTarget();
		if (target == null)
		{
			feedback(spell.getName() + ": kein Ziel");
			return;
		}

		// 3) Runen/Level/Zauberbuch pruefen, bevor irgendwas geklickt wird.
		if (!Rs2Magic.hasRequiredRunes(spell))
		{
			feedback(spell.getName() + ": nicht genug Runen");
			return;
		}

		// 4) Zaubern. castOn waehlt den Zauber aus und klickt dann das Ziel an.
		Microbot.status = "PvM: " + spell.getName() + " -> " + target.getName();
		if (Rs2Magic.castOn(spell, target))
		{
			lastCastTick = currentTick();
		}
		else
		{
			feedback(spell.getName() + ": konnte nicht gezaubert werden");
		}
	}

	private Actor findTarget()
	{
		Actor current = Rs2Player.getInteracting();
		if (current instanceof Rs2NpcModel && !((Rs2NpcModel) current).isDead())
		{
			return current;
		}
		if (!config.fallbackToNearest())
		{
			return null;
		}
		// Naechstes angreifbares Monster, das nicht schon mit jemand anderem kaempft.
		return Rs2Npc.getAttackableNpcs()
			.min(Comparator.comparingInt(n -> n.getDistanceFromPlayer()))
			.orElse(null);
	}

	private int currentTick()
	{
		return Microbot.getClientThread()
			.runOnClientThreadOptional(() -> Microbot.getClient().getTickCount())
			.orElse(0);
	}

	private void feedback(String msg)
	{
		if (config.chatFeedback())
		{
			Microbot.log(msg);
		}
	}

	@Override
	public void shutdown()
	{
		pending.set(null);
		super.shutdown();
	}
}
