package net.runelite.client.plugins.microbot.pvppresets;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Supplier;

/**
 * UI + Hotkeys für das serverseitige PvP-Preset-System.
 * Der Client führt selbst KEINE Spielaktionen aus – er sendet nur ::pvp-Befehle an deinen Server.
 */
@Slf4j
@PluginDescriptor(name = "PvP Presets", description = "Gear-Presets mit Prayer, Follow-up-Spell und Auto-Sustain (Server-Feature)",
        tags = {"pvp", "gear", "preset", "switch"})
public class PvpPresetsPlugin extends Plugin {
    private static final String SUSTAIN_KEY = "sustain";
    private static final String OPENER_KEY = "opener";

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private KeyManager keyManager;
    @Inject private ConfigManager configManager;
    @Inject private ItemManager itemManager;
    @Inject private PvpPresetsConfig config;
    @Inject private ServerCommandSender sender;
    @Inject private Gson gson;

    private final List<ClientPreset> presets = new ArrayList<>();
    private SustainState sustain = new SustainState();
    private OpenerState opener = new OpenerState();
    private final List<HotkeyListener> listeners = new ArrayList<>();
    private PvpPresetsPanel panel;
    private NavigationButton navButton;

    @Provides
    PvpPresetsConfig provideConfig(ConfigManager cm) { return cm.getConfig(PvpPresetsConfig.class); }

    @Override
    protected void startUp() {
        load();
        panel = new PvpPresetsPanel(itemManager);
        BufferedImage icon;
        try { icon = ImageUtil.loadImageResource(getClass(), "icon.png"); }
        catch (Exception e) { icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); }
        navButton = NavigationButton.builder().tooltip("PvP Presets").icon(icon).priority(5).panel(panel).build();
        clientToolbar.addNavigation(navButton);
        rebuildUi();
        registerHotkeys();
    }

    @Override
    protected void shutDown() {
        unregisterHotkeys();
        clientToolbar.removeNavigation(navButton);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (!PvpPresetsConfig.GROUP.equals(e.getGroup()) || !e.getKey().startsWith("hk")) return;
        unregisterHotkeys();
        registerHotkeys();
        SwingUtilities.invokeLater(this::rebuildUi);
    }

    // ---------- Hotkeys ----------

    private Keybind hotkeyFor(int id) {
        switch (id) {
            case 1: return config.hk1(); case 2: return config.hk2(); case 3: return config.hk3();
            case 4: return config.hk4(); case 5: return config.hk5(); case 6: return config.hk6();
            case 7: return config.hk7(); case 8: return config.hk8(); default: return Keybind.NOT_SET;
        }
    }

    private void registerHotkeys() {
        for (int i = 1; i <= 8; i++) {
            final int id = i;
            addHotkey(() -> hotkeyFor(id), () -> applyPreset(id));
        }
        addHotkey(config::hkOpener, this::fireOpener);
        addHotkey(config::hkSustain, () -> {
            sustain.enabled = !sustain.enabled;
            sender.send("sustain " + (sustain.enabled ? "on" : "off"));
            saveSustain();
            SwingUtilities.invokeLater(() -> panel.setSustainEnabled(sustain.enabled));
        });
    }

    private void addHotkey(Supplier<Keybind> key, Runnable action) {
        HotkeyListener l = new HotkeyListener(key) {
            @Override public void hotkeyPressed() { action.run(); }
        };
        listeners.add(l);
        keyManager.registerKeyListener(l);
    }

    private void unregisterHotkeys() {
        listeners.forEach(keyManager::unregisterKeyListener);
        listeners.clear();
    }

    /** Opener auf den Spieler unter der Maus (ohne Hover: Server wählt das Ziel). */
    private void fireOpener() {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        clientThread.invoke(() -> {
            String name = hoveredPlayerName();
            sender.send(name == null ? "opener" : "opener " + name);
        });
    }

    private String hoveredPlayerName() {
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (mouse == null) return null;
        Player local = client.getLocalPlayer();
        for (Player pl : client.getPlayers()) {
            if (pl == null || pl == local || pl.getName() == null) continue;
            java.awt.Shape hull = pl.getConvexHull();
            if (hull != null && hull.contains(mouse.getX(), mouse.getY())) return pl.getName().replace('\u00A0', ' ');
        }
        return null;
    }

    private void syncOpener() {
        sender.send("openercfg spells " + opener.spellList());
        sender.send("openercfg gear " + opener.gearPreset);
        sender.send("openercfg follow " + opener.followPreset);
        configManager.setConfiguration(PvpPresetsConfig.GROUP, OPENER_KEY, gson.toJson(opener));
    }

    private final PvpPresetsPanel.OpenerActions openerActions = new PvpPresetsPanel.OpenerActions() {
        @Override public void changed() { syncOpener(); }
        @Override public void fire() { fireOpener(); }
        @Override public void armed(boolean on) { sender.send("opener arm " + (on ? "on" : "off")); }
    };

    /** Server meldet "Teleblock sitzt" -> Haken raus. */
    @Subscribe
    public void onChatMessage(ChatMessage e) {
        if (e.getType() == ChatMessageType.GAMEMESSAGE && e.getMessage().contains("Teleblock sitzt")) {
            SwingUtilities.invokeLater(() -> panel.setArmed(false));
        }
    }

    /** Beim Login ist der Auto-Opener serverseitig immer aus -> UI angleichen. */
    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOGIN_SCREEN) SwingUtilities.invokeLater(() -> panel.setArmed(false));
    }

    private void applyPreset(int id) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (presets.stream().anyMatch(p -> p.id == id)) sender.send("use " + id);
    }

    // ---------- Panel-Aktionen ----------

    private final PresetCard.Actions actions = new PresetCard.Actions() {
        @Override public void apply(ClientPreset p) { applyPreset(p.id); }

        @Override public void captureGear(ClientPreset p) {
            clientThread.invoke(() -> {
                ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
                p.gear.clear();
                if (eq != null) for (Item it : eq.getItems()) if (it.getId() > 0) p.gear.add(it.getId());
                sender.send("save " + p.id);
                // Server übernimmt aktive Prayer; lokal gewählte Prayer/Spell danach erneut setzen
                if (!"NONE".equals(p.prayer)) sender.send("set " + p.id + " pray " + p.prayer);
                sender.send("set " + p.id + " spell " + p.spell);
                savePresets();
                SwingUtilities.invokeLater(PvpPresetsPlugin.this::rebuildUi);
            });
        }

        @Override public void prayerChanged(ClientPreset p) { sender.send("set " + p.id + " pray " + p.prayer); savePresets(); }
        @Override public void spellChanged(ClientPreset p) { sender.send("set " + p.id + " spell " + p.spell); savePresets(); }
        @Override public void renamed(ClientPreset p) { savePresets(); }

        @Override public void delete(ClientPreset p) {
            int ok = JOptionPane.showConfirmDialog(panel, "\"" + p.name + "\" löschen?", "Preset löschen", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
            presets.remove(p);
            sender.send("del " + p.id);
            if (opener.gearPreset == p.id) opener.gearPreset = 0;
            if (opener.followPreset == p.id) opener.followPreset = 0;
            syncOpener();
            savePresets();
            rebuildUi();
        }
    };

    private final PvpPresetsPanel.SustainActions sustainActions = new PvpPresetsPanel.SustainActions() {
        @Override public void toggled(boolean on) { sustain.enabled = on; sender.send("sustain " + (on ? "on" : "off")); saveSustain(); }
        @Override public void eatAt(int pct) { sustain.eatAt = pct; sender.send("sustain eat " + pct); saveSustain(); }
        @Override public void prayerAt(int pts) { sustain.prayerAt = pts; sender.send("sustain prayer " + pts); saveSustain(); }
        @Override public void combo(boolean on) { sustain.combo = on; sender.send("sustain combo " + (on ? "on" : "off")); saveSustain(); }
        @Override public void buff(String skill, int min) {
            sustain.buffs.put(skill, min);
            sender.send("sustain buff " + skill + " " + (min <= 0 ? "off" : min));
            saveSustain();
        }
    };

    private void addPreset() {
        Set<Integer> used = new HashSet<>();
        presets.forEach(p -> used.add(p.id));
        for (int i = 1; i <= 8; i++) {
            if (!used.contains(i)) {
                presets.add(new ClientPreset(i));
                presets.sort(Comparator.comparingInt(p -> p.id));
                savePresets();
                rebuildUi();
                return;
            }
        }
    }

    private void rebuildUi() {
        panel.rebuild(presets, id -> {
            Keybind k = hotkeyFor(id);
            return k == Keybind.NOT_SET ? "—" : k.toString();
        }, actions, this::addPreset, sustain, sustainActions,
                opener, config.hkOpener() == Keybind.NOT_SET ? "—" : config.hkOpener().toString(), openerActions);
    }

    // ---------- Persistenz (lokal, nur Anzeige) ----------

    private void load() {
        presets.clear();
        String json = config.presets();
        if (json != null && !json.isEmpty()) {
            List<ClientPreset> l = gson.fromJson(json, new TypeToken<List<ClientPreset>>() { }.getType());
            if (l != null) presets.addAll(l);
        }
        String s = configManager.getConfiguration(PvpPresetsConfig.GROUP, SUSTAIN_KEY);
        if (s != null && !s.isEmpty()) sustain = gson.fromJson(s, SustainState.class);
        String o = configManager.getConfiguration(PvpPresetsConfig.GROUP, OPENER_KEY);
        if (o != null && !o.isEmpty()) opener = gson.fromJson(o, OpenerState.class);
    }

    private void savePresets() { configManager.setConfiguration(PvpPresetsConfig.GROUP, "presets", gson.toJson(presets)); }
    private void saveSustain() { configManager.setConfiguration(PvpPresetsConfig.GROUP, SUSTAIN_KEY, gson.toJson(sustain)); }
}
