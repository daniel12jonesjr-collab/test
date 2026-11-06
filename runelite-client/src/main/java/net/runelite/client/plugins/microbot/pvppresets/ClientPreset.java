package net.runelite.client.plugins.microbot.pvppresets;

import java.util.ArrayList;
import java.util.List;

/** Lokale Anzeige-Daten eines Presets. Ausgeführt wird immer serverseitig. */
public class ClientPreset {
    public int id;
    public String name;
    public String prayer = "NONE";
    public String spell = "NONE";
    /** Item-IDs der Ausrüstung beim letzten "Set current gear" (nur Vorschau). */
    public List<Integer> gear = new ArrayList<>();

    public ClientPreset() { }
    public ClientPreset(int id) { this.id = id; this.name = "Setup " + id; }
}
