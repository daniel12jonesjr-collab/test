package net.runelite.client.plugins.microbot.questhelper.prep;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Schnelle Pruefungen vor JEDEM Quest-Schritt. Gibt true zurueck, wenn eine Aktion ausgefuehrt wurde
 * (dann laesst QuestScript den Schritt einmal aus, damit die Aktion im Spiel ankommt).
 */
public final class StepGuard
{
	private StepGuard() {}

	public static boolean check(QuestPrepConfig config)
	{
		// 1) Leben: zuerst essen, alles andere ist zweitrangig
		if (config.eatAtPercent() > 0 && Rs2Player.getHealthPercentage() < config.eatAtPercent())
		{
			if (Rs2Player.eatAt(config.eatAtPercent()))
			{
				Microbot.status = "Quest: essen";
				return true;
			}
		}

		int energy = Rs2Player.getRunEnergy();

		// 2) Stamina/Energy, wenn knapp und kein Stamina-Effekt aktiv
		if (energy < config.staminaBelow() && !Rs2Player.hasStaminaBuffActive())
		{
			int before = energy;
			Rs2Inventory.useRestoreEnergyItem();
			if (Rs2Player.getRunEnergy() != before || Rs2Player.hasStaminaBuffActive())
			{
				Microbot.status = "Quest: Stamina";
				return true;
			}
		}

		// 3) Run immer an, sobald etwas Energie da ist
		if (!Rs2Player.isRunEnabled() && energy >= 5)
		{
			Rs2Player.toggleRunEnergy(true);
		}
		return false;
	}
}
