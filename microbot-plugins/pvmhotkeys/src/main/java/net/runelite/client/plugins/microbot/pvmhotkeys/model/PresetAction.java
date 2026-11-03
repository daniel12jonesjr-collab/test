package net.runelite.client.plugins.microbot.pvmhotkeys.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.Color;

/** Was nach Gear- und Gebetswechsel passieren soll. Bestimmt auch die Akzentfarbe der Karte. */
@Getter
@RequiredArgsConstructor
public enum PresetAction
{
	NONE("Nur Switch", new Color(160, 160, 170)),
	CAST_SPELL("Zauber", new Color(88, 140, 255)),
	SPECIAL_ATTACK("Special Attack", new Color(255, 170, 40)),
	ATTACK("Angriff", new Color(235, 75, 75));

	private final String label;
	private final Color accent;

	@Override
	public String toString()
	{
		return label;
	}
}
