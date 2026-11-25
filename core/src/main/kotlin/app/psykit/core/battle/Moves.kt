package app.psykit.core.battle

import app.psykit.core.model.PetState
import app.psykit.core.model.Stage

/** Wen eine Attacke trifft. */
enum class Target { ONE, ONE_ANY_ROW, ALL_FRONT, ALL, SELF, RANDOM }

/** Woher man eine Attacke bekommt. */
sealed interface MoveSource {
    data class FromStage(val stage: Stage) : MoveSource
    data class Shop(val price: Int) : MoveSource
    data class Training(val stat: TrainStat, val needed: Int) : MoveSource
    data class Drop(val where: String) : MoveSource
    /** In einer Dungeon-Ebene versteckt (Index in Floors.all), nicht kaufbar. */
    data class Hidden(val floor: Int) : MoveSource
}

enum class TrainStat(val label: String) { ATK("Angriff"), DEF("Abwehr"), SPD("Tempo"), PSI("Psi") }

/**
 * Alle Attacken. [type] null = Unterstützung ohne Schaden.
 * Ein Trainings-Meilenstein bei 40 verstärkt die Trainings-Attacken (siehe [BattleEngine]).
 */
enum class MoveId(
    val label: String,
    val type: AttackType?,
    val power: Int,
    val psiCost: Int,
    val target: Target,
    val info: String,
    val source: MoveSource,
    val accuracy: Int = 95,
    val hits: Int = 1,
    val priority: Boolean = false,
) {
    PAW("Tatzenhieb", AttackType.CLAW, 30, 0, Target.ONE, "Einfacher Hieb", MoveSource.FromStage(Stage.BABY)),
    DUCK("Ducken", null, 0, 0, Target.SELF, "Halbiert den nächsten Treffer in dieser Runde", MoveSource.FromStage(Stage.BABY), priority = true),
    BUMP("Rempler", AttackType.BLUNT, 35, 1, Target.ONE, "20 % betäuben", MoveSource.FromStage(Stage.BABY)),
    PSI_WAVE("Psi-Welle", AttackType.PSI, 45, 3, Target.ONE_ANY_ROW, "Trifft vorne und hinten", MoveSource.FromStage(Stage.CHILD), accuracy = 100),
    MOON_SHIELD("Mondschild", null, 0, 3, Target.SELF, "2 Runden halber Schaden", MoveSource.FromStage(Stage.TEEN)),
    STAR_STORM("Sternensturm", AttackType.PSI, 80, 8, Target.ALL, "Trifft alle Gegner", MoveSource.FromStage(Stage.ADULT), accuracy = 100),
    SCRATCH_SPIN("Kratzwirbel", AttackType.CLAW, 12, 2, Target.ONE, "Drei schnelle Kratzer, jeder senkt die Abwehr", MoveSource.Shop(150), hits = 3),
    HISS("Fauchen", null, 0, 1, Target.ALL, "Angriff aller Gegner -1 Stufe", MoveSource.Shop(150)),
    GROOM("Putzen", null, 0, 1, Target.SELF, "Entfernt Gift, Brand und Verwirrung", MoveSource.Shop(150)),
    TAIL_WHIP("Schwanzpeitsche", AttackType.BLUNT, 25, 2, Target.ALL_FRONT, "Trifft alle vorne", MoveSource.Shop(200)),
    STINK_CLOUD("Stinkwolke", AttackType.POISON, 15, 3, Target.ALL, "Vergiftet alle Gegner", MoveSource.Shop(200), accuracy = 100),
    SPARK_SNEEZE("Funkenniesen", AttackType.FIRE, 25, 2, Target.ONE_ANY_ROW, "Brand für 3 Runden", MoveSource.Shop(250)),
    FROST_BREATH("Frosthauch", AttackType.FROST, 30, 2, Target.ONE_ANY_ROW, "Gegner wird langsamer", MoveSource.Shop(250)),
    NAP("Nickerchen", null, 0, 0, Target.SELF, "Setzt 1 Runde aus, füllt alle Psi-Punkte", MoveSource.Shop(300)),
    PURR("Schnurren", null, 0, 4, Target.SELF, "Heilt 25 % KP, einmal pro Kampf", MoveSource.Shop(350)),
    BELLY_FLOP("Bauchplatscher", AttackType.BLUNT, 60, 4, Target.ONE, "30 % betäuben, danach selbst langsam", MoveSource.Shop(400), accuracy = 90),
    LEVITATE_THROW("Schwebewurf", AttackType.PSI, 50, 5, Target.ONE_ANY_ROW, "Ignoriert Panzer", MoveSource.Shop(500), accuracy = 100),
    MIND_FLASH("Gedankenblitz", AttackType.PSI, 40, 3, Target.ONE_ANY_ROW, "Trifft immer und immer zuerst", MoveSource.Training(TrainStat.PSI, 20), accuracy = 100, priority = true),
    LUCKY_PAW("Glückspfote", null, 0, 2, Target.SELF, "Nächster Treffer ist sicher kritisch (×1,5)", MoveSource.Training(TrainStat.SPD, 20)),
    IRON_FUR("Eisenfell", null, 0, 2, Target.SELF, "Abwehr +2 Stufen für den Kampf", MoveSource.Training(TrainStat.DEF, 20)),
    RAGE_CLAW("Kralle der Wut", AttackType.CLAW, 55, 3, Target.ONE, "Stärker, je weniger KP man hat", MoveSource.Training(TrainStat.ATK, 20)),
    SPORES("Pilzsporen", AttackType.POISON, 20, 4, Target.ONE_ANY_ROW, "Gift und 40 % Schlaf", MoveSource.Drop("Pilzkönigin Morla")),
    BLIZZARD("Schneesturm", AttackType.FROST, 40, 6, Target.ALL, "Trifft alle, 30 % einfrieren", MoveSource.Drop("Klirr, die Gletscherkrabbe")),
    EMBER_BREATH("Glutatem", AttackType.FIRE, 50, 6, Target.ALL, "Trifft alle, Brand", MoveSource.Drop("Eisenofen")),
    TIGER_LEAP("Tigersprung", AttackType.CLAW, 70, 4, Target.ONE_ANY_ROW, "Springt über die vordere Reihe", MoveSource.Drop("Elite-Gegner in Welt 3")),
    NIGHTMARE_GAZE("Albtraumblick", AttackType.PSI, 60, 6, Target.ONE_ANY_ROW, "Verwirrt und senkt den Angriff", MoveSource.Drop("Der Albtraum"), accuracy = 100),
    // ---- versteckt in den Ebenen (nicht kaufbar)
    EMBER_SPARK("Glutfunke", AttackType.FIRE, 15, 0, Target.ONE_ANY_ROW, "30 % Brand für 2 Runden", MoveSource.Hidden(0)),
    POISON_BITE("Giftbiss", AttackType.POISON, 25, 1, Target.ONE, "Doppelt stark gegen Vergiftete, sonst 30 % Gift", MoveSource.Hidden(1)),
    DUST_WHIRL("Staubwirbel", null, 0, 2, Target.ALL, "Alle Gegner sehen 2 Runden schlechter (Treffsicherheit -15 %)", MoveSource.Hidden(2)),
    EARTH_STOMP("Erdstampfer", AttackType.BLUNT, 30, 3, Target.ALL_FRONT, "Trifft alle vorne und zieht den stärksten Gegner von hinten nach vorne", MoveSource.Hidden(3)),
    CLAW_TRAP("Krallenfalle", AttackType.CLAW, 40, 2, Target.SELF, "Konter: Wer dich in dieser Runde angreift, bekommt den Hieb", MoveSource.Hidden(4), priority = true),
    MIND_THEFT("Gedankenraub", AttackType.PSI, 25, 0, Target.ONE_ANY_ROW, "Du bekommst 3 Psi-Punkte dazu", MoveSource.Hidden(5), accuracy = 100),
    ICICLE_SPEAR("Eiszapfen", AttackType.FROST, 90, 3, Target.ONE_ANY_ROW, "Eine Runde aufladen, dann sehr starker Treffer, 30 % einfrieren", MoveSource.Hidden(6)),
    MIRROR_PAW("Spiegelpfote", null, 0, 2, Target.SELF, "Der nächste Zustand, den dir ein Gegner gibt, trifft ihn selbst", MoveSource.Hidden(7)),
    ICE_GLIDE("Eisgleiter", AttackType.FROST, 35, 2, Target.ONE, "Greift immer zuerst an, Tempo +1 Stufe", MoveSource.Hidden(8), priority = true),
    CRYSTAL_ARMOR("Kristallpanzer", null, 0, 3, Target.SELF, "Schluckt die nächsten 30 Schaden", MoveSource.Hidden(9)),
    AVALANCHE("Lawinenstoß", AttackType.BLUNT, 50, 4, Target.ALL_FRONT, "Trifft alle vorne, +25 % gegen Gepanzerte", MoveSource.Hidden(10)),
    SCISSOR_CUT("Scherenschnitt", AttackType.CLAW, 25, 3, Target.ONE_ANY_ROW, "Zwei Schnitte, ignorieren Panzer", MoveSource.Hidden(11), hits = 2),
    SPARK_RAIN("Funkenregen", AttackType.FIRE, 15, 3, Target.RANDOM, "Drei Funken auf zufällige Gegner, je 20 % Brand", MoveSource.Hidden(12), hits = 3),
    SOOT_CLOUD("Rußwolke", AttackType.POISON, 20, 3, Target.ALL, "Vergiftet alle und senkt ihre Treffsicherheit", MoveSource.Hidden(13)),
    HAMMER("Hammerschlag", AttackType.BLUNT, 75, 4, Target.ONE, "25 % betäuben, danach bist du 1 Runde langsam", MoveSource.Hidden(14), accuracy = 90),
    LAVA_HEART("Lavaherz", null, 0, 4, Target.SELF, "3 Runden +25 % Feuer-Schaden und immun gegen Brand", MoveSource.Hidden(15)),
    DREAM_CATCHER("Traumfänger", AttackType.PSI, 30, 2, Target.ONE_ANY_ROW, "Nimmt dem Ziel Schutz und Stärkungen weg", MoveSource.Hidden(16), accuracy = 100),
    LULLABY_SONG("Schlaflied", null, 0, 3, Target.ALL, "Jeder Gegner schläft mit 50 % für 1 Runde ein", MoveSource.Hidden(17)),
    SHOOTING_STAR("Sternschnuppe", AttackType.PSI, 100, 7, Target.RANDOM, "Trifft einen zufälligen Gegner, gegen Schlafende sicher kritisch", MoveSource.Hidden(18), accuracy = 100),
    ECLIPSE("Mondfinsternis", AttackType.PSI, 60, 8, Target.ALL, "Trifft alle, 30 % Verwirrung", MoveSource.Hidden(19), accuracy = 100),

    // ---- neu im Laden (wechselndes Angebot)
    SNOWBALL("Schneeball", AttackType.FROST, 20, 1, Target.ONE_ANY_ROW, "20 % verlangsamen", MoveSource.Shop(120)),
    STINKY_FOOT("Stinkefuß", AttackType.POISON, 10, 1, Target.ONE, "Vergiftet sicher", MoveSource.Shop(120)),
    FLARE("Leuchtkugel", AttackType.PSI, 20, 1, Target.ONE_ANY_ROW, "Dein nächster Treffer trifft sicher", MoveSource.Shop(150), accuracy = 100),
    PAW_WHIRL("Pfotenwirbel", AttackType.CLAW, 20, 2, Target.ALL_FRONT, "Senkt die Abwehr aller vorne", MoveSource.Shop(200)),
    HOT_WATER_BOTTLE("Wärmflasche", null, 0, 2, Target.SELF, "Heilt 15 % KP und taut Frost und Langsam auf", MoveSource.Shop(250)),
    HEADBUTT("Kopfnuss", AttackType.BLUNT, 45, 2, Target.ONE, "Kostet dich 5 % deiner KP", MoveSource.Shop(300));


    val isTrainingMove get() = source is MoveSource.Training

    companion object {
        fun parse(name: String): MoveId? = entries.firstOrNull { it.name == name }

        /** Attacken, die das Tierchen schon durch seine Stufe kann. */
        fun fromStage(stage: Stage): List<MoveId> =
            entries.filter { val s = it.source; s is MoveSource.FromStage && stage != Stage.EGG && stage.ordinal >= s.stage.ordinal }

        fun trainValue(p: PetState, stat: TrainStat) = when (stat) {
            TrainStat.ATK -> p.trainAtk
            TrainStat.DEF -> p.trainDef
            TrainStat.SPD -> p.trainSpd
            TrainStat.PSI -> p.trainPsi
        }

        fun fromTraining(p: PetState): List<MoveId> = entries.filter {
            val s = it.source
            s is MoveSource.Training && trainValue(p, s.stat) >= s.needed
        }

        /** Trainings-Attacke verstärkt (Trainingswert ab 40)? */
        fun empowered(p: PetState, m: MoveId): Boolean {
            val s = m.source
            return s is MoveSource.Training && trainValue(p, s.stat) >= 40
        }

        /** Alles, was das Tierchen kann: Stufe, Training, gekauft und gefunden. */
        fun known(p: PetState): List<MoveId> =
            (fromStage(p.stage) + fromTraining(p) + p.ownedMoves.mapNotNull { parse(it) }).distinct().sortedBy { it.ordinal }

        /** Die in Ebene [floor] versteckte Attacke. */
        fun hiddenIn(floor: Int): MoveId? = entries.firstOrNull { (it.source as? MoveSource.Hidden)?.floor == floor }

        /** Stufe einer Attacke nach Einsätzen: ab 20 Stufe II, ab 60 Stufe III. */
        const val LEVEL2_USES = 20
        const val LEVEL3_USES = 60
        fun levelFor(uses: Int) = when { uses >= LEVEL3_USES -> 3; uses >= LEVEL2_USES -> 2; else -> 1 }
        fun level(p: PetState, m: MoveId) = levelFor(p.moveUses[m.name] ?: 0)

        /** Die 4 ausgerüsteten Attacken (oder sinnvolle Standardwahl). */
        fun equipped(p: PetState): List<MoveId> {
            val known = known(p)
            val chosen = p.equippedMoves.mapNotNull { parse(it) }.filter { it in known }.distinct().take(4)
            if (chosen.isNotEmpty()) return chosen
            val stage = fromStage(p.stage)
            val attacks = (stage.filter { it.type != null }.sortedByDescending { it.power }) + stage.filter { it.type == null }
            return attacks.distinct().take(4)
        }
    }
}
