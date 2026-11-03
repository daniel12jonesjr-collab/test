package net.runelite.client.plugins.microbot.questhelper.prep;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Ersatz fuer Rs2Walker.walkTo in QuestScript.
 *
 * Rs2Walker.walkTo nutzt nur Teleports, die schon im Inventar sind. Bei weiten Wegen vergleicht
 * walkWithBankedTransports die direkte Route mit "erst zur Bank, Teleport holen, dann teleportieren"
 * und nimmt die schnellere. So rennt der Bot nicht mehr quer durch die Welt.
 */
public final class QuestTravel
{
	private QuestTravel() {}

	public static boolean walkTo(WorldPoint target)
	{
		return walkTo(target, -1);
	}

	public static boolean walkTo(WorldPoint target, int distance)
	{
		if (target == null) return false;

		QuestPrepConfig config = config();
		WorldPoint me = Rs2Player.getWorldLocation();
		boolean far = me == null || me.getPlane() != target.getPlane() || me.distanceTo2D(target) > config.longWalkTiles();

		if (config.enabled() && config.useBankTeleports() && far && !Microbot.getClient().isInInstancedRegion())
		{
			Microbot.status = "Quest: weiter Weg - pruefe Teleports";
			return Rs2Walker.walkWithBankedTransports(target, Math.max(distance, 1), false);
		}
		return distance < 0 ? Rs2Walker.walkTo(target) : Rs2Walker.walkTo(target, distance);
	}

	static QuestPrepConfig config()
	{
		return Microbot.getConfigManager().getConfig(QuestPrepConfig.class);
	}
}
