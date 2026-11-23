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
| Füttern | Mahlzeit macht satt, Snack macht glücklich (mehr als 3 Snacks pro Stunde geben Bauchweh) |
| Streicheln | Button oder direkt aufs Tierchen tippen: Laune steigt, Herzchen |
| Putzen | Entfernt Häufchen, Hygiene auf 100 |
| Schlafen / Wecken | Nachts (21–7 Uhr) wird es schneller müde. Morgens wacht es von selbst auf. Nachts wecken macht grummelig |
| Spielen | Minispiel „Wohin schaut es?“, 5 Runden. Treffer bringen EP und Laune |
| Schritte | 100 Schritte = 1 EP, je 500 Schritte +5 Laune. Gezählt wird mit dem Schrittsensor des Handys |
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

In der App: **Zahnrad → Eigene Bilder** → Stufe wählen → Bilder setzen. Pro Stufe gibt es „Normal“ und die Laune-Varianten **Schläft, Glücklich, Traurig, Hungrig, Krank**. Die App wechselt automatisch zum passenden Bild; fehlt eine Variante, nimmt sie „Normal“. Am besten PNG mit transparentem Hintergrund.
Alternativ fest einbauen: PNGs unter `app/src/main/assets/sprites/`, z. B. `baby.png`, `baby_sleeping.png`, `adult_sad.png` (Endungen: `_sleeping`, `_happy`, `_sad`, `_hungry`, `_sick`).

## Aufbau (für spätere Erweiterungen)

- `core/` – reine Spiellogik ohne Android, mit Tests: Bedürfnisse, Zeitverlauf, Level, Schritte (`PetEngine`, `LevelSystem`) und das vorbereitete Kampfsystem (`battle/Battle.kt`: `Combatant`, `BattleStats`, `Move`, `BattleEngine`).
- `app/` – Android-App mit Jetpack Compose: Oberfläche, Speicherung, Schrittsensor, Hintergrund-Erinnerungen.
- Jeder Push auf `main` baut per GitHub Actions automatisch eine neue APK und veröffentlicht sie als Release.

Die APK ist mit einem festen Schlüssel (`app/psykit.keystore`) signiert, damit Updates sich installieren lassen. Das ist nur für die private Nutzung gedacht.
