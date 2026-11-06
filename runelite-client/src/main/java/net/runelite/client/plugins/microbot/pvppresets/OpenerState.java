package net.runelite.client.plugins.microbot.pvppresets;

public class OpenerState {
    public String spell1 = "TELE_BLOCK";
    public String spell2 = "ENTANGLE";
    public String spell3 = "NONE";
    /** 0 = kein Preset. */
    public int gearPreset = 0;
    public int followPreset = 0;

    String spellList() {
        StringBuilder sb = new StringBuilder();
        for (String s : new String[]{spell1, spell2, spell3}) if (!"NONE".equals(s)) sb.append(sb.length() > 0 ? "," : "").append(s);
        return sb.length() == 0 ? "NONE" : sb.toString();
    }
}
