package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Context
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.actions.utils.Direction
import it.unibo.alchemist.model.actions.utils.Movement
import it.unibo.alchemist.model.actions.zones.AttractionZone
import it.unibo.alchemist.model.actions.zones.NeutralZone
import it.unibo.alchemist.model.actions.zones.RearZone
import it.unibo.alchemist.model.actions.zones.StressZone
import it.unibo.alchemist.model.actions.zones.Zone
import it.unibo.alchemist.model.actions.zones.shapes.ZoneShapeFactoryImpl
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.physics.environments.ContinuousPhysics2DEnvironment
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import org.protelis.lang.datatype.impl.ArrayTupleImpl
import java.lang.IllegalStateException
import java.lang.Math.toRadians
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// @JvmOverloads
class Grouping constructor(
    node: Node<Any>,
    private val environment: ContinuousPhysics2DEnvironment<Any>,
    private val stressZoneRadius: Double,
    private val neutralZoneRadius: Double,
    private val attractionZoneRadius: Double,
    private val rearZoneRadius: Double,
    private val repulsionFactor: Double,
    private val slowDownFactor: Double,
    private val speedUpFactor: Double,
) : AbstractAction<Any>(node) {
    val zones: List<Zone>
    private val stressZone: StressZone
    private val rearZone: RearZone
    private val movements: Map<Direction, Movement>

    companion object {
        const val STRESS_ZONE_ELLIPSE_RATIO = 2.0
        const val DIRECTIONAL_ZONE_ANGLE = 180.0 // degrees
    }

    init {
        environment.setHeading(node, Euclidean2DPosition(0.0, 1.0))
        val probabilities = getMoleculeDoubleTupleValues("MovementProbabilities")
        val velocities = getMoleculeDoubleTupleValues("Velocities")
        movements = mapOf(
            Direction.LEFT to Movement(-velocities[0], 0.0, probabilities[0]),
            Direction.FORWARD to Movement(0.0, velocities[1], probabilities[1]),
            Direction.RIGHT to Movement(velocities[0], 0.0, probabilities[2]),
        )
        val list: MutableList<Zone> = mutableListOf()

        val zoneShapeFactory = ZoneShapeFactoryImpl(environment.shapeFactory)
//        val stressZoneShape = zoneShapeFactory.produceRectangularZoneShape(stressZoneWidth * 2, stressZoneHeight * 2, ZoneType.FRONT_AND_REAR)
        val stressZoneShape = zoneShapeFactory.produceEllipseZoneShape(stressZoneRadius, STRESS_ZONE_ELLIPSE_RATIO)
        stressZone = StressZone(stressZoneShape, node, environment, movements, repulsionFactor)
        list.add(stressZone)

//        val neutralZoneShape = zoneShapeFactory.produceRectangularZoneShape(24.0, 24.0, ZoneType.FRONT)
        val neutralZoneShape = zoneShapeFactory.produceCircularSectorZoneShape(neutralZoneRadius, DIRECTIONAL_ZONE_ANGLE)
        list.add(NeutralZone(neutralZoneShape, node, environment, movements))

        val attractionZoneShape = zoneShapeFactory.produceCircularSectorZoneShape(attractionZoneRadius, DIRECTIONAL_ZONE_ANGLE)
        list.add(AttractionZone(attractionZoneShape, node, environment, movements, speedUpFactor))

        val rearZoneShape = zoneShapeFactory.produceCircularSectorZoneShape(rearZoneRadius, DIRECTIONAL_ZONE_ANGLE, true)
        rearZone = RearZone(rearZoneShape, node, environment, movements, slowDownFactor)
        list.add(rearZone)
        zones = list.toList()
    }

    private fun getMoleculeDoubleTupleValues(moleculeName: String): List<Double> {
        return (getMoleculeValue(moleculeName) as ArrayTupleImpl).toList().map { it.toString().toDouble() }
    }

    override fun cloneAction(node: Node<Any>, reaction: Reaction<Any>) =
        Grouping(node, environment, stressZoneRadius, neutralZoneRadius, attractionZoneRadius, rearZoneRadius, repulsionFactor, slowDownFactor, speedUpFactor)

    override fun execute() {
        environment.moveNode(node, getNextPosition())
    }

    override fun getContext(): Context = Context.LOCAL

    private fun getAngle(position: Euclidean2DPosition): Double {
        return position.asAngle - Math.PI / 2
    }

    private fun getMoleculeValue(moleculeName: String) =
        node.contents.getValue(SimpleMolecule(moleculeName))

    private fun getNextPosition(): Euclidean2DPosition {
        if (Random.nextDouble() < 0.01) {
            val headingAngle = environment.getHeading(node).asAngle + toRadians(1.0)
            environment.setHeading(node, environment.makePosition(cos(headingAngle), sin(headingAngle)))
        }
        for (zone in zones) {
            if (zone.areNodesInZone()) {
                var movement = zone.getNextMovement()
                if (!rearZone.areNodesInZone() && Random.nextDouble() <= 0.3) {
                    movement = movement.multiplyVelocity(2.0)
                }
                if (zone is NeutralZone && Random.nextDouble() < 0.1) {
                    val nodes = zone.getNodesInZone(environment.getPosition(node))

                    val (vector, count) = nodes.map { environment.getHeading(it) }.foldRight(Pair(Euclidean2DPosition(0.0, 0.0), 0)) { elem, acc ->
                        Pair(Euclidean2DPosition(acc.first.x + elem.x, acc.first.y + elem.y), acc.second + 1)
                    }
                    val avgHeading = (Euclidean2DPosition(vector.x / count, vector.y / count))
                    environment.setHeading(node, avgHeading)
                }

                node.setConcentration(SimpleMolecule("zone"), zone::class)
                node.setConcentration(SimpleMolecule("x"), movement.lateralVelocity)
                node.setConcentration(SimpleMolecule("y"), movement.forwardVelocity)

                val newPosition = environment.makePosition(movement.lateralVelocity, movement.forwardVelocity)

                if (zone !is StressZone && stressZone.areNodesInZone(newPosition.plus(environment.getPosition(node)))) {
                    val randomMovement = getRandomMovement()
                    return rotateVector(environment.makePosition(randomMovement.lateralVelocity, randomMovement.forwardVelocity), getAngle(environment.getHeading(node)))
                }
                return rotateVector(newPosition, getAngle(environment.getHeading(node)))
            }
        }
        val movement = getRandomMovement()
        node.setConcentration(SimpleMolecule("x"), movement.lateralVelocity)
        node.setConcentration(SimpleMolecule("y"), movement.forwardVelocity)
        node.setConcentration(SimpleMolecule("zone"), "No zone")
        return rotateVector(environment.makePosition(movement.lateralVelocity, movement.forwardVelocity), getAngle(environment.getHeading(node)))
    }

    private fun getRandomMovement(): Movement {
        val randomNumber = Random.nextDouble()
        var cumulativeProbability = 0.0

        for (movement in movements.values) {
            cumulativeProbability += movement.probability
            if (randomNumber < cumulativeProbability) {
                return movement
            }
        }
        throw IllegalStateException("The sum of movement probabilities is not equal to 1")
    }

    private fun rotateVector(vector: Euclidean2DPosition, angle: Double): Euclidean2DPosition {
        val newX = vector.x * cos(angle) - vector.y * sin(angle)
        val newY = vector.x * sin(angle) + vector.y * cos(angle)
        node.setConcentration(SimpleMolecule("new x"), newX)
        node.setConcentration(SimpleMolecule("new y"), newY)
        return environment.makePosition(newX, newY)
    }
}
