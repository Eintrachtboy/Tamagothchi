package app.psykit.core.battle

import app.psykit.core.model.PetState
import app.psykit.core.model.Stage

/** Wen eine Attacke trifft. */
enum class Target { ONE, ONE_ANY_ROW, ALL_FRONT, ALL, SELF }

/** Woher man eine Attacke bekommt. */
sealed interface MoveSource {
    data class FromStage(val stage: Stage) : MoveSource
    data class Shop(val price: Int) : MoveSource
    data class Training(val stat: TrainStat, val needed: Int) : MoveSource
    data class Drop(val where: String) : MoveSource
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
    NIGHTMARE_GAZE("Albtraumblick", AttackType.PSI, 60, 6, Target.ONE_ANY_ROW, "Verwirrt und senkt den Angriff", MoveSource.Drop("Der Albtraum"), accuracy = 100);

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
