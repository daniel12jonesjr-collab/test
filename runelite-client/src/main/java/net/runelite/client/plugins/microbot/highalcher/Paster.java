package net.runelite.client.plugins.microbot.highalcher;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Copy and paste for the game, which has none of its own:
 * <ul>
 *   <li>Ctrl+V in the client types the clipboard into whatever takes text right now (GE search, amount box, chat).</li>
 *   <li>{@link #typeIntoSearch} types an item name from the side panel straight into an open GE search.</li>
 * </ul>
 * Text is typed key by key, the same way a person would, and never followed by Enter.
 */
@Slf4j
@Singleton
public class Paster implements KeyListener {

    /** Longer text is cut, nothing in the game takes more. */
    private static final int MAX_CHARS = 80;
    /** MESLAYERMODE value while the GE item search is open. */
    private static final int GE_SEARCH = 14;

    private final Client client;
    private final ClientThread clientThread;
    private ExecutorService typer;

    @Inject
    Paster(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    public void start() {
        typer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "highalcher-paste");
            t.setDaemon(true);
            return t;
        });
    }

    public void stop() {
        if (typer != null) typer.shutdownNow();
        typer = null;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_V || !e.isControlDown() || e.isAltDown()) return;
        if (client.getGameState() != GameState.LOGGED_IN) return;
        String text = clipboard();
        if (text == null || text.isEmpty()) return;
        e.consume();
        type(text);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    /**
     * Types {@code name} if the GE item search is open right now. Returns false (and types nothing) otherwise,
     * so a click in the panel never ends up as text in the public chat.
     */
    public boolean typeIntoSearch(String name) {
        boolean open = clientThread.runOnClientThreadOptional(
                () -> client.getVarcIntValue(VarClientID.MESLAYERMODE) == GE_SEARCH).orElse(false);
        if (open) type(name);
        return open;
    }

    private void type(String raw) {
        ExecutorService ex = typer;
        if (ex == null) return;
        // One line only: a newline would send the chat message
        String text = raw.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
        String toType = text;
        ex.submit(() -> {
            // Ctrl from the shortcut must be up first, or the typed letters become Ctrl+letter
            Global.sleepUntil(() -> !InputArbiter.isRealButtonOrKeyDown(), 1500);
            Rs2Keyboard.typeString(toType);
            log.debug("[HighAlcher] Pasted {} characters", toType.length());
        });
    }

    private static String clipboard() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data instanceof String ? (String) data : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
