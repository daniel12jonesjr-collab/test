package net.runelite.client.plugins.microbot.pvmhotkeys.ui;

import net.runelite.client.plugins.microbot.pvmhotkeys.model.Preset;

/** Was das Panel beim Plugin ausloesen darf. Haelt die UI frei von Spiellogik. */
public interface PanelActions
{
	/** Liest die aktuell angelegte Ausruestung (Client-Thread) und speichert sie im Preset. */
	void captureGear(Preset preset);

	void save();

	void delete(Preset preset);

	void move(Preset preset, int delta);
}
