package it.unibo.alchemist.model.actions.zones

import it.unibo.alchemist.model.Molecule
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.actions.utils.Direction
import it.unibo.alchemist.model.actions.utils.Movement
import it.unibo.alchemist.model.actions.zones.shapes.ZoneShape
import it.unibo.alchemist.model.geometry.Euclidean2DShape
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.physics.environments.Physics2DEnvironment
import it.unibo.alchemist.model.positions.Euclidean2DPosition

class RearZone(
    override val zoneShape: ZoneShape<Euclidean2DShape>,
    node: Node<Any>,
    private val environment: Physics2DEnvironment<Any>,
    private val movements: Map<Direction, Movement>,
    private val slowDownFactor: Double,
): AbstractZone(node, environment, movements) {
    override val visibleNodes: Molecule = SimpleMolecule("Rear zone")

    override fun getZoneCentroid(position: Euclidean2DPosition): Euclidean2DPosition {
        // TODO zone margin with heading consideration
        return Euclidean2DPosition(position.x, position.y + zoneShape.offset)
    }

    override fun getNextMovement(): Movement {
        return getRandomMovement().multiplyVelocity(slowDownFactor)
    }

}