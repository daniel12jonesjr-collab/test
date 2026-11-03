package net.runelite.client.plugins.microbot.pvmhotkeys.model;

import lombok.Data;
import net.runelite.client.config.Keybind;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ein Hotkey-Preset: Gear + Gebete + Aktion. Wird als JSON in der RuneLite-Config gespeichert,
 * deshalb nur einfache Felder (keine RuneLite-Objekte direkt).
 */
@Data
public class Preset
{
	private String id = UUID.randomUUID().toString();
	private String name = "Neues Preset";

	/** Hotkey: KeyEvent-Code + Modifier. 0 = nicht gesetzt. */
	private int keyCode;
	private int modifiers;

	/** Item-IDs der gespeicherten Ausruestung (leere Slots werden nicht gespeichert). */
	private List<Integer> gear = new ArrayList<>();

	/** Namen von Rs2PrayerEnum-Werten. */
	private List<String> prayers = new ArrayList<>();

	private PresetAction action = PresetAction.NONE;

	/** Name eines MagicAction-Werts (z. B. "ENTANGLE"), nur relevant bei CAST_SPELL. */
	private String spell = MagicAction.EARTH_SURGE.name();

	/** Mindest-Spec-Energie in Prozent fuer SPECIAL_ATTACK (z. B. DDS = 25). */
	private int specPercent = 25;

	public boolean hasHotkey()
	{
		return keyCode != 0;
	}

	public Keybind toKeybind()
	{
		return hasHotkey() ? new Keybind(keyCode, modifiers) : Keybind.NOT_SET;
	}

	public void setKeybind(Keybind kb)
	{
		keyCode = kb == null ? 0 : kb.getKeyCode();
		modifiers = kb == null ? 0 : kb.getModifiers();
	}

	public MagicAction spellOrNull()
	{
		try
		{
			return spell == null ? null : MagicAction.valueOf(spell);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	public List<Rs2PrayerEnum> prayerEnums()
	{
		List<Rs2PrayerEnum> out = new ArrayList<>();
		for (String p : prayers)
		{
			try
			{
				out.add(Rs2PrayerEnum.valueOf(p));
			}
			catch (IllegalArgumentException ignored)
			{
				// Prayer wurde in einer neuen Version umbenannt -> ignorieren statt crashen
			}
		}
		return out;
	}
}
