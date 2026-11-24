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

    fun buyMove(p: PetState, m: MoveId): ActionResult {
        val price = price(m)
        if (price <= 0) return ActionResult(p, "Diese Attacke gibt es nicht im Laden.", false)
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
        if (p.stardust < it.price) return ActionResult(p, "Dafür fehlen noch ${it.price - p.stardust} ✨.", false)
        return ActionResult(p.copy(stardust = p.stardust - it.price, items = p.items + it.name), "${it.label} gekauft!", reaction = Reaction.HAPPY)
    }

    fun buyPotion(p: PetState, potion: Potion): ActionResult {
        if (p.stardust < potion.price) return ActionResult(p, "Dafür fehlen noch ${potion.price - p.stardust} ✨.", false)
        val ns = if (potion == Potion.HEAL) p.copy(healPotions = p.healPotions + 1) else p.copy(psiPotions = p.psiPotions + 1)
        return ActionResult(ns.copy(stardust = p.stardust - potion.price), "${potion.label} gekauft.")
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
