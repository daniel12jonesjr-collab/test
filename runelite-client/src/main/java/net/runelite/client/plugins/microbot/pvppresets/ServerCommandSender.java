package net.runelite.client.plugins.microbot.pvppresets;

import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Schickt "::pvp ..."-Befehle an DEINEN Server.
 *
 * Standard-Weg: Text in die Chatbox legen und Enter auslösen; der Client sendet
 * "::"-Befehle dann als Command-Paket. Hat dein Client eine direkte Methode für
 * Command-Pakete, ersetze {@link #flushOne()} damit (schneller, kein Chatbox-Flackern).
 */
@Singleton
public class ServerCommandSender {
    @Inject private Client client;
    @Inject private ClientThread clientThread;

    private final Queue<String> queue = new ArrayDeque<>();

    public void send(String args) {
        synchronized (queue) { queue.add("::pvp " + args); }
        clientThread.invokeLater(this::flushOne);
    }

    private void flushOne() {
        String cmd;
        synchronized (queue) { cmd = queue.poll(); }
        if (cmd == null) return;
        client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, cmd);
        Canvas canvas = client.getCanvas();
        long now = System.currentTimeMillis();
        canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED));
        canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED));
        synchronized (queue) { if (!queue.isEmpty()) clientThread.invokeLater(this::flushOne); }
    }
}
