# PvM Switcher (Microbot-Plugin)

Hotkey-Presets für PvM: **Gear-Switch + Gebete + Aktion** (Zauber, Special Attack oder Angriff) mit einem einzigen Tastendruck. Dazu gibt es Cooldown- und Freeze-Balken im Spiel.

> **Status:** Geschrieben gegen die aktuelle Microbot-Source (`chsami/Microbot`). Die Syntax ist geprüft, aber das Plugin ist **noch nicht in Microbot kompiliert und nicht im Spiel getestet**.
> Ziele sind bewusst **nur Monster (NPCs)**.

## Funktionen

- **Seitenpanel** (Blitz-Icon) mit einer Karte pro Preset:
  - Farbige Akzentleiste je Aktion (blau = Zauber, orange = Spec, rot = Angriff)
  - Hotkey-Badge, Item-Icons der gespeicherten Ausrüstung, Gebets-Chips
  - **Set**: speichert die Ausrüstung, die du gerade trägst
  - **Bearbeiten**: Name, Hotkey (klicken und Taste drücken), Aktion, Zauber oder Spec-%, Gebete
  - Die Karte leuchtet kurz auf, wenn ihr Hotkey auslöst
- **Overlay** (oben links):
  - Zauber-Cooldown → „BEREIT“
  - Freeze-Restzeit (Snare/Entangle) auf dem Ziel, danach 3 s Immunität
  - Balken laufen flüssig, nicht in 0,6-s-Sprüngen
  - Splash-Erkennung: Bewegt sich das Ziel trotz Freeze, verschwindet der Balken
- **Zauberliste (Standard-Zauberbuch):** Snare, Entangle, alle Waves, alle Surges, God Spells
  (Tele Block fehlt absichtlich, er wirkt nur auf Spieler.)

## Ablauf eines Presets (auf Tempo optimiert)

```
Taste ─► einreihen (AWT-Thread, blockiert nie)
          │
Script-Thread (alle 15 ms)
  1. Gebete   – Menü-Aktion ohne Maus, kein Warten
  2. Gear     – alle Items direkt hintereinander, kein Warten
  3. Spec     – Spec-Orb aktivieren (nur bei genug Energie)
  4. Aktion   – Schnell-Zauber / Angriff auf das Monster
```

### Schnell-Zauber statt `Rs2Magic.castOn`

| Microbot-Standard | PvM Switcher |
|---|---|
| feste Pause 150–300 ms | keine feste Pause |
| `canCast` mit Tab-Wechseln + Suche über alle Zauber | Widget-ID einmal suchen, dann gemerkt |
| Warten auf „Zauber ausgewählt“, grobes Abfrage-Intervall | Abfrage alle 5 ms (meist 1 Frame) |
| dreht ggf. die Kamera | keine Kamera-Bewegung |

## Einbauen

1. Den Ordner `src/main/java/.../pvmhotkeys` nach
   `runelite-client/src/main/java/net/runelite/client/plugins/microbot/pvmhotkeys/` kopieren.
2. `./gradlew :client:compileJava` ausführen.
3. Client starten, **„PvM Switcher“** aktivieren und rechts aufs Blitz-Icon klicken.

## Wichtig

- In den **Microbot-Einstellungen** muss **„Disable input yielding“ AN** bleiben (Standard). Sonst wartet Microbot nach jedem echten Tastendruck 1,8 s.
- Hotkeys am besten auf **F-Tasten** legen. Sie werden abgefangen und landen nicht im Chat.
- Gear-Items müssen **im Inventar** sein. Fehlende Items meldet das Plugin im Chat.

## Dateien

| Datei | Aufgabe |
|---|---|
| `PvmHotkeysPlugin` | Start/Stopp, Tastatur, Panel, Overlay, „Set“ |
| `PvmHotkeysScript` | Führt Presets aus (Gebete → Gear → Spec → Aktion) |
| `FastCaster` | Schnelles Zaubern ohne Wartezeiten |
| `TimerState` / `PvmTimersOverlay` | Cooldown-, Freeze- und Immunitäts-Balken |
| `PresetStore` | Speichert Presets als JSON in der RuneLite-Config |
| `model/*` | Preset, Aktionen, Zauberliste mit Freeze-Dauern |
| `ui/*` | Panel, Karten, Chips, Hotkey-Button, Theme |
