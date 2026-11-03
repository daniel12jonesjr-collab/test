package net.runelite.client.plugins.microbot.gearswitcher;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
    /** Longest wait for the client to take one click; a frame is normally 15-25 ms. */
    private static final long CLICK_TAKEN_TIMEOUT_MS = 150;

    @Inject
    private GearSwitcherConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;

    @Inject
    private SupplyKeeper supplies;

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

    private final HotkeyListener set1Listener = listener(() -> config.set1Hotkey(), () -> config.set1Items(), () -> config.set1Prayer(), () -> config.set1Spell(), "Set 1");
    private final HotkeyListener set2Listener = listener(() -> config.set2Hotkey(), () -> config.set2Items(), () -> config.set2Prayer(), () -> config.set2Spell(), "Set 2");
    private final HotkeyListener set3Listener = listener(() -> config.set3Hotkey(), () -> config.set3Items(), () -> config.set3Prayer(), () -> config.set3Spell(), "Set 3");

    @Provides
    GearSwitcherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GearSwitcherConfig.class);
    }

    @Override
    protected void startUp() {
        gearExecutor = Executors.newSingleThreadExecutor();
        supplies.start();
        offenceExecutor = Executors.newSingleThreadExecutor();
        protectionExecutor = Executors.newSingleThreadExecutor();
        requestedProtection = null;
        keyManager.registerKeyListener(set1Listener);
        keyManager.registerKeyListener(set2Listener);
        keyManager.registerKeyListener(set3Listener);

        panel = new GearSwitcherPanel(config, configManager, itemManager);
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
        supplies.stop();
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
                                    Supplier<GearSwitcherConfig.OffensivePrayer> prayer,
                                    Supplier<GearSwitcherConfig.Spell> spell, String setName) {
        return new HotkeyListener(key) {
            @Override
            public void hotkeyPressed() {
                gearExecutor.submit(() -> {
                    equipSet(setName, items.get(), prayer.get());
                    castOnTarget(spell.get());
                });
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
        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        }
        // One client-thread pass plans every click, then they go out back to back
        List<Planned> clicks = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> planWield(names))
                .orElse(List.of());
        for (Planned click : clicks) {
            quickClickAndWait(click.bounds, click.entry);
        }
        log.debug("[GearSwitcher] {} equipped: {} clicks for {}, prayer {}", setName, clicks.size(), names, prayer);
    }

    /**
     * Each click hands its menu entry to the client through Microbot.targetMenu, and the client only takes it on
     * its next frame. Sending the next click before that overwrote the entry, which is how one or two items of a
     * set got lost. So wait until the client has taken it (cleared on MenuOptionClicked), usually one frame.
     */
    private static void quickClickAndWait(Rectangle bounds, NewMenuEntry entry) {
        Microbot.getMouse().quickClick(Rs2UiHelper.getClickingPoint(bounds, true), entry);
        long deadline = System.currentTimeMillis() + CLICK_TAKEN_TIMEOUT_MS;
        while (Microbot.targetMenu != null && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** A wield click worked out ahead: the menu entry and where the item sits on screen. */
    private static final class Planned {
        final NewMenuEntry entry;
        final Rectangle bounds;

        Planned(NewMenuEntry entry, Rectangle bounds) {
            this.entry = entry;
            this.bounds = bounds;
        }
    }

    /**
     * Speed: Rs2Inventory.wield() glides the mouse to every item, and Microbot.doInvoke() asks the client thread
     * several times per item and sleeps 50-100 ms after every click. Here one client-thread pass reads the worn
     * gear and the inventory widgets for the whole set, and the caller sends the clicks with
     * {@code Mouse.quickClick()} directly, so an 8-piece switch takes well under one tick.
     * Items that are missing or already worn are skipped. Client thread only.
     */
    private static List<Planned> planWield(List<String> names) {
        Client client = Microbot.getClient();
        // A selected spell would turn "Wear" into "Cast on", so drop the selection first
        if (client.isWidgetSelected()) client.setWidgetSelected(false);

        Set<String> worn = new HashSet<>();
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment != null) {
            for (Item i : equipment.getItems()) {
                if (i.getId() > 0) worn.add(client.getItemDefinition(i.getId()).getName().toLowerCase());
            }
        }
        Widget inventory = client.getWidget(ComponentID.INVENTORY_CONTAINER);
        Widget[] slots = inventory == null ? null : inventory.getDynamicChildren();
        if (slots == null) return List.of();

        List<Planned> clicks = new ArrayList<>();
        Set<Integer> usedSlots = new HashSet<>();
        for (String name : names) {
            if (worn.contains(name.toLowerCase())) continue;
            for (Widget w : slots) {
                if (w == null || w.getItemId() <= 0 || usedSlots.contains(w.getIndex())) continue;
                if (!Text.removeTags(w.getName()).equalsIgnoreCase(name)) continue;
                // The menu identifier is the position in the inventory widget's actions, which is laid out
                // differently from the item definition's actions. Same lookup as Rs2Inventory.invokeMenu()
                String[] actions = w.getActions();
                int index = -1;
                for (int i = 0; actions != null && i < actions.length; i++) {
                    if (actions[i] != null && WIELD_ACTIONS.contains(Text.removeTags(actions[i]).toLowerCase())) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) continue;
                usedSlots.add(w.getIndex());
                clicks.add(new Planned(new NewMenuEntry()
                        .option(Text.removeTags(actions[index]))
                        .param0(w.getIndex())
                        .param1(ComponentID.INVENTORY_CONTAINER)
                        .opcode(MenuAction.CC_OP.getId())
                        .identifier(index + 1)
                        .itemId(w.getItemId())
                        .target(w.getName()),
                        w.isHidden() ? new Rectangle(1, 1) : w.getBounds()));
                break;
            }
        }
        return clicks;
    }

    // --- Spell after the switch ---

    /**
     * Selects the spell and casts it on the monster we are fighting: the NPC we target, otherwise the NPC
     * that targets us. Only ever an NPC, and nothing at all while a player is involved.
     */
    private void castOnTarget(GearSwitcherConfig.Spell spell) {
        if (spell == null || spell == GearSwitcherConfig.Spell.NONE || !Microbot.isLoggedIn()) return;
        NPC target = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (isPvpSituation()) return null;
            Player local = Microbot.getClient().getLocalPlayer();
            if (local.getInteracting() instanceof NPC) return (NPC) local.getInteracting();
            for (NPC npc : Microbot.getClient().getTopLevelWorldView().npcs()) {
                if (npc.getInteracting() == local && !npc.isDead()) return npc;
            }
            return null;
        }).orElse(null);
        if (target == null) {
            Microbot.log("Gear Switcher: no monster to cast " + spell + " on.");
            return;
        }

        MagicAction action = MagicAction.valueOf(spell.name());
        Rectangle spellBounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget w = Microbot.getClient().getWidget(action.getWidgetId());
            return w == null || w.isHidden() ? new Rectangle(1, 1) : w.getBounds();
        }).orElse(new Rectangle(1, 1));
        // Select the spell with a quick click; the spellbook does not have to be open for the menu action
        NewMenuEntry select = new NewMenuEntry()
                .option("Cast")
                .param0(-1)
                .param1(action.getWidgetId())
                .opcode(MenuAction.WIDGET_TARGET.getId())
                .identifier(1)
                .itemId(-1)
                .target(action.getName());
        quickClickAndWait(spellBounds, select);
        if (!Global.sleepUntil(() -> Microbot.getClient().isWidgetSelected(), 300)) {
            Microbot.log("Gear Switcher: could not select " + action.getName() + " (spellbook, level or runes?)");
            return;
        }
        Rs2Npc.interact(new Rs2NpcModel(target));
        log.debug("[GearSwitcher] Cast {} on {}", action.getName(), target.getName());
    }

    // --- Auto protection prayer (all handlers run on the client thread) ---

    /** Single-style monsters: switch as soon as one of them is fighting us. */
    @Subscribe
    public void onGameTick(GameTick event) {
        boolean pvp = isPvpSituation();
        // Eating and potions, PvM only like everything else here
        if (!pvp) supplies.onTick(config);
        if (!config.autoProtection() || pvp) return;
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
        if (!config.autoProtection() || isPvpSituation()) return;
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
