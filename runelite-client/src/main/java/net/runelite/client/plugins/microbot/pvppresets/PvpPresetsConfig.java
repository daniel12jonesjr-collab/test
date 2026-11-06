package net.runelite.client.plugins.microbot.pvppresets;

import net.runelite.client.config.*;

import java.awt.event.KeyEvent;

@ConfigGroup(PvpPresetsConfig.GROUP)
public interface PvpPresetsConfig extends Config {
    String GROUP = "pvppresets";

    @ConfigSection(name = "Hotkeys", description = "Ein Hotkey pro Preset", position = 0)
    String hotkeys = "hotkeys";

    @ConfigItem(keyName = "hk1", name = "Preset 1", description = "", section = hotkeys, position = 1)
    default Keybind hk1() { return new Keybind(KeyEvent.VK_F1, 0); }
    @ConfigItem(keyName = "hk2", name = "Preset 2", description = "", section = hotkeys, position = 2)
    default Keybind hk2() { return new Keybind(KeyEvent.VK_F2, 0); }
    @ConfigItem(keyName = "hk3", name = "Preset 3", description = "", section = hotkeys, position = 3)
    default Keybind hk3() { return new Keybind(KeyEvent.VK_F3, 0); }
    @ConfigItem(keyName = "hk4", name = "Preset 4", description = "", section = hotkeys, position = 4)
    default Keybind hk4() { return new Keybind(KeyEvent.VK_F4, 0); }
    @ConfigItem(keyName = "hk5", name = "Preset 5", description = "", section = hotkeys, position = 5)
    default Keybind hk5() { return Keybind.NOT_SET; }
    @ConfigItem(keyName = "hk6", name = "Preset 6", description = "", section = hotkeys, position = 6)
    default Keybind hk6() { return Keybind.NOT_SET; }
    @ConfigItem(keyName = "hk7", name = "Preset 7", description = "", section = hotkeys, position = 7)
    default Keybind hk7() { return Keybind.NOT_SET; }
    @ConfigItem(keyName = "hk8", name = "Preset 8", description = "", section = hotkeys, position = 8)
    default Keybind hk8() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "hkOpener", name = "Opener (TB + Entangle)", description = "Combo auf Spieler unter der Maus",
            section = hotkeys, position = 0)
    default Keybind hkOpener() { return new Keybind(KeyEvent.VK_Q, 0); }

    @ConfigItem(keyName = "hkSustain", name = "Auto-Sustain an/aus", description = "", section = hotkeys, position = 9)
    default Keybind hkSustain() { return new Keybind(KeyEvent.VK_F12, 0); }

    /** Intern: Preset-Metadaten als JSON (Name, Prayer, Spell, Gear-Vorschau). */
    @ConfigItem(keyName = "presets", name = "", description = "", hidden = true)
    default String presets() { return ""; }
}
