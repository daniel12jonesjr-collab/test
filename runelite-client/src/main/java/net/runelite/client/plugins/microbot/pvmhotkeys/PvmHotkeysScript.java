package net.runelite.client.plugins.microbot.pvmhotkeys;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Preset;
import net.runelite.client.plugins.microbot.pvmhotkeys.model.Spells;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Fuehrt Presets aus. Reihenfolge ist auf Tempo optimiert:
 *
 *   1. Gebete   – reine Menue-Aktionen ohne Maus, landen im selben Tick
 *   2. Gear     – alle Items direkt hintereinander, KEIN Warten dazwischen
 *   3. Spec     – Spec-Orb aktivieren (nur wenn genug Energie)
 *   4. Aktion   – Zauber / Angriff auf ein MONSTER
 *
 * Ziele sind bewusst nur NPCs (PvM). Spieler werden nie anvisiert.
 */
@Slf4j
public class PvmHotkeysScript extends Script
{
	private static final long POLL_MS = 15;

	/** Latest-wins: Druecken waehrend ein Preset laeuft ersetzt das wartende, stapelt aber nicht. */
	private final AtomicReference<Preset> pending = new AtomicReference<>();

	private final TimerState timers;
	private PvmHotkeysConfig config;
	private Consumer<Preset> onExecuted = p -> {};

	@Inject
	PvmHotkeysScript(TimerState timers)
	{
		this.timers = timers;
	}

	public void setOnExecuted(Consumer<Preset> onExecuted)
	{
		this.onExecuted = onExecuted == null ? p -> {} : onExecuted;
	}

	/** Vom Key-Listener (AWT-Thread) aufgerufen: nur einreihen, nie blockieren. */
	public void request(Preset preset)
	{
		if (preset != null && isRunning())
		{
			pending.set(preset);
		}
	}

	public boolean run(PvmHotkeysConfig config)
	{
		this.config = config;
		pending.set(null);

		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try
			{
				Preset preset = pending.getAndSet(null);
				if (preset == null) return;
				if (!Microbot.isLoggedIn() || Microbot.pauseAllScripts.get()) return;

				long start = System.nanoTime();
				execute(preset);
				log.debug("Preset '{}' in {} ms", preset.getName(), (System.nanoTime() - start) / 1_000_000);
				onExecuted.accept(preset);
			}
			catch (Exception ex)
			{
				if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
				log.warn("PvM Hotkeys: Fehler beim Ausfuehren", ex);
			}
		}, 0, POLL_MS, TimeUnit.MILLISECONDS);
		return true;
	}

	private void execute(Preset p)
	{
		List<String> problems = new ArrayList<>();
		long t0 = System.nanoTime();

		switchPrayers(p, problems);
		long t1 = System.nanoTime();

		switchGear(p, problems);
		long t2 = System.nanoTime();

		switch (p.getAction())
		{
			case CAST_SPELL:
				castSpell(p, problems);
				break;
			case SPECIAL_ATTACK:
				specialAttack(p, problems);
				break;
			case ATTACK:
				attack(problems);
				break;
			case NONE:
			default:
				break;
		}
		long t3 = System.nanoTime();

		if (!problems.isEmpty())
		{
			feedback(p.getName() + ": " + String.join(", ", problems));
		}
		if (config.showTimings())
		{
			// Zeigt, welcher Schritt bremst. Ziel: alles zusammen deutlich unter einem Tick (600 ms).
			Microbot.log(String.format("%s: %d ms  (Gebet %d | Gear %d | Aktion %d)",
				p.getName(), ms(t0, t3), ms(t0, t1), ms(t1, t2), ms(t2, t3)));
		}
	}

	private static long ms(long from, long to)
	{
		return (to - from) / 1_000_000;
	}

	// ------------------------------------------------------------------ Gebete

	private void switchPrayers(Preset p, List<String> problems)
	{
		List<Rs2PrayerEnum> wanted = p.prayerEnums();
		if (wanted.isEmpty()) return;
		if (Rs2Prayer.isOutOfPrayer())
		{
			problems.add("keine Gebetspunkte");
			return;
		}
		for (Rs2PrayerEnum prayer : wanted)
		{
			// toggle(prayer) ohne "on"-Parameter wartet NICHT (die Variante mit Boolean wartet bis zu 10 s!).
			// Sich ausschliessende Gebete (z. B. Piety/Augury) schaltet das Spiel selbst ab.
			if (!Rs2Prayer.isPrayerActive(prayer))
			{
				Rs2Prayer.toggle(prayer);
			}
		}
	}

	// ------------------------------------------------------------------ Gear

	private void switchGear(Preset p, List<String> problems)
	{
		FastEquip.Result r = FastEquip.equip(p.getGear());
		if (r.missing > 0)
		{
			problems.add(r.missing + " Item(s) nicht im Inventar");
		}
	}

	// ------------------------------------------------------------------ Aktionen

	private void castSpell(Preset p, List<String> problems)
	{
		MagicAction spell = p.spellOrNull();
		if (spell == null)
		{
			problems.add("kein Zauber gewaehlt");
			return;
		}

		if (timers.getTick() - timers.getLastCastTick() < config.minTicksBetweenCasts())
		{
			problems.add("Zauber-Cooldown");
			return;
		}

		Rs2NpcModel target = findTarget();
		if (target == null)
		{
			problems.add("kein Monster als Ziel");
			return;
		}

		// Billige Vorab-Checks (Varbits/Cache) statt Rs2Magic.canCast mit Tab-Wechseln
		if (!Rs2Magic.isSpellbook(spell.getSpellbook()))
		{
			problems.add("falsches Zauberbuch fuer " + spell.getName());
			return;
		}
		if (Rs2Player.getRealSkillLevel(Skill.MAGIC) < spell.getLevel())
		{
			problems.add("Magie-Level zu niedrig fuer " + spell.getName());
			return;
		}
		Spell runeData = Spells.spell(spell);
		if (runeData != null && !Rs2Magic.hasRequiredRunes(runeData))
		{
			problems.add("nicht genug Runen fuer " + spell.getName());
			return;
		}

		Microbot.status = "PvM: " + spell.getName() + " -> " + target.getName();
		try
		{
			boolean ok = FastCaster.castOn(spell, target);
			if (!ok)
			{
				// Evtl. Zauberbuch gewechselt -> Widget-IDs neu suchen und einmal wiederholen
				FastCaster.clearCache();
				ok = FastCaster.castOn(spell, target);
			}
			if (ok)
			{
				timers.onCast(spell, target, timers.getTick());
			}
			else
			{
				problems.add(spell.getName() + " fehlgeschlagen");
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private void specialAttack(Preset p, List<String> problems)
	{
		int required = p.getSpecPercent() * 10; // Spiel rechnet in 0..1000
		if (Rs2Combat.getSpecEnergy() < required)
		{
			problems.add("zu wenig Spec-Energie");
			return;
		}
		Rs2NpcModel target = findTarget();
		if (target == null)
		{
			problems.add("kein Monster als Ziel");
			return;
		}
		Rs2Combat.setSpecState(true, required);
		Rs2Npc.interact(target, "Attack");
	}

	private void attack(List<String> problems)
	{
		Rs2NpcModel target = findTarget();
		if (target == null)
		{
			problems.add("kein Monster als Ziel");
			return;
		}
		Rs2Npc.interact(target, "Attack");
	}

	/** Nur NPCs. Ein Spieler als aktuelles Ziel wird absichtlich ignoriert. */
	private Rs2NpcModel findTarget()
	{
		Actor current = Rs2Player.getInteracting();
		if (current instanceof Rs2NpcModel && !current.isDead())
		{
			return (Rs2NpcModel) current;
		}
		if (!config.fallbackToNearest())
		{
			return null;
		}
		return Rs2Npc.getAttackableNpcs()
			.min(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))
			.orElse(null);
	}

	// ------------------------------------------------------------------ Helfer

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
