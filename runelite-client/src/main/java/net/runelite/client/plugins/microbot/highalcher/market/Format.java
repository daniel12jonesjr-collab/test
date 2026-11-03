package net.runelite.client.plugins.microbot.highalcher.market;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Zahlen kompakt und deutsch: 1.890 | 132k | 1,25M */
public final class Format
{
	private static final DecimalFormatSymbols DE = DecimalFormatSymbols.getInstance(Locale.GERMANY);

	private Format() {}

	public static String gp(long v)
	{
		long a = Math.abs(v);
		String sign = v < 0 ? "-" : "";
		if (a >= 10_000_000) return sign + new DecimalFormat("0.#", DE).format(a / 1_000_000.0) + "M";
		if (a >= 1_000_000) return sign + new DecimalFormat("0.##", DE).format(a / 1_000_000.0) + "M";
		if (a >= 100_000) return sign + (a / 1_000) + "k";
		return sign + new DecimalFormat("#,##0", DE).format(a);
	}

	public static String pct(double v)
	{
		return new DecimalFormat("0.0", DE).format(v) + " %";
	}

	public static String age(long seconds)
	{
		if (seconds < 60) return seconds + " s";
		if (seconds < 3600) return (seconds / 60) + " min";
		return (seconds / 3600) + " h";
	}
}
