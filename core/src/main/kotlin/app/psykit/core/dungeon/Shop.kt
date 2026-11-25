package app.psykit.core.dungeon

import app.psykit.core.battle.MoveId
import app.psykit.core.battle.MoveSource
import app.psykit.core.battle.Potion
import app.psykit.core.engine.ActionResult
import app.psykit.core.model.Item
import app.psykit.core.model.PetState
import app.psykit.core.model.Reaction
import app.psykit.core.model.Slot

/** Laden, Ankleiden und Attacken ausrüsten. */
object Shop {
    const val MAX_DECO = 4

    val moves: List<MoveId> get() = MoveId.entries.filter { it.source is MoveSource.Shop }
    val items: List<Item> get() = Item.entries.filter { it.inShop }

    fun price(m: MoveId) = (m.source as? MoveSource.Shop)?.price ?: 0

    // ------------------------------------------------------------ Tagesangebot

    const val OFFER_MOVES = 4
    const val OFFER_ITEMS = 3
    const val DEAL_PCT = 25
    const val REROLL_PRICE = 30
    const val HIGHLIGHT_PRICE = 400

    /** Montag (Tag 0 = Donnerstag, 1.1.1970). */
    fun isHighlightDay(day: Long) = Math.floorMod(day + 3, 7L) == 0L

    /** Preis heute (mit Angebot −25 %). */
    fun price(p: PetState, m: MoveId): Int {
        val base = price(m)
        return if (m.name == p.shopDeal) base * (100 - DEAL_PCT) / 100 else base
    }

    fun offerMoves(p: PetState): List<MoveId> = p.shopMoves.mapNotNull { MoveId.parse(it) }
    fun offerItems(p: PetState): List<Item> = p.shopItems.mapNotNull { Item.parse(it) }

    private fun roll(p: PetState, day: Long, salt: Int): PetState {
        val rng = kotlin.random.Random(p.bornAt * 31 + day * 7919 + salt)
        val known = MoveId.known(p).toSet()
        val open = moves.filter { it !in known }
        val picked = mutableListOf<MoveId>()
        if (isHighlightDay(day)) open.filter { price(it) >= HIGHLIGHT_PRICE }.randomOrNull(rng)?.let { picked += it }
        picked += open.filter { it !in picked }.shuffled(rng).take(OFFER_MOVES)
        val deal = picked.randomOrNull(rng)
        val goods = items.filter { it.name !in p.items }.shuffled(rng).take(OFFER_ITEMS)
        return p.copy(shopDay = day, shopMoves = picked.map { it.name }, shopItems = goods.map { it.name }, shopDeal = deal?.name)
    }

    /** Neues Tagesangebot, wenn ein neuer Tag begonnen hat. */
    fun refresh(p: PetState, day: Long): PetState =
        if (p.shopDay == day) p else roll(p, day, 0).copy(shopRerolled = false)

    fun canReroll(p: PetState, day: Long) = !(p.shopDay == day && p.shopRerolled)

    /** Einmal am Tag für 30 ✨ neu würfeln. */
    fun reroll(p: PetState, day: Long): ActionResult {
        if (!canReroll(p, day)) return ActionResult(p, "Heute wurde schon neu gewürfelt. Morgen gibt es ein neues Angebot.", false)
        if (p.stardust < REROLL_PRICE) return ActionResult(p, "Dafür fehlen noch ${REROLL_PRICE - p.stardust} ✨.", false)
        val ns = roll(p.copy(stardust = p.stardust - REROLL_PRICE), day, 1).copy(shopRerolled = true)
        return ActionResult(ns, "🎲 Der Laden hat neue Ware ausgelegt.", reaction = Reaction.HAPPY)
    }

    fun buyMove(p: PetState, m: MoveId): ActionResult {
        if (price(m) <= 0) return ActionResult(p, "Diese Attacke gibt es nicht im Laden.", false)
        if (m.name !in p.shopMoves) return ActionResult(p, "${m.label} ist heute nicht im Angebot.", false)
        val price = price(p, m)
        if (m in MoveId.known(p)) return ActionResult(p, "${p.name} kann ${m.label} schon.", false)
        if (p.stardust < price) return ActionResult(p, "Dafür fehlen noch ${price - p.stardust} ✨.", false)
        var ns = p.copy(stardust = p.stardust - price, ownedMoves = p.ownedMoves + m.name)
        // Freien Platz gleich belegen
        val eq = MoveId.equipped(p)
        if (eq.size < 4) ns = ns.copy(equippedMoves = (eq + m).map { it.name })
        return ActionResult(ns, "${m.label} gelernt!", reaction = Reaction.JUMP)
    }

    fun buyItem(p: PetState, it: Item): ActionResult {
        if (!it.inShop) return ActionResult(p, "Das gibt es nur im Dungeon.", false)
        if (it.name in p.items) return ActionResult(p, "Hast du schon.", false)
        if (it.name !in p.shopItems) return ActionResult(p, "${it.label} ist heute nicht im Angebot.", false)
        if (p.stardust < it.price) return ActionResult(p, "Dafür fehlen noch ${it.price - p.stardust} ✨.", false)
        return ActionResult(p.copy(stardust = p.stardust - it.price, items = p.items + it.name), "${it.label} gekauft!", reaction = Reaction.HAPPY)
    }

    fun buyPotion(p: PetState, potion: Potion): ActionResult {
        if (p.stardust < potion.price) return ActionResult(p, "Dafür fehlen noch ${potion.price - p.stardust} ✨.", false)
        val ns = if (potion == Potion.HEAL) p.copy(healPotions = p.healPotions + 1) else p.copy(psiPotions = p.psiPotions + 1)
        return ActionResult(ns.copy(stardust = p.stardust - potion.price), "${potion.label} gekauft.")
    }

    const val DRINK_PRICE = 50
    const val DRINK_ENERGY = 40f
    const val DRINKS_PER_DAY = 3

    fun drinksLeft(p: PetState): Int = DRINKS_PER_DAY - if (p.energyDrinkDay == p.questDay) p.energyDrinksToday else 0

    /** Energiedrink: sofort +40 Energie (weckt auch auf), höchstens 3 am Tag. */
    fun drinkEnergy(p: PetState): ActionResult {
        if (p.stage == app.psykit.core.model.Stage.EGG) return ActionResult(p, "Ein Ei trinkt noch nichts.", false)
        if (p.energy >= 99f) return ActionResult(p, "${p.name} ist schon voller Energie.", false)
        val left = drinksLeft(p)
        if (left <= 0) return ActionResult(p, "Für heute reicht's mit Energiedrinks. Morgen wieder!", false)
        if (p.stardust < DRINK_PRICE) return ActionResult(p, "Dafür fehlen noch ${DRINK_PRICE - p.stardust} ✨.", false)
        val today = if (p.energyDrinkDay == p.questDay) p.energyDrinksToday else 0
        val ns = p.copy(
            stardust = p.stardust - DRINK_PRICE,
            energy = (p.energy + DRINK_ENERGY).coerceAtMost(100f),
            sleeping = false,
            energyDrinkDay = p.questDay,
            energyDrinksToday = today + 1,
        )
        return ActionResult(ns, "⚡ Zisch! ${p.name} ist hellwach: +${DRINK_ENERGY.toInt()} Energie.", reaction = Reaction.JUMP)
    }

    fun buyKey(p: PetState): ActionResult {
        if (p.keys >= DungeonEngine.MAX_KEYS) return ActionResult(p, "Mehr als ${DungeonEngine.MAX_KEYS} Schlüssel passen nicht in den Beutel.", false)
        if (p.stardust < DungeonEngine.KEY_PRICE) return ActionResult(p, "Dafür fehlen noch ${DungeonEngine.KEY_PRICE - p.stardust} ✨.", false)
        return ActionResult(p.copy(stardust = p.stardust - DungeonEngine.KEY_PRICE, keys = p.keys + 1), "Schlüssel gekauft.")
    }

    /** Hut oder Brille anlegen (null = ablegen). */
    fun wear(p: PetState, slot: Slot, it: Item?): PetState {
        if (it != null && (it.name !in p.items || it.slot != slot)) return p
        return when (slot) {
            Slot.HEAD -> p.copy(headItem = it?.name)
            Slot.EYES -> p.copy(eyesItem = it?.name)
            Slot.DECO -> p
        }
    }

    /** Glücksbringer: irgendein eigenes Teil, dessen Bonus wirken soll (meist Deko). */
    fun setCharm(p: PetState, it: Item?): PetState {
        if (it != null && it.name !in p.items) return p
        return p.copy(charmItem = it?.name)
    }

    fun toggleDeco(p: PetState, it: Item): ActionResult {
        if (it.slot != Slot.DECO || it.name !in p.items) return ActionResult(p, "", false)
        return if (it.name in p.placedDeco) ActionResult(p.copy(placedDeco = p.placedDeco - it.name), "${it.label} weggeräumt.")
        else if (p.placedDeco.size >= MAX_DECO) ActionResult(p, "Es passen höchstens $MAX_DECO Deko-Teile ins Bild.", false)
        else ActionResult(p.copy(placedDeco = p.placedDeco + it.name), "${it.label} aufgestellt.")
    }

    fun setMoves(p: PetState, moves: List<MoveId>): PetState {
        val known = MoveId.known(p)
        return p.copy(equippedMoves = moves.filter { it in known }.distinct().take(4).map { it.name })
    }
}
