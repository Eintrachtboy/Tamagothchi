#!/usr/bin/env python3
"""
Psykit-Paketprüfung: prüft ein Figuren-Paket (ZIP oder Ordner) mit denselben Regeln wie die App (ab v29),
bevor du es aufs Handy überträgst.

Benutzung:
    python psykit_pruefen.py MeinPaket.zip
    python psykit_pruefen.py MeinOrdner
Ohne Angabe fragt das Skript nach dem Pfad (unter Windows kannst du die ZIP auch auf das Skript ziehen).

Braucht nur Python 3.8 oder neuer, keine Zusatzpakete.
Rückgabewert: 0 = alles wird übernommen, 1 = mindestens eine Datei wird abgelehnt.
"""
import json
import os
import re
import struct
import sys
import zipfile

# ---------- Regeln der App (app/src/main/java/app/psykit/sprites) ----------
MAX_IMAGE_BYTES = 40 * 1024 * 1024        # SpriteStore.MAX_IMAGE_BYTES
MAX_ZIP_BYTES = 150 * 1024 * 1024         # CharacterPacks.MAX_ZIP_BYTES (entpackt)
STILL_MAX_PX = 768                        # Standbilder werden auf 768 px verkleinert
IMAGE_EXT = {"png", "webp", "gif", "jpg", "jpeg"}
IGNORED_EXT = {"txt", "md"}

# Empfehlungen (keine harten Grenzen in der App)
ANIM_GOOD_MIN, ANIM_GOOD_MAX, ANIM_TOO_BIG = 512, 768, 1024
FPS_WARN = 30
ANIM_MB_HINT = 15

STAGES = [  # Reihenfolge wie SpriteNames.stageWords
    ("egg", "Ei", ["1", "ei", "egg"]),
    ("baby", "Baby", ["2", "baby"]),
    ("child", "Kind", ["3", "kind", "child"]),
    ("teen", "Jugendlich", ["4", "jugendlich", "jugend", "teen"]),
    ("adult", "Erwachsen", ["5", "erwachsen", "adult"]),
]
VARIANTS = [  # Reihenfolge wie SpriteNames.variantWords
    ("_sleeping", "Schläft", ["schlaeft", "schlafen", "schlaf", "schlafend", "sleeping", "sleep", "asleep"]),
    ("_happy", "Glücklich", ["gluecklich", "froh", "happy", "freude"]),
    ("_sad", "Traurig", ["traurig", "sad"]),
    ("_hungry", "Hungrig", ["hungrig", "hunger", "hungry"]),
    ("_sick", "Krank", ["krank", "sick"]),
]


def guess(file_name):
    """Genau wie SpriteNames.guess: gibt (Schlüssel, Beschreibung) oder None zurück."""
    norm = file_name.lower()
    if "." in norm:
        norm = norm[: norm.rfind(".")]
    norm = norm.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    tokens = {t for t in re.split(r"[^a-z0-9]+", norm) if t}
    stage = None
    for key, label, words in STAGES:  # erst Wörter …
        if any(not w[0].isdigit() and w in tokens for w in words):
            stage = (key, label)
            break
    if stage is None:
        for key, label, words in STAGES:  # … dann Ziffern
            if any(w[0].isdigit() and w in tokens for w in words):
                stage = (key, label)
                break
    if stage is None:
        return None
    variant = ("", "Normal")
    for suffix, label, words in VARIANTS:
        if any(w in tokens for w in words):
            variant = (suffix, label)
            break
    return stage[0] + variant[0], f"{stage[1]} · {variant[1]}"


# ---------- Bildformate lesen ----------
class Info:
    def __init__(self, fmt, w=0, h=0, frames=1, duration_ms=0, alpha=None, note=None):
        self.fmt, self.w, self.h, self.frames, self.duration_ms, self.alpha, self.note = fmt, w, h, frames, duration_ms, alpha, note

    @property
    def animated(self):
        return self.frames > 1

    @property
    def fps(self):
        return self.frames * 1000.0 / self.duration_ms if self.duration_ms > 0 else 0.0


def u24(b, o):
    return b[o] | (b[o + 1] << 8) | (b[o + 2] << 16)


def parse_webp(b):
    if len(b) < 16 or b[0:4] != b"RIFF" or b[8:12] != b"WEBP":
        return None
    w = h = 0
    frames = 0
    duration = 0
    alpha = False
    o = 12
    while o + 8 <= len(b):
        cid = b[o:o + 4]
        size = struct.unpack_from("<I", b, o + 4)[0]
        p = o + 8
        if cid == b"VP8X" and size >= 10:
            alpha = bool(b[p] & 0x10)
            w, h = u24(b, p + 4) + 1, u24(b, p + 7) + 1
        elif cid == b"ANMF" and size >= 16:
            frames += 1
            duration += u24(b, p + 12)
        elif cid == b"VP8 " and size >= 10 and w == 0:
            w = struct.unpack_from("<H", b, p + 6)[0] & 0x3FFF
            h = struct.unpack_from("<H", b, p + 8)[0] & 0x3FFF
        elif cid == b"VP8L" and size >= 5 and w == 0:
            bits = struct.unpack_from("<I", b, p + 1)[0]
            w, h = (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
            alpha = bool((bits >> 28) & 1)
        elif cid == b"ALPH":
            alpha = True
        o = p + size + (size & 1)
    return Info("WebP", w, h, max(frames, 1), duration, alpha)


def parse_gif(b):
    if len(b) < 13 or b[0:3] != b"GIF":
        return None
    w, h = struct.unpack_from("<HH", b, 6)
    flags = b[10]
    o = 13 + (3 * (2 ** ((flags & 7) + 1)) if flags & 0x80 else 0)
    frames, duration, zero_delay, alpha = 0, 0, 0, False
    delay = 0

    def skip_sub(o):
        while o < len(b):
            n = b[o]
            o += 1
            if n == 0:
                return o
            o += n
        return o

    while o < len(b):
        t = b[o]
        if t == 0x21 and o + 1 < len(b):
            label = b[o + 1]
            if label == 0xF9 and o + 7 < len(b):
                alpha = alpha or bool(b[o + 3] & 1)
                delay = struct.unpack_from("<H", b, o + 4)[0] * 10
            o = skip_sub(o + 2)
        elif t == 0x2C and o + 10 <= len(b):
            f = b[o + 9]
            o += 10 + (3 * (2 ** ((f & 7) + 1)) if f & 0x80 else 0)
            o = skip_sub(o + 1)  # LZW-Mindestcode + Daten
            frames += 1
            if delay <= 10:  # Android spielt 0/10 ms als 100 ms ab
                zero_delay += 1
                delay = 100
            duration += delay
            delay = 0
        elif t == 0x3B:
            break
        else:
            break
    note = f"{zero_delay} Frames ohne Verzögerung, Android zeigt sie je 100 ms" if zero_delay and frames > 1 else None
    return Info("GIF", w, h, max(frames, 1), duration, alpha, note)


def parse_png(b):
    if len(b) < 24 or b[0:8] != b"\x89PNG\r\n\x1a\n":
        return None
    w, h = struct.unpack_from(">II", b, 16)
    color = b[25] if len(b) > 25 else 0
    alpha = color in (4, 6)
    o, apng = 8, 0
    while o + 8 <= len(b):
        size = struct.unpack_from(">I", b, o)[0]
        cid = b[o + 4:o + 8]
        if cid == b"tRNS":
            alpha = True
        if cid == b"acTL" and size >= 8:
            apng = struct.unpack_from(">I", b, o + 8)[0]
        if cid == b"IDAT" or cid == b"IEND":
            break
        o += 12 + size
    info = Info("PNG", w, h, 1, 0, alpha)
    if apng > 1:
        info.note = f"animiertes PNG ({apng} Frames): die App übernimmt nur das erste Bild, bitte als WebP speichern"
    return info


def parse_jpeg(b):
    if len(b) < 4 or b[0:2] != b"\xff\xd8":
        return None
    o = 2
    while o + 9 < len(b):
        if b[o] != 0xFF:
            o += 1
            continue
        m = b[o + 1]
        if m == 0xFF:
            o += 1
            continue
        if m in (0xD8, 0x01) or 0xD0 <= m <= 0xD7:
            o += 2
            continue
        seg = struct.unpack_from(">H", b, o + 2)[0]
        if 0xC0 <= m <= 0xCF and m not in (0xC4, 0xC8, 0xCC):
            h, w = struct.unpack_from(">HH", b, o + 5)
            return Info("JPEG", w, h, 1, 0, False, "JPEG hat keinen transparenten Hintergrund")
        o += 2 + seg
    return Info("JPEG", note="JPEG hat keinen transparenten Hintergrund")


def parse_image(b):
    for p in (parse_webp, parse_gif, parse_png, parse_jpeg):
        try:
            r = p(b)
        except (struct.error, IndexError):
            r = None
        if r is not None:
            return r
    return None


def app_sees_animated(b):
    """Genau wie ImageInfo.isAnimatedContainer in der App (ab v29): GIF, oder WebP mit Animations-Flag."""
    if len(b) > 6 and b[0:3] == b"GIF":
        return True
    if not (len(b) >= 16 and b[0:4] == b"RIFF" and b[8:12] == b"WEBP"):
        return False
    o = 12
    while o + 8 <= len(b):
        cid = b[o:o + 4]
        size = struct.unpack_from("<I", b, o + 4)[0]
        if cid == b"VP8X" and size >= 10:
            return bool(b[o + 8] & 0x02)
        if cid in (b"ANIM", b"ANMF"):
            return True
        o += 8 + size + (size & 1)
    return False


# ---------- Prüfung ----------
def mb(n):
    if n < 1024 * 1024:
        return f"{max(1, round(n / 1024))} KB"
    return f"{n / 1024 / 1024:.1f} MB".replace(".", ",")


def num(x, digits=1):
    return f"{x:.{digits}f}".replace(".", ",")


def entries_of(path):
    """Liefert (Pfad im Paket, Größe, Lesefunktion) in der Reihenfolge, wie die App sie liest."""
    if os.path.isdir(path):
        for root, dirs, files in os.walk(path):
            dirs.sort()
            for f in sorted(files):
                full = os.path.join(root, f)
                rel = os.path.relpath(full, path).replace("\\", "/")
                yield rel, os.path.getsize(full), (lambda full=full: open(full, "rb").read())
    else:
        z = zipfile.ZipFile(path)
        for i in z.infolist():
            if i.is_dir():
                continue
            yield i.filename, i.file_size, (lambda i=i: z.read(i))


def check(path):
    errors, warnings = 0, 0
    total = 0
    seen = {}
    taken = {}
    manifest = None
    lines = []

    def out(sym, name, text):
        lines.append(f"  {sym} {name}: {text}")

    for name, size, read in entries_of(path):
        base = re.split(r"[/\\]", name)[-1]
        if "__MACOSX" in name or base.startswith("."):
            continue
        ext = base.rsplit(".", 1)[-1].lower() if "." in base else ""
        is_image = ext in IMAGE_EXT
        target = guess(base) if is_image else None
        if is_image and target is None:
            out("✗", base, "Dateiname unbekannt, wird abgelehnt (z. B. adult.webp oder baby_happy.webp)")
            errors += 1
            continue
        total += size
        if size > MAX_IMAGE_BYTES:
            out("✗", base, f"zu groß: {mb(size)}, erlaubt sind {mb(MAX_IMAGE_BYTES)} – wird abgelehnt")
            errors += 1
            continue
        if total > MAX_ZIP_BYTES:
            out("✗", base, f"ab hier liest die App nicht weiter: Paket entpackt größer als {mb(MAX_ZIP_BYTES)}")
            errors += 1
            break
        if base.lower() == "pack.json":
            try:
                txt = read().decode("utf-8-sig").strip()
                manifest = json.loads(txt)
                if not isinstance(manifest, dict):
                    raise ValueError
                out("✓", base, "gelesen")
            except Exception:
                out("!", base, "nicht lesbar, die App ignoriert sie")
                warnings += 1
            continue
        if not is_image:
            if ext not in IGNORED_EXT:
                out("·", base, "kein Bild, wird ignoriert")
            continue

        key, label = target
        b = read()
        info = parse_image(b)
        if info is None:
            out("✗", base, f"kein lesbares PNG/WebP/GIF/JPEG ({mb(size)}) – wird abgelehnt")
            errors += 1
            continue
        if key in taken:
            out("!", base, f"gleiche Rolle wie {taken[key]} ({label}), die spätere Datei gewinnt")
            warnings += 1
        taken[key] = base

        notes = []
        anim_in_app = app_sees_animated(b) and info.animated
        if info.animated and not app_sees_animated(b):
            notes.append(("!", "animiert, aber das Animations-Flag im Dateikopf fehlt: die App übernimmt nur ein Standbild"))
        if anim_in_app:
            desc = f"{info.fmt} animiert, {info.w}×{info.h}, {info.frames} Frames, {num(info.fps)} fps, {num(info.duration_ms / 1000)} s, {mb(size)}"
            m = max(info.w, info.h)
            if m > ANIM_TOO_BIG:
                notes.append(("!", f"{m} px ist größer als nötig ({ANIM_GOOD_MIN}–{ANIM_GOOD_MAX} px reichen), kostet nur Speicher"))
            elif m < ANIM_GOOD_MIN:
                notes.append(("·", f"{m} px wirkt auf großen Handys etwas weich, {ANIM_GOOD_MIN}–{ANIM_GOOD_MAX} px ist ideal"))
            if info.fps > FPS_WARN:
                notes.append(("!", f"{info.fps:.0f} fps: mehr als {FPS_WARN} fps sieht man kaum, die Datei wird nur größer"))
            if size > ANIM_MB_HINT * 1024 * 1024:
                notes.append(("!", f"über {ANIM_MB_HINT} MB: auf schwächeren Handys kann der Import knapp werden"))
            if info.duration_ms == 0:
                notes.append(("!", "Frames haben keine Dauer"))
            notes.append(("·", "auf Android 8 nur als Standbild"))
        else:
            desc = f"{info.fmt}, {info.w}×{info.h}, {mb(size)}" if info.w else f"{info.fmt}, {mb(size)}"
            if max(info.w, info.h) > STILL_MAX_PX:
                notes.append(("·", f"wird auf {STILL_MAX_PX} px verkleinert"))
        if info.w and info.h and abs(info.w - info.h) > max(info.w, info.h) * 0.05:
            notes.append(("·", "nicht quadratisch, die Figur wird eingepasst"))
        if info.alpha is False and info.fmt != "JPEG":
            notes.append(("·", "kein transparenter Hintergrund"))
        if info.note:
            notes.append(("!" if "nur das erste" in info.note else "·", info.note))

        out("✓", base, f"{label} – {desc}")
        for sym, n in notes:
            lines.append(f"      {sym} {n}")
            if sym == "!":
                warnings += 1
        seen[key] = base

    # Übersicht über die Stufen
    lines.append("")
    lines.append("Stufen:")
    for key, label, _ in STAGES:
        moods = [v[1] for v in VARIANTS if key + v[0] in seen]
        if key in seen:
            extra = f" + {', '.join(moods)}" if moods else ""
            lines.append(f"  ✓ {label}: Normal{extra}")
        elif moods:
            lines.append(f"  ! {label}: kein Normal-Bild (nur {', '.join(moods)}), sonst wird das Bild einer Nachbarstufe gezeigt")
            warnings += 1
        else:
            lines.append(f"  · {label}: kein Bild, die App zeigt das Bild der nächsten Stufe" + (" bzw. das eingebaute Ei" if key == "egg" else ""))
    if manifest:
        name = manifest.get("name") or "(Dateiname)"
        float_txt = {True: "schwebt", False: "steht auf dem Boden"}.get(manifest.get("float"), "Standard")
        lines.append(f"\nPaket: {name}" + (f" · Name: {manifest.get('petName')}" if manifest.get("petName") else "") + f" · {float_txt}")
    lines.append(f"Gesamt entpackt: {mb(total)} von {mb(MAX_ZIP_BYTES)}")
    if not seen:
        lines.append("✗ Kein einziges Bild wird übernommen – die App bricht den Import ab, das bisherige Paket bleibt.")
        errors += 1
    return errors, warnings, lines


def main():
    args = sys.argv[1:]
    interactive = not args
    if interactive:
        args = [input("Pfad zur ZIP-Datei oder zum Ordner: ").strip().strip('"')]
    worst = 0
    for p in args:
        print(f"\n=== {p} ===")
        if not os.path.exists(p):
            print("  Datei nicht gefunden.")
            worst = 1
            continue
        try:
            e, w, lines = check(p)
        except zipfile.BadZipFile:
            print("  Das ist kein gültiges ZIP.")
            worst = 1
            continue
        print("\n".join(lines))
        if e:
            print(f"\nErgebnis: {e} Datei(en) werden abgelehnt (✗), {w} Hinweis(e) (!).")
            worst = 1
        elif w:
            print(f"\nErgebnis: alles wird übernommen, {w} Hinweis(e) (!).")
        else:
            print("\nErgebnis: alles wird übernommen.")
    print("\nLegende: ✓ übernommen · ✗ abgelehnt · ! Hinweis · · Info")
    if interactive:
        input("\nEnter zum Beenden …")
    return worst


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        try:
            sys.stdout.reconfigure(encoding="utf-8")
        except Exception:
            pass
    sys.exit(main())
