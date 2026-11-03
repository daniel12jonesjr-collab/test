package net.runelite.client.plugins.microbot.gearswitcher;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * PvM helper:
 * <ul>
 *   <li>Hotkeys equip a configured gear set and turn on that set's offensive prayer.</li>
 *   <li>Optional auto protection prayer, driven by two tables in the config: monster name -> style
 *   for single-style monsters, and animation/projectile id -> style for multi-style ones.</li>
 *   <li>A side panel to set everything up: take the worn gear into a set with one click, record hotkeys.</li>
 * </ul>
 * Everything is refused while a player is involved (we target one, or one targets us).
 * The Wilderness and PvP worlds themselves are allowed, for Wilderness bosses and Revenants.
 */
@PluginDescriptor(
        name = PluginDescriptor.Default + "Gear Switcher",
        description = "Gear sets with offensive prayer on a hotkey, optional auto protection prayer (PvM only)",
        tags = {"gear", "switch", "hotkey", "prayer", "pvm", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GearSwitcherPlugin extends Plugin {

    static final String CONFIG_GROUP = "gearswitcher";
    private static final Set<String> WIELD_ACTIONS = Set.of("wield", "wear", "equip");

    @Inject
    private GearSwitcherConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private ClientToolbar clientToolbar;

    private GearSwitcherPanel panel;
    private NavigationButton navButton;

    /**
     * Key events arrive on the AWT thread and game events on the client thread. Equipping and prayer
     * toggling wait for the game, so they run on these instead. One queue each, so a protection prayer
     * never waits behind a gear switch, and an offensive prayer that cannot turn on (level, points)
     * never holds up a protection prayer.
     */
    private ExecutorService gearExecutor;
    private ExecutorService offenceExecutor;
    private ExecutorService protectionExecutor;
    /** The protection prayer we asked for last, so we don't queue the same switch every tick. */
    private Rs2PrayerEnum requestedProtection;

    private final HotkeyListener set1Listener = listener(() -> config.set1Hotkey(), () -> config.set1Items(), () -> config.set1Prayer(), "Set 1");
    private final HotkeyListener set2Listener = listener(() -> config.set2Hotkey(), () -> config.set2Items(), () -> config.set2Prayer(), "Set 2");
    private final HotkeyListener set3Listener = listener(() -> config.set3Hotkey(), () -> config.set3Items(), () -> config.set3Prayer(), "Set 3");

    @Provides
    GearSwitcherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GearSwitcherConfig.class);
    }

    @Override
    protected void startUp() {
        gearExecutor = Executors.newSingleThreadExecutor();
        offenceExecutor = Executors.newSingleThreadExecutor();
        protectionExecutor = Executors.newSingleThreadExecutor();
        requestedProtection = null;
        keyManager.registerKeyListener(set1Listener);
        keyManager.registerKeyListener(set2Listener);
        keyManager.registerKeyListener(set3Listener);

        panel = new GearSwitcherPanel(config, configManager);
        navButton = NavigationButton.builder()
                .tooltip("Gear Switcher")
                .icon(GearSwitcherPanel.icon())
                .priority(8)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        keyManager.unregisterKeyListener(set1Listener);
        keyManager.unregisterKeyListener(set2Listener);
        keyManager.unregisterKeyListener(set3Listener);
        gearExecutor.shutdownNow();
        offenceExecutor.shutdownNow();
        protectionExecutor.shutdownNow();
        clientToolbar.removeNavigation(navButton);
        panel = null;
    }

    /** Keeps the panel in sync when a value changes in the regular config panel. */
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup()) || panel == null) return;
        SwingUtilities.invokeLater(panel::refresh);
    }

    // --- Gear sets ---

    private HotkeyListener listener(Supplier<Keybind> key, Supplier<String> items,
                                    Supplier<GearSwitcherConfig.OffensivePrayer> prayer, String setName) {
        return new HotkeyListener(key) {
            @Override
            public void hotkeyPressed() {
                gearExecutor.submit(() -> equipSet(setName, items.get(), prayer.get()));
            }
        };
    }

    private void equipSet(String setName, String itemList, GearSwitcherConfig.OffensivePrayer prayer) {
        if (!Microbot.isLoggedIn()) return;
        boolean pvp = Microbot.getClientThread().runOnClientThreadOptional(this::isPvpSituation).orElse(true);
        if (pvp) {
            Microbot.log("Gear Switcher is PvM only: switching is off while a player is involved.");
            return;
        }

        // The prayer goes out first on its own queue, so it lands while the gear clicks are still running
        if (prayer != GearSwitcherConfig.OffensivePrayer.NONE) {
            // Turning one offensive prayer on turns the conflicting ones off, like clicking it by hand
            offenceExecutor.submit(() -> Rs2Prayer.toggle(Rs2PrayerEnum.valueOf(prayer.name()), true));
        }

        List<String> names = Arrays.stream(itemList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        for (String name : names) {
            quickWield(name);
        }
        log.debug("[GearSwitcher] {} equipped: {}, prayer {}", setName, names, prayer);
    }

    /**
     * Rs2Inventory.wield() glides the mouse to every item first, which costs a few hundred ms per piece.
     * This sends the same Wield/Wear menu action with a quick click, so a whole set fits in about one tick.
     * Items that are missing or already worn are skipped.
     */
    private static void quickWield(String name) {
        Rs2ItemModel item = Rs2Inventory.get(name, true);
        if (item == null || Rs2Equipment.isWearing(name, true)) return;

        // The menu identifier is the position in the inventory widget's actions, which is laid out differently
        // from the item definition's actions. Same lookup as Rs2Inventory.invokeMenu()
        String[] actions = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget inventory = Microbot.getClient().getWidget(ComponentID.INVENTORY_CONTAINER);
            Widget[] children = inventory == null ? null : inventory.getChildren();
            if (children == null) return null;
            return Arrays.stream(children)
                    .filter(w -> w != null && w.getIndex() == item.getSlot())
                    .findFirst().map(Widget::getActions).orElse(null);
        }).orElse(item.getInventoryActions());
        int index = -1;
        for (int i = 0; actions != null && i < actions.length; i++) {
            if (actions[i] != null && WIELD_ACTIONS.contains(Text.removeTags(actions[i]).toLowerCase())) {
                index = i;
                break;
            }
        }
        if (index < 0) return;

        Rectangle bounds = Rs2Inventory.itemBounds(item);
        Microbot.doInvokeQuick(new NewMenuEntry()
                        .option(Text.removeTags(actions[index]))
                        .param0(item.getSlot())
                        .param1(ComponentID.INVENTORY_CONTAINER)
                        .opcode(MenuAction.CC_OP.getId())
                        .identifier(index + 1)
                        .itemId(item.getId())
                        .target(item.getName()),
                bounds == null ? new Rectangle(1, 1) : bounds);
    }

    // --- Auto protection prayer (all handlers run on the client thread) ---

    /** Single-style monsters: switch as soon as one of them is fighting us. */
    @Subscribe
    public void onGameTick(GameTick event) {
        if (isPvpSituation()) return;
        Map<String, Rs2PrayerEnum> byName = parseTable(config.npcPrayers());
        if (byName.isEmpty()) return;

        Player local = Microbot.getClient().getLocalPlayer();
        for (NPC npc : Microbot.getClient().getTopLevelWorldView().npcs()) {
            if (npc.getName() == null) continue;
            boolean fighting = npc.getInteracting() == local || local.getInteracting() == npc;
            Rs2PrayerEnum prayer = byName.get(npc.getName().toLowerCase());
            if (fighting && prayer != null) {
                requestProtection(prayer);
                return;
            }
        }
    }

    /** Multi-style monsters, melee and instant attacks: the attack animation of a monster targeting us. */
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC) || actor.getInteracting() != Microbot.getClient().getLocalPlayer()) return;
        handleId(actor.getAnimation());
    }

    /** Multi-style monsters, ranged and magic attacks: a projectile flying at us. */
    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        Projectile projectile = event.getProjectile();
        // Only monster projectiles: a player's spell must never trigger a switch, even if its id is in the table
        if (!(projectile.getSourceActor() instanceof NPC)) return;
        if (projectile.getTargetActor() != Microbot.getClient().getLocalPlayer()) return;
        handleId(projectile.getId());
    }

    private void handleId(int id) {
        if (isPvpSituation()) return;
        Rs2PrayerEnum prayer = parseTable(config.idPrayers()).get(String.valueOf(id));
        if (prayer != null) {
            requestProtection(prayer);
        }
    }

    private void requestProtection(Rs2PrayerEnum prayer) {
        if (prayer == requestedProtection && Rs2Prayer.isPrayerActive(prayer)) return;
        requestedProtection = prayer;
        protectionExecutor.submit(() -> Rs2Prayer.toggle(prayer, true));
    }

    /**
     * True when another player is involved, so switching would help in PvP. Being in the Wilderness or on a
     * PvP world alone is fine (Wilderness bosses, Revenants), but the moment we target a player or a player targets us, it stops.
     * Client thread only.
     */
    private boolean isPvpSituation() {
        Player local = Microbot.getClient().getLocalPlayer();
        if (local == null) return true;
        if (local.getInteracting() instanceof Player) return true;
        for (Player other : Microbot.getClient().getTopLevelWorldView().players()) {
            if (other != local && other.getInteracting() == local) return true;
        }
        return false;
    }

    /** Parses "key=STYLE, key=STYLE" into lower-case key -> protection prayer. Bad entries are skipped. */
    private static Map<String, Rs2PrayerEnum> parseTable(String table) {
        Map<String, Rs2PrayerEnum> result = new HashMap<>();
        for (String entry : table.split(",")) {
            String[] parts = entry.split("=");
            if (parts.length != 2) continue;
            Rs2PrayerEnum prayer = protectionFor(parts[1].trim());
            if (prayer != null) {
                result.put(parts[0].trim().toLowerCase(), prayer);
            }
        }
        return result;
    }

    private static Rs2PrayerEnum protectionFor(String style) {
        switch (style.toUpperCase()) {
            case "MELEE":
                return Rs2PrayerEnum.PROTECT_MELEE;
            case "RANGE":
            case "RANGED":
                return Rs2PrayerEnum.PROTECT_RANGE;
            case "MAGIC":
            case "MAGE":
                return Rs2PrayerEnum.PROTECT_MAGIC;
            default:
                return null;
        }
    }

}
