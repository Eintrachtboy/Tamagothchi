package app.psykit.core.model

import app.psykit.core.battle.AttackType
import app.psykit.core.battle.Body

enum class Slot(val label: String) { HEAD("Kopf"), EYES("Augen"), DECO("Deko") }

enum class Rarity(val label: String, val bonusPct: Int, val duplicateDust: Int) {
    COMMON("gewöhnlich", 2, 50),
    RARE("selten", 4, 100),
    EPIC("episch", 6, 150),
    LEGENDARY("legendär", 10, 200),
}

/** Wie ein Teil gezeichnet wird (die App zeichnet einfache Formen bzw. Emoji). */
enum class Look { STRAW, BOW, WIZARD, FEATHER, TOPHAT, MUSHROOM, FLOWERCROWN, BEANIE, HELMET, CAP, CROWN, NIGHTCAP, MASK,
    ROUND, MONOCLE, WEBBED, SUNGLASSES, CRYSTAL, GOGGLES, MOON, EMOJI }

/** Wirkung eines Teils. Prozentwerte als ganze Zahl. */
sealed interface Effect {
    data class TypeDamage(val type: AttackType, val pct: Int) : Effect
    data class BodyDamage(val body: Body, val pct: Int) : Effect
    data class Hp(val pct: Int) : Effect
    data class Attack(val pct: Int) : Effect
    data class Defense(val pct: Int) : Effect
    data class Speed(val pct: Int) : Effect
    data class Accuracy(val pct: Int) : Effect
    data class CritPoints(val points: Int) : Effect
    data class PsiRegen(val pct: Int) : Effect
    data class StartPsi(val points: Int) : Effect
    data class HealPerRound(val pct: Int) : Effect
    data class NetDodge(val pct: Int) : Effect
    data class BurnTaken(val pct: Int) : Effect
    data object AccuracyLock : Effect
    data object NoFreezeSlow : Effect
    data object NoConfusion : Effect
    data object LongerBurn : Effect
    data object ShorterSleep : Effect
    // außerhalb des Kampfs
    data class MoodDecay(val pct: Int) : Effect
    data class SleepEnergy(val pct: Int) : Effect
    data class GameDust(val pct: Int) : Effect
    data class DungeonDust(val pct: Int) : Effect
    data class ChestDust(val pct: Int) : Effect
    data class BattleExp(val pct: Int) : Effect
    data class GardenGrowth(val pct: Int) : Effect
    data object FewerSnails : Effect
    data object MixerBonus : Effect
}

/** Hüte, Brillen und Deko. world 0 = Laden. */
enum class Item(
    val label: String,
    val slot: Slot,
    val rarity: Rarity,
    val world: Int,
    val price: Int,
    val look: Look,
    val emoji: String,
    val bonusText: String,
    val effects: List<Effect>,
) {
    STRAW_HAT("Strohhut", Slot.HEAD, Rarity.COMMON, 0, 100, Look.STRAW, "👒", "+2 % Krallen-Schaden", listOf(Effect.TypeDamage(AttackType.CLAW, 2))),
    RED_BOW("Rote Schleife", Slot.HEAD, Rarity.COMMON, 0, 100, Look.BOW, "🎀", "+2 % KP", listOf(Effect.Hp(2))),
    ROUND_GLASSES("Rundbrille", Slot.EYES, Rarity.COMMON, 0, 100, Look.ROUND, "👓", "+2 % Treffsicherheit", listOf(Effect.Accuracy(2))),
    POT_PLANT("Topfpflanze", Slot.DECO, Rarity.COMMON, 0, 100, Look.EMOJI, "🪴", "Laune sinkt 2 % langsamer", listOf(Effect.MoodDecay(2))),
    FAIRY_LIGHTS("Lichterkette", Slot.DECO, Rarity.COMMON, 0, 100, Look.EMOJI, "✨", "+2 % Sternenstaub aus Minispielen", listOf(Effect.GameDust(2))),
    WIZARD_HAT("Zaubererhut", Slot.HEAD, Rarity.RARE, 0, 300, Look.WIZARD, "🔮", "+4 % Psi-Schaden", listOf(Effect.TypeDamage(AttackType.PSI, 4))),
    MONOCLE("Monokel", Slot.EYES, Rarity.RARE, 0, 300, Look.MONOCLE, "🧐", "Krit-Chance +2 Prozentpunkte", listOf(Effect.CritPoints(2))),
    AQUARIUM("Aquarium", Slot.DECO, Rarity.RARE, 0, 300, Look.EMOJI, "🐠", "+4 % Sternenstaub im Dungeon", listOf(Effect.DungeonDust(4))),

    DUSTER_HAT("Staubwedel-Hut", Slot.HEAD, Rarity.RARE, 1, 0, Look.FEATHER, "🪶", "+4 % Wucht-Schaden", listOf(Effect.TypeDamage(AttackType.BLUNT, 4))),
    WEB_GLASSES("Kellerbrille mit Spinnwebe", Slot.EYES, Rarity.RARE, 1, 0, Look.WEBBED, "🕸️", "25 % Chance, Netzen auszuweichen", listOf(Effect.NetDodge(25))),
    CANDLE("Kerzenstummel", Slot.DECO, Rarity.COMMON, 1, 0, Look.EMOJI, "🕯️", "+2 % Treffsicherheit", listOf(Effect.Accuracy(2))),
    OLD_CHEST("Alte Kiste", Slot.DECO, Rarity.EPIC, 1, 0, Look.EMOJI, "📦", "+6 % Sternenstaub aus Truhen", listOf(Effect.ChestDust(6))),
    SOOT_TOPHAT("Rußzylinder", Slot.HEAD, Rarity.LEGENDARY, 1, 0, Look.TOPHAT, "🎩", "+10 % Krallen-Schaden, Treffsicherheit kann nicht gesenkt werden", listOf(Effect.TypeDamage(AttackType.CLAW, 10), Effect.AccuracyLock)),

    MUSHROOM_HAT("Pilzhut", Slot.HEAD, Rarity.RARE, 2, 0, Look.MUSHROOM, "🍄", "+4 % Gift-Schaden", listOf(Effect.TypeDamage(AttackType.POISON, 4))),
    LILY_SUNGLASSES("Seerosen-Sonnenbrille", Slot.EYES, Rarity.RARE, 2, 0, Look.SUNGLASSES, "🕶️", "+4 % Feuer-Schaden", listOf(Effect.TypeDamage(AttackType.FIRE, 4))),
    FIREFLY_JAR("Glühwürmchenglas", Slot.DECO, Rarity.COMMON, 2, 0, Look.EMOJI, "🫙", "+2 % EP aus Kämpfen", listOf(Effect.BattleExp(2))),
    SMALL_POND("Kleiner Teich", Slot.DECO, Rarity.EPIC, 2, 0, Look.EMOJI, "🪷", "Gartenbeeren wachsen 6 % schneller", listOf(Effect.GardenGrowth(6))),
    FLOWER_CROWN("Morlas Blumenkrone", Slot.HEAD, Rarity.LEGENDARY, 2, 0, Look.FLOWERCROWN, "🌸", "+10 % Schaden gegen Pflanzen, heilt 3 % KP pro Runde", listOf(Effect.BodyDamage(Body.PLANT, 10), Effect.HealPerRound(3))),

    BOBBLE_HAT("Bommelmütze", Slot.HEAD, Rarity.RARE, 3, 0, Look.BEANIE, "🧶", "+4 % Frost-Schaden", listOf(Effect.TypeDamage(AttackType.FROST, 4))),
    CRYSTAL_GLASSES("Eiskristallbrille", Slot.EYES, Rarity.RARE, 3, 0, Look.CRYSTAL, "💎", "+4 % Psi-Punkte-Erholung", listOf(Effect.PsiRegen(4))),
    SNOW_GLOBE("Schneekugel", Slot.DECO, Rarity.COMMON, 3, 0, Look.EMOJI, "🔮", "+2 % Abwehr", listOf(Effect.Defense(2))),
    ICICLE_LIGHTS("Eiszapfen-Lichterkette", Slot.DECO, Rarity.EPIC, 3, 0, Look.EMOJI, "🧊", "+6 % Tempo", listOf(Effect.Speed(6))),
    GLACIER_HELMET("Gletscherhelm", Slot.HEAD, Rarity.LEGENDARY, 3, 0, Look.HELMET, "⛑️", "+10 % Abwehr, immun gegen Eingefroren und Langsam", listOf(Effect.Defense(10), Effect.NoFreezeSlow)),

    SMITH_CAP("Schmiedekappe", Slot.HEAD, Rarity.RARE, 4, 0, Look.CAP, "🧢", "+4 % Angriff", listOf(Effect.Attack(4))),
    WELDER_GOGGLES("Schweißerbrille", Slot.EYES, Rarity.RARE, 4, 0, Look.GOGGLES, "🥽", "Brand-Schaden an dir -25 %", listOf(Effect.BurnTaken(25))),
    FIREPLACE("Kamin", Slot.DECO, Rarity.COMMON, 4, 0, Look.EMOJI, "🔥", "Energie erholt sich im Schlaf 2 % schneller", listOf(Effect.SleepEnergy(2))),
    ANVIL("Amboss", Slot.DECO, Rarity.EPIC, 4, 0, Look.EMOJI, "⚒️", "+6 % Wucht-Schaden", listOf(Effect.TypeDamage(AttackType.BLUNT, 6))),
    EMBER_CROWN("Glutkrone", Slot.HEAD, Rarity.LEGENDARY, 4, 0, Look.CROWN, "👑", "+10 % Feuer-Schaden, Brand hält 1 Runde länger", listOf(Effect.TypeDamage(AttackType.FIRE, 10), Effect.LongerBurn)),

    STAR_NIGHTCAP("Schlafmütze mit Sternen", Slot.HEAD, Rarity.RARE, 5, 0, Look.NIGHTCAP, "🌙", "Schlaf dauert 1 Runde kürzer", listOf(Effect.ShorterSleep)),
    MOON_GLASSES("Mondbrille", Slot.EYES, Rarity.RARE, 5, 0, Look.MOON, "🌗", "+4 % Psi-Schaden", listOf(Effect.TypeDamage(AttackType.PSI, 4))),
    CUCKOO_CLOCK("Kuckucksuhr", Slot.DECO, Rarity.COMMON, 5, 0, Look.EMOJI, "🕰️", "+2 % Tempo", listOf(Effect.Speed(2))),
    STARRY_SKY("Sternenhimmel", Slot.DECO, Rarity.EPIC, 5, 0, Look.EMOJI, "🌌", "Kampfbeginn mit 2 extra Psi-Punkten", listOf(Effect.StartPsi(2))),
    NIGHTMARE_MASK("Albtraummaske", Slot.HEAD, Rarity.LEGENDARY, 5, 0, Look.MASK, "🎭", "+10 % Schaden gegen Geister, immun gegen Verwirrung", listOf(Effect.BodyDamage(Body.GHOST, 10), Effect.NoConfusion)),

    // Belohnungen aus dem Garten (world 9: weder Laden noch Dungeon)
    SCARECROW("Vogelscheuche", Slot.DECO, Rarity.COMMON, 9, 0, Look.EMOJI, "🎃", "Gartenbeeren wachsen 2 % schneller", listOf(Effect.GardenGrowth(2))),
    GARDEN_GNOME("Gartenzwerg", Slot.DECO, Rarity.RARE, 9, 0, Look.EMOJI, "🧙", "Schnecken kommen halb so oft", listOf(Effect.FewerSnails)),
    GLASS_MIXER("Glas-Mixer", Slot.DECO, Rarity.RARE, 9, 0, Look.EMOJI, "🫙", "Bei jedem Smoothie kommt eine Beere zurück", listOf(Effect.MixerBonus)),
    WATERING_HAT("Gießkannenhut", Slot.HEAD, Rarity.LEGENDARY, 9, 0, Look.STRAW, "👒", "Gartenbeeren wachsen 6 % schneller", listOf(Effect.GardenGrowth(6)));

    val gardenReward get() = world == 9

    val inShop get() = world == 0

    companion object {
        fun parse(name: String?): Item? = entries.firstOrNull { it.name == name }
        fun ofWorld(world: Int) = entries.filter { it.world == world }
        fun bossItem(world: Int) = entries.first { it.world == world && it.rarity == Rarity.LEGENDARY }

        /** Die Teile, deren Bonus gerade wirkt: Hut, Brille und Glücksbringer. */
        fun active(p: PetState): List<Item> = listOfNotNull(parse(p.headItem), parse(p.eyesItem), parse(p.charmItem))
            .filter { it.name in p.items }

        fun effects(p: PetState): List<Effect> = active(p).flatMap { it.effects }

        /** Welten, deren Set komplett ist (+5 % Schaden gegen deren Gegner, Titel). */
        fun completeSets(p: PetState): List<Int> = (1..5).filter { w -> ofWorld(w).all { it.name in p.items } }

        fun setTitle(world: Int) = when (world) {
            1 -> "König des Kellers"
            2 -> "Gärtner der Wildnis"
            3 -> "Herr der Grotte"
            4 -> "Meister der Schmiede"
            else -> "Bezwinger der Träume"
        }

        inline fun <reified T : Effect> sum(p: PetState, f: (T) -> Int): Int = effects(p).filterIsInstance<T>().sumOf(f)
        inline fun <reified T : Effect> has(p: PetState): Boolean = effects(p).any { it is T }
    }
}
