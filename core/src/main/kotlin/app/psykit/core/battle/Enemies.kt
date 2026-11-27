package app.psykit.core.battle

/** Rollen-Faktoren auf die Grundwerte eines Levels. */
enum class Role(val label: String, val hp: Double, val atk: Double, val def: Double, val spd: Double, val cost: Int, val front: Boolean) {
    BRUTE("Brocken", 1.5, 1.2, 1.0, 0.7, 3, true),
    TANK("Panzer", 1.1, 0.7, 1.6, 0.6, 2, true),
    QUICK("Flitzer", 0.7, 1.0, 0.7, 1.5, 2, true),
    HEALER("Heiler", 0.8, 0.6, 0.9, 0.9, 2, false),
    DISRUPTOR("Störer", 0.8, 0.8, 0.8, 1.1, 2, false),
    SWARM("Schwarm", 0.5, 0.6, 0.6, 1.0, 1, true),
}

/** Der Trick jedes Gegners. Passive Tricks wirken immer, aktive werden angekündigt. */
enum class Trick(val label: String, val passive: Boolean = false) {
    SNEEZE("Niesen"),
    CURL("Einrollen"),
    DUST_CLOUD("Staubwolke"),
    WEB("Netz"),
    COUNTDOWN("Explosion", passive = true),
    SPLIT("Teilen", passive = true),
    THORNS("Dornen", passive = true),
    TONGUE("Zunge"),
    SPORE_HEAL("Sporenheilung"),
    DOUBLE_STING("Doppelstich"),
    ICICLE("Eiszapfen-Fall"),
    FROST_TOUCH("Frostberührung"),
    PINCER("Scherenkonter", passive = true),
    MIRROR("Funkeln"),
    RAGE("Wut", passive = true),
    BURROW("Eingraben"),
    HEAVY_SMASH("Schwerer Schlag"),
    STEAL("Klauen"),
    BURN_TRAIL("Brandspur"),
    FORGE("Schmieden"),
    LULLABY("Gute-Nacht-Lied"),
    COPY("Kopieren"),
    CLOCKWORK("Uhrwerk", passive = true),
    WHISPER("Flüstern"),
    HOWL("Heulen"),
    NONE("–", passive = true),
}

enum class EnemyKind(
    val label: String,
    val emoji: String,
    val world: Int,
    val body: Body,
    val role: Role,
    val trick: Trick,
    val info: String,
) {
    DUST_BUNNY("Staubfluse", "🌫️", 1, Body.SOFT, Role.SWARM, Trick.SNEEZE, "Niesen: deine Treffsicherheit -15 %"),
    WOODLOUSE("Kellerassel", "🪲", 1, Body.ARMORED, Role.TANK, Trick.CURL, "Rollt sich ein: 1 Runde Abwehr ×2"),
    MOTH("Mottenflatter", "🦋", 1, Body.QUICK, Role.DISRUPTOR, Trick.DUST_CLOUD, "Staubwolke: verwirrt"),
    SPIDER("Spinne Webbi", "🕷️", 1, Body.SOFT, Role.DISRUPTOR, Trick.WEB, "Netz: du setzt 1 Runde aus"),
    SOOT_SPARK("Rußfunke", "🔥", 1, Body.EMBER, Role.QUICK, Trick.COUNTDOWN, "Explodiert nach 4 Runden. Frost löscht ihn, Ducken hilft"),
    MUD_SLIME("Matschschleim", "🟢", 2, Body.SOFT, Role.BRUTE, Trick.SPLIT, "Teilt sich bei Krallentreffern"),
    THORN_VINE("Dornenranke", "🌿", 2, Body.PLANT, Role.TANK, Trick.THORNS, "Krallen und Wucht stechen dich selbst"),
    SNAP_FROG("Schnappfrosch", "🐸", 2, Body.QUICK, Role.QUICK, Trick.TONGUE, "Zunge: klaut einen Trank"),
    SPORE_SHROOM("Sporenpilz", "🍄", 2, Body.PLANT, Role.HEALER, Trick.SPORE_HEAL, "Heilt alle und vergiftet dich"),
    HORNET("Hornisse", "🐝", 2, Body.QUICK, Role.QUICK, Trick.DOUBLE_STING, "Sticht zweimal"),
    ICICLE("Eiszapfel", "🧊", 3, Body.ARMORED, Role.BRUTE, Trick.ICICLE, "Es knackt: großer Schaden in der nächsten Runde"),
    SNOWFLAKE("Flöckchen", "❄️", 3, Body.GHOST, Role.SWARM, Trick.FROST_TOUCH, "Berührung friert ein"),
    FROST_CRAB("Frostkrabbe", "🦀", 3, Body.ARMORED, Role.TANK, Trick.PINCER, "Kontert Krallenangriffe"),
    CRYSTAL_BAT("Kristallfledermaus", "🦇", 3, Body.QUICK, Role.DISRUPTOR, Trick.MIRROR, "Wirft die nächste Psi-Attacke zurück"),
    YETI("Yeti-Junges", "🐻‍❄️", 3, Body.SOFT, Role.BRUTE, Trick.RAGE, "Unter 50 % KP doppelter Schaden"),
    EMBER_WORM("Glutwurm", "🪱", 4, Body.EMBER, Role.QUICK, Trick.BURROW, "Eingegraben treffen nur Wucht und Psi"),
    ASH_GOLEM("Aschegolem", "🗿", 4, Body.ARMORED, Role.BRUTE, Trick.HEAVY_SMASH, "Schlägt alle 2 Runden extrem hart zu"),
    SPARK_IMP("Funkenkobold", "👺", 4, Body.EMBER, Role.DISRUPTOR, Trick.STEAL, "Klaut Sternenstaub und flieht nach 3 Runden"),
    LAVA_SNAIL("Lavaschnecke", "🐌", 4, Body.EMBER, Role.TANK, Trick.BURN_TRAIL, "Brandspur: Brand für dich"),
    LIVING_HAMMER("Lebender Hammer", "🔨", 4, Body.ARMORED, Role.HEALER, Trick.FORGE, "Angriff aller Verbündeten +1 Stufe"),
    PILLOW("Kissenmonster", "🛏️", 5, Body.SOFT, Role.DISRUPTOR, Trick.LULLABY, "Schlaf für 2 Runden"),
    MIRROR_CAT("Spiegelkatze", "🐈‍⬛", 5, Body.GHOST, Role.QUICK, Trick.COPY, "Kopiert deine letzte Attacke"),
    CLOCK_MOUSE("Uhrwerkmaus", "🐭", 5, Body.ARMORED, Role.QUICK, Trick.CLOCKWORK, "Jede 3. Runde handelt sie zweimal"),
    WHISPERER("Alpflüsterer", "👻", 5, Body.GHOST, Role.HEALER, Trick.WHISPER, "Deine Psi-Punkte -3, heilt Verbündete"),
    SHADOW_WOLF("Schattenwolf", "🐺", 5, Body.GHOST, Role.BRUTE, Trick.HOWL, "Ruft eine Staubfluse zu Hilfe"),
    SLIMELING("Schleimling", "🫧", 2, Body.SOFT, Role.SWARM, Trick.NONE, "Kleiner Schleim aus einem Matschschleim");

    /** Typ der normalen Angriffe, passend zum Körper. */
    val attackType: AttackType get() = when (body) {
        Body.EMBER -> AttackType.FIRE
        Body.GHOST -> AttackType.PSI
        Body.PLANT -> AttackType.POISON
        Body.ARMORED -> AttackType.BLUNT
        else -> AttackType.CLAW
    }

    companion object {
        fun ofWorld(w: Int) = entries.filter { it.world == w && it != SLIMELING }
    }
}

enum class BossKind(val label: String, val emoji: String, val world: Int, val level: Int, val body: Body, val weakness: AttackType, val info: String) {
    SOOT_KING("Rußkönig", "🤴", 1, 10, Body.EMBER, AttackType.FROST, "Ruft Staubflusen. Ab 50 % Rußwolke: Wucht wirbelt sie weg."),
    MORLA("Pilzkönigin Morla", "🍄", 2, 16, Body.PLANT, AttackType.FIRE, "Pflanzt heilende Pilze – besiege sie zuerst, solange einer lebt, wirkt der Sporensturm. Später Wurzelgriff."),
    KLIRR("Klirr, die Gletscherkrabbe", "🦀", 3, 22, Body.ARMORED, AttackType.BLUNT, "3 Panzerschichten, Wucht bricht sie. Ohne Panzer doppelter Schaden."),
    FURNACE("Eisenofen", "🏭", 4, 30, Body.EMBER, AttackType.FROST, "Hitzeleiste steigt, bei 5 explodiert er. Frost kühlt ab."),
    NIGHTMARE("Der Albtraum", "🌑", 5, 40, Body.GHOST, AttackType.PSI, "Kopiert dich, wird zu deinem Schatten, vertauscht deine Attacken."),
}
