package net.runelite.client.plugins.microbot.pvppresets;

import java.util.LinkedHashMap;
import java.util.Map;

public class SustainState {
    public boolean enabled = false;
    public int eatAt = 50;
    public int prayerAt = 20;
    public boolean combo = true;
    /** Skill -> minimaler Boost (0 = aus). */
    public Map<String, Integer> buffs = new LinkedHashMap<>();
    {
        for (String s : new String[]{"ATTACK", "STRENGTH", "DEFENCE", "RANGED", "MAGIC"}) buffs.put(s, 0);
    }
}
