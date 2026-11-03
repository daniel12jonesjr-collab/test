package net.runelite.client.plugins.flipassist.model;

/**
 * Letzte Preise aus /latest.
 * high = Sofort-Kauf-Preis (was Kaeufer zuletzt bezahlt haben)
 * low  = Sofort-Verkauf-Preis (was Verkaeufer zuletzt bekommen haben)
 * Zeiten in Unix-Sekunden, 0 = unbekannt.
 */
public final class PriceSnap
{
	public final int high;
	public final long highTime;
	public final int low;
	public final long lowTime;

	public PriceSnap(int high, long highTime, int low, long lowTime)
	{
		this.high = high;
		this.highTime = highTime;
		this.low = low;
		this.lowTime = lowTime;
	}
}
