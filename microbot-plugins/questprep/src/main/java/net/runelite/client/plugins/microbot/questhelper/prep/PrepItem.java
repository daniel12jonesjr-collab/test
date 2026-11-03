package net.runelite.client.plugins.microbot.questhelper.prep;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Eine Zeile im Vorbereitungsplan. */
@Data
public class PrepItem
{
	public enum Source { QUEST, RECOMMENDED, LOADOUT, GEAR, TELEPORT }

	private String name;
	/** Alle akzeptierten IDs (Alternativen). Die erste handelbare wird gekauft. */
	private List<Integer> ids = new ArrayList<>();
	private int needed;
	private int haveOnPlayer;
	private int haveInBank;
	private boolean tradeable;
	private boolean equip;
	private Source source;

	/** ID, die gekauft wird (-1 = nicht kaufbar). */
	private int buyId = -1;
	private int unitPrice;

	public int missingAfterBank()
	{
		return Math.max(0, needed - haveOnPlayer - haveInBank);
	}

	public int toWithdraw()
	{
		return Math.max(0, Math.min(needed - haveOnPlayer, haveInBank));
	}

	public boolean satisfied()
	{
		return haveOnPlayer >= needed;
	}
}
