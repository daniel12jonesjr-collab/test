# Quest Prep (Erweiterung für den Microbot Quest Helper)

Behebt die typischen Probleme des automatischen Quest Helpers:

| Problem im Original | Ursache im Code | Quest Prep |
|---|---|---|
| Kauft willkürlich Sachen | `collectAllItemRequirements` nimmt **alle** Items der ganzen Quest und kauft sie mitten in der Quest, Schritt für Schritt | Einkauf **einmal vor Questbeginn**, nur nach **deiner Bestätigung** |
| Rennt um die halbe Welt | `Rs2Walker.walkTo` nutzt nur Teleports, die **schon im Inventar** sind | Bei weiten Wegen: `walkWithBankedTransports` vergleicht mit „erst Bank, Teleport holen“ |
| Keine Staminas | Microbot trinkt nur unter einer Schwelle und nur beim Laufen, aus dem Inventar | Staminas werden ins Loadout gepackt, vor **jedem Schritt** geprüft |
| Kein Food / keine Ausrüstung | wird nicht vorbereitet | Food-Menge + Quest-Gear (per **Set**-Button) im Loadout |

> **Status:** Geschrieben gegen die aktuelle Microbot-Source, Syntax geprüft, **noch nicht kompiliert und nicht im Spiel getestet**.

## Ablauf

```
Quest im Quest Helper wählen + starten
  │
  ├─ 1. Bank prüfen      Was ist schon da? (Inventar, Ausrüstung, Bank)
  ├─ 2. Teleports        Quest-Orte sammeln → passende Teleports finden
  ├─ 3. ABFRAGE          Dialog: Teleports + Einkaufsliste mit Preisen, alles an/abwählbar
  │                      [Abbrechen]  [Ohne Kauf starten]  [Kaufen & Starten]
  ├─ 4. Einkauf (GE)     nur die freigegebenen Items, mit Preisaufschlag + Limits
  ├─ 5. Loadout          Inventar einlagern → Quest-Items, Food, Staminas, Teleports abheben
  ├─ 6. Gear anlegen     Quest-Gear aus dem Set-Button
  └─ 7. Quest läuft      vor JEDEM Schritt: essen? Stamina? Run an?
                         weite Wege: Teleport aus der Bank lohnt sich?
```

## Teleport-Vorschläge

1. Alle Orte der Quest-Schritte werden aus dem Quest Helper gesammelt.
2. Alle Teleport-Items und -Zauber aus Microbots Wegfinder-Daten werden geladen.
3. Es wird immer der Teleport gewählt, der **die meisten noch nicht abgedeckten Quest-Orte** (Umkreis 30 Felder) erreicht.
4. Pro Teleport werden die benötigten Items bestimmt. Bevorzugt werden die, die du schon besitzt, sonst die billigsten.
5. Im Dialog siehst du pro Teleport, wie viele Orte er abdeckt, was er braucht und ob du es schon hast oder was der Kauf kostet.

## Einbauen

1. Den Ordner `src/main/java/.../questhelper/prep` nach
   `runelite-client/src/main/java/net/runelite/client/plugins/microbot/questhelper/prep/` kopieren.
2. Den Patch für `QuestScript.java` einspielen (im Microbot-Hauptordner):
   ```
   git apply pfad/zu/QuestScript.patch
   ```
3. `./gradlew :client:compileJava`
4. Im Client **„Quest Prep“** aktivieren (Einstellungen + Panel) und den Quest Helper wie gewohnt starten.

## Einstellungen (Auszug)

| Bereich | Einstellung | Standard |
|---|---|---|
| Einkauf | Vor dem Kauf fragen | an |
| | Preisaufschlag | 15 % |
| | Max. Preis pro Item / Gesamt | 500k / 2M gp |
| Loadout | Food / Menge | Shark / 6 |
| | Stamina / Menge | Stamina potion(4) / 2 |
| | Weitere Items | `Varrock teleport:3, Falador teleport:3, Camelot teleport:3` |
| | Teleports vorschlagen / Anzahl | an / 4 |
| Unterwegs | Essen ab HP | 50 % |
| | Stamina unter Energie | 30 % |
| | Teleports aus der Bank, weiter Weg ab | an, 120 Felder |

## Dateien

| Datei | Aufgabe |
|---|---|
| `QuestPrep` | Zustandsmaschine: Bank → Abfrage → Einkauf → Loadout → Gear → Bereit |
| `PrepPlanner` | Berechnet Bedarf (Quest, empfohlen, Loadout, Gear) und Preise |
| `TeleportAdvisor` | Findet Teleports nahe den Quest-Orten |
| `PrepDialog` | Bestätigungsdialog mit Live-Summe |
| `StepGuard` | Vor jedem Schritt: essen, Stamina, Run |
| `QuestTravel` | Laufen mit Teleport-Prüfung bei weiten Wegen |
| `QuestPrepPlugin` / `QuestPrepPanel` | Einstellungen, Status-Panel, Gear-Set-Button |
| `QuestScript.patch` | Bindet alles in den Quest Helper ein |
