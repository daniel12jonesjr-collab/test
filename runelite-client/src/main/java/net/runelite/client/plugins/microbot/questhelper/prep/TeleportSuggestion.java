package net.runelite.client.plugins.microbot.questhelper.prep;

import lombok.Data;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;

/** Ein vorgeschlagener Teleport + die Items, die er braucht. */
@Data
public class TeleportSuggestion
{
	private String name;
	private WorldPoint destination;
	/** Wie viele Quest-Orte liegen in der Naehe des Ziels. */
	private int coveredSteps;
	/** Benoetigte Items (Runen, Tab, Schmuck ...), Mengen schon fuer mehrere Nutzungen hochgerechnet. */
	private List<PrepItem> items = new ArrayList<>();
	private boolean selected = true;

	public boolean fullyOwned()
	{
		return items.stream().allMatch(i -> i.getHaveOnPlayer() + i.getHaveInBank() >= i.getNeeded());
	}

	public long buyCost()
	{
		long sum = 0;
		for (PrepItem i : items)
		{
			if (i.getUnitPrice() > 0) sum += (long) i.getUnitPrice() * i.missingAfterBank();
		}
		return sum;
	}
}
