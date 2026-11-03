package net.runelite.client.plugins.flipassist.model;

/** Handelsvolumen der letzten Stunde aus /1h. */
public final class Volume
{
	public final int highVolume;
	public final int lowVolume;

	public Volume(int highVolume, int lowVolume)
	{
		this.highVolume = highVolume;
		this.lowVolume = lowVolume;
	}

	public int total()
	{
		return highVolume + lowVolume;
	}

	/** Engpass-Seite: fuer Flips muss man kaufen UND verkaufen koennen. */
	public int min()
	{
		return Math.min(highVolume, lowVolume);
	}
}
