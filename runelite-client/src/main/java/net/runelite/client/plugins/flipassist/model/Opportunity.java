package net.runelite.client.plugins.flipassist.model;

/** Eine gefundene Gelegenheit (Alch oder Flip). Unveraenderlich, sicher zwischen Threads teilbar. */
public final class Opportunity
{
	public enum Type { ALCH, FLIP }

	public final Type type;
	public final ItemMeta item;
	/** Preis, zu dem man kauft. */
	public final int buy;
	/** Alch-Wert bzw. Verkaufspreis. */
	public final int sell;
	/** Gewinn pro Stueck nach Kosten (Nature Rune bzw. GE-Steuer). */
	public final int profit;
	/** Rendite in Prozent. */
	public final double roi;
	/** Volumen pro Stunde (Flip: Engpass-Seite, Alch: Kaeufe). */
	public final int volume;
	/** Moeglicher Gewinn pro 4-h-Kauflimit. */
	public final long profitPerLimit;
	/** Alter der aeltesten verwendeten Preisangabe in Sekunden. */
	public final long ageSeconds;
	/** Sortier-Kennzahl: realistisch erzielbarer Gewinn. */
	public final double score;

	public Opportunity(Type type, ItemMeta item, int buy, int sell, int profit, double roi,
		int volume, long profitPerLimit, long ageSeconds, double score)
	{
		this.type = type;
		this.item = item;
		this.buy = buy;
		this.sell = sell;
		this.profit = profit;
		this.roi = roi;
		this.volume = volume;
		this.profitPerLimit = profitPerLimit;
		this.ageSeconds = ageSeconds;
		this.score = score;
	}

	public String key()
	{
		return type + ":" + item.id;
	}
}
