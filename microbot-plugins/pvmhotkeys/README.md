# PvM Hotkeys (Microbot-Plugin)

Drei frei belegbare Hotkeys. Jeder löst einen eingestellten Kampfzauber auf dein **aktuelles Ziel** aus.

> **Status:** Geschrieben gegen die aktuelle Microbot-Source (`chsami/Microbot`), aber **noch nicht kompiliert und nicht im Spiel getestet**. Das machen wir zusammen am PC.

## Dateien

| Datei | Aufgabe |
|---|---|
| `PvmHotkeysPlugin.java` | Registriert die Hotkeys beim Start und entfernt sie beim Stopp. |
| `PvmHotkeysConfig.java` | Einstellungen: Hotkey und Zauber pro Slot, Cooldown, Ziel-Fallback. |
| `PvmHotkeysScript.java` | Hintergrund-Thread: prüft Cooldown, Ziel und Runen und zaubert dann. |

## Einbauen

1. Den Ordner `pvmhotkeys` nach
   `runelite-client/src/main/java/net/runelite/client/plugins/microbot/pvmhotkeys/`
   in eure Microbot-Source kopieren.
2. Kompilieren: `./gradlew :client:compileJava`
3. Client starten → in der Plugin-Liste **„PvM Hotkeys“** aktivieren.
4. In den Einstellungen Hotkeys setzen (am besten **F-Tasten**, damit nichts in den Chat getippt wird).

## Wichtig

- In den **Microbot-Einstellungen** muss **„Disable input yielding“ AN** bleiben (Standard).
  Sonst pausiert Microbot nach jedem echten Tastendruck 1,8 s alle Bot-Eingaben und der Hotkey würde erst verspätet oder gar nicht zaubern.
- Du musst im richtigen Zauberbuch sein (Ancient-Zauber gehen nur mit dem Ancient-Zauberbuch).

## So funktioniert's

```
Taste (AWT-Thread)  ──►  pending = Zauber   (nur merken, nie blockieren!)
                               │
Script-Thread (alle 30 ms) ◄───┘
   1. Cooldown: genug Ticks seit letztem Zauber?
   2. Ziel: aktuelles Kampfziel (optional: nächstes Monster)
   3. Runen vorhanden?
   4. Rs2Magic.castOn(zauber, ziel)
```

- **Nur eine wartende Anfrage:** Drückst du schnell zwei Tasten, gewinnt die letzte.
- **Cooldown in Ticks:** Ein Tick dauert 0,6 s. Standard sind 5 Ticks (normales Zauber-Tempo). Zu frühe Tastendrücke werden verworfen, statt Zauber zu verschwenden.

## Ideen für die nächste Version

- Gebet und Zauber mit einem Tastendruck (z. B. Protect from Magic + Ice Barrage)
- Gear-Switch vor dem Zaubern
- Overlay mit Cooldown-Balken und Zähler
- Eingaben, die im Cooldown kommen, für den nächsten Tick vormerken, statt sie zu verwerfen
