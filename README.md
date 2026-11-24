# Psykit – ein Tamagotchi für dein Android-Handy

Ein kleines virtuelles Haustier: **Lumi**, ein schwebendes Fantasiewesen mit herzförmigem Kopf, langen Blattohren, Bernsteinaugen, leuchtenden Punkten und einem Schwanz mit geteilter Flossenspitze.

## APK installieren

1. Unter **Releases** (rechts auf der Repo-Seite bzw. `…/releases`) die neueste `Psykit-vX.apk` herunterladen.
2. Auf dem Handy öffnen. Beim ersten Mal fragt Android, ob dein Browser/Dateimanager Apps installieren darf: erlauben.
3. Beim Start fragt die App nach **Körperliche Aktivität** (Schrittzähler) und **Benachrichtigungen**.

Neue Versionen einfach über die alte installieren, der Spielstand bleibt erhalten.

## Was die App kann (Phase 1)

| Funktion | Wirkung |
|---|---|
| Füttern | Essen aus der Leiste in den Mund ziehen. Jedes Tierchen hat 2 geheime Lieblingsessen (❤️) und 1, das es ausspuckt (👎). Mehr als 3 Süßigkeiten in 2 Stunden machen träge |
| Streicheln | Mit dem Finger über das Tierchen wischen: langsam beruhigt, zu schnell nervt. Je mehr Vertrauen, desto mehr Kuscheln am Stück. Finger gedrückt halten = kraulen; jeden Tag ist eine andere Körperstelle die geheime Lieblingsstelle (Freudensprung + Vertrauensbonus) |
| Putzen | Mit dem Schwamm Flecken einschäumen, dann mit der Dusche abspülen. Nicht eingeschäumte Stellen werden nicht sauber. Häufchen mit der Dusche wegspülen. Nach Spaziergängen (alle 1500 Schritte) gibt es neue Schmutzflecken |
| Schlafen / Wecken | Decke von unten hochziehen und Licht mit dem Regler dimmen. Optional ein Schlaflied ins Mikrofon summen, dann schläft es sofort ein. Nachts wecken macht grummelig |
| Spielen | Freie Auswahl: „Wo steckt es?“ (3 Wolken mischen sich), Psi-Memory, Seifenblasen. Gute Ergebnisse trainieren Kampfwerte |
| Bonus | Beerenfang: Handy kippen, fallende Beeren fangen, goldene Beeren geben Extra-EP |
| Schritte | 100 Schritte = 1 EP. Gezählt wird mit dem Schrittsensor des Handys |
| Mehrere Tierchen | Über 🐾 oben: Liste aller Tierchen, wechseln, neues Ei holen oder löschen. Jedes hat eigene Werte, Level, Bilder und Figuren-Paket. Schritte gehen an das gerade aktive Tierchen |
| Vertrauen | Das Tierchen merkt sich, wie du es behandelst. Vernachlässigen und Nerven senkt das Vertrauen, liebevolle Pflege erhöht es |
| Werte | Kampfwerte als Vorbereitung für das spätere Kampfsystem |

- Alle Werte sinken in **echter Zeit**, auch wenn die App zu ist (bis zu 7 Tage werden nachgerechnet).
- **Tag/Nacht**: Himmel, Sonne, Mond und Sterne passen sich der Uhrzeit an.
- Wird es vernachlässigt, sinkt die Gesundheit und es wird krank. Gute Pflege heilt es wieder (es stirbt nicht).
- Etwa stündlich prüft die App im Hintergrund, ob dein Tierchen etwas braucht, und schickt tagsüber höchstens alle 3 Stunden eine Erinnerung.

## Level und Stufen

EP gibt es fürs Kümmern, Spielen, Schritte und passiv für jede Stunde in gutem Zustand.

| Stufe | ab Level |
|---|---|
| Ei | schlüpft nach 2 Minuten |
| Baby | 1 |
| Kind | 5 |
| Jugendlich | 10 |
| Erwachsen | 20 |

## Eigene Bilder

**Figuren-Pakete:** Eine ZIP-Datei mit allen Bildern einer Figur (WebP mit Transparenz, auch animiert: `egg.webp`, `baby.webp`, `baby_happy.webp` … `adult_sleeping.webp`, optional `pack.json` mit `name` und `petName`). Import unter Zahnrad → Figuren-Pakete, mehrere Pakete möglich und jederzeit wechselbar. Genaue Anleitung und eine Vorlage für ChatGPT stehen in der App unter „❓ Anleitung“.

In der App: **Zahnrad → Eigene Bilder** → Stufe wählen → Bilder setzen. Pro Stufe gibt es „Normal“ und die Laune-Varianten **Schläft, Glücklich, Traurig, Hungrig, Krank**. Die App wechselt automatisch zum passenden Bild; fehlt eine Variante, nimmt sie „Normal“. Am besten PNG mit transparentem Hintergrund. **Animierte GIF- oder WebP-Dateien** werden ebenfalls abgespielt (z. B. blinzeln oder Schwanz bewegen). Standbilder bewegen sich automatisch: Schweben, Atmen, Neigen, Hüpfer beim Streicheln, je nach Laune lebhafter oder ruhiger.
Mehrere Bilder auf einmal: **Bildpaket importieren** und alle markieren; die Zuordnung läuft über den Dateinamen (z. B. `1-ei.png`, `2-baby.png`, `baby_traurig.png`, `erwachsen_schlaeft.png`).
Alternativ fest einbauen: PNGs unter `app/src/main/assets/sprites/`, z. B. `baby.png`, `baby_sleeping.png`, `adult_sad.png` (Endungen: `_sleeping`, `_happy`, `_sad`, `_hungry`, `_sick`).

## Aufbau (für spätere Erweiterungen)

- `core/` – reine Spiellogik ohne Android, mit Tests: Bedürfnisse, Zeitverlauf, Level, Schritte (`PetEngine`, `LevelSystem`) und das vorbereitete Kampfsystem (`battle/Battle.kt`: `Combatant`, `BattleStats`, `Move`, `BattleEngine`).
- `app/` – Android-App mit Jetpack Compose: Oberfläche, Speicherung, Schrittsensor, Hintergrund-Erinnerungen.
- Jeder Push auf `main` baut per GitHub Actions automatisch eine neue APK und veröffentlicht sie als Release.

Die APK ist mit einem festen Schlüssel (`app/psykit.keystore`) signiert, damit Updates sich installieren lassen. Das ist nur für die private Nutzung gedacht.
