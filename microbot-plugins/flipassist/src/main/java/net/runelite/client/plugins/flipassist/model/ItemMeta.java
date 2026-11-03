package net.runelite.client.plugins.flipassist.model;

/** Stammdaten eines Items aus /mapping (aendern sich selten). */
public final class ItemMeta
{
	public final int id;
	public final String name;
	public final boolean members;
	/** GE-Kauflimit pro 4 Stunden, 0 = unbekannt. */
	public final int limit;
	/** High-Alch-Wert, 0 = nicht alchbar. */
	public final int highAlch;

	public ItemMeta(int id, String name, boolean members, int limit, int highAlch)
	{
		this.id = id;
		this.name = name;
		this.members = members;
		this.limit = limit;
		this.highAlch = highAlch;
	}
}
