package it.unibo.experiment.clustering

import it.unibo.alchemist.model.Position2D
import it.unibo.alchemist.model.VisibleNode
import it.unibo.alchemist.model.actions.CameraSeeWithBlindSpot
import it.unibo.alchemist.model.physics.environments.Physics2DEnvironment
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.protelis.AlchemistExecutionContext
import it.unibo.alchemist.protelis.properties.ProtelisDevice
import it.unibo.experiment.toTargets
import org.protelis.lang.datatype.Field
import org.protelis.lang.datatype.Tuple
import org.protelis.lang.datatype.impl.ArrayTupleImpl
import smile.clustering.hclust
import java.util.stream.Collectors
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin


class HerdExperimentUtils {
    companion object {
        @JvmStatic
        fun getClusterSolver() = CameraClusterAssignmentProblemForProtelis()

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun findNonCollidingPosition(
            context: AlchemistExecutionContext<Euclidean2DPosition>,
            field: Field<*>,
            default: Tuple,
            targetPosition: Euclidean2DPosition,
            distance: Double,
        ): Tuple {
            val env = context.environmentAccess
            require(env is Physics2DEnvironment<Any>)
            val node = (context.deviceUID as ProtelisDevice<Euclidean2DPosition>).node
            val allNodes = field.stream()
                .filter { it.value == targetPosition }
                .map { (it.key as ProtelisDevice<Euclidean2DPosition>).node }
                .sorted()
                .collect(Collectors.toList())
            return if (allNodes.size <= 1) {
                default
            } else {
                val myPos = allNodes.indexOf(node)
                val plusAngle = (env.getPosition(allNodes.first()!!) - targetPosition).asAngle

                val offs = 2 * Math.PI / allNodes.size
                val targetAngle = offs * myPos + plusAngle
                (targetPosition + env.makePosition(cos(targetAngle) * distance, sin(targetAngle) * distance)).toTuple()
            }
        }

        /**
         * Return true if [cluster] centroid is visible by node owner of [context]
         */
        @JvmStatic
        fun isClusterCentroidVisible(context: AlchemistExecutionContext<Euclidean2DPosition>, cluster: Cluster): Boolean {
            return  isPointVisible(context, cluster.centroid)
        }

        @JvmStatic
        fun isPointVisible(value: Double): Boolean {
            return false
        }

        @JvmStatic
        fun isPointVisible(context: AlchemistExecutionContext<Euclidean2DPosition>, point: Tuple): Boolean {
            return isPointVisible(context, point.toPosition())
        }

        @JvmStatic
        fun isPointVisible(context: AlchemistExecutionContext<Euclidean2DPosition>, point: Euclidean2DPosition): Boolean {
            val env = context.environmentAccess
            require(env is Physics2DEnvironment<Any>)
            require(context.deviceUID is ProtelisDevice<*>)
            val node = (context.deviceUID as ProtelisDevice<*>).node
            return node.reactions
                .flatMap { it.actions }
                .filterIsInstance<CameraSeeWithBlindSpot>()
                .first().isVisible(point)
        }


        @JvmStatic
        fun getVisibleCentroids(context: AlchemistExecutionContext<Euclidean2DPosition>, clusters: List<Cluster>): List<Euclidean2DPosition> {
            return clusters.filter { isClusterCentroidVisible(context, it) }.map { it.centroid }
        }

        @JvmStatic
        fun getAssignedNodes(clusterAssignments: Map<String, Cluster>, clusters: List<Cluster>): Tuple {
            val ids = clusterAssignments.values.stream().map { m -> m.id}
            val list = mutableListOf<VisibleNode<*, Euclidean2DPosition>>()
            for(id in ids){
                list.addAll(clusters.filter { it.id == id }.map { it.nodes }.first())
            }
            return list.toTuple()
        }

        @JvmStatic
        fun getFakeCluster() = Cluster(-1, Euclidean2DPosition(0.0, 0.0), emptyList())

        @JvmStatic
        fun getEmptyNodesList() = emptyList<List<VisibleNode<*, Euclidean2DPosition>>>()

        @JvmStatic
        fun getClusters(targets: Tuple, clusteringLimit: Double): List<Cluster> {
            val clusters = groupByClusters(targets, clusteringLimit)
            val centroids = mutableListOf<Cluster>()
            val MPL = 4.0
            for (cluster in clusters.entries) {
                val c = cluster.value.foldRight(Euclidean2DPosition(0.0, 0.0)) { v, acc -> v.position + acc }
                val size = cluster.value.size
                val centroid = Euclidean2DPosition(round((c.x / size) / MPL) * MPL, round((c.y / size) / MPL) * MPL)
                centroids.add(Cluster(cluster.key, centroid, cluster.value))
            }
            return centroids
        }

        @JvmStatic
        private fun groupByClusters(targets: Tuple, clusteringLimit: Double): Map<Int, List<VisibleNode<*, Euclidean2DPosition>>> {
            val nodes = targets.toTargets<Euclidean2DPosition>()
            if (nodes.isEmpty()) {
                return emptyMap()
            } else if (nodes.size == 1) {
                return mapOf(0 to nodes)
            }
            val data = nodes.map { it.position.coordinates }.toTypedArray()
            val c = hclust(data, "ward") // wpgma
            val labels = if (c.height().last() <= clusteringLimit) {
                IntArray(data.size) { 0 }
            } else {
                c.partition(clusteringLimit)
            }

            val groupedData = nodes.zip(labels.toTypedArray()).groupBy { it.second }

            val result = mutableMapOf<Int, List<VisibleNode<*, Euclidean2DPosition>>>()

            groupedData.forEach { (key, pairs) ->
                result[key] = pairs.map { it.first }
            }
            return result
        }

        /**
         * Returns the elements in the [field] appearing for the least number of devices or an empty [Tuple] if the field is empty.
         * The [field] is supposed to contain a tuple of elements for each device.
         */
        @JvmStatic
        fun clustersWithLeastSources(field: Field<Tuple>): Tuple {
            val counts = mutableMapOf<Any, Int>()
            field.values().forEach { tuple ->
                tuple.forEach {
                    counts.merge(it, 1) { v1, v2 -> v1 + v2 }
                }
            }
            val min = if (counts.values.isEmpty()) Int.MAX_VALUE else counts.values.min()
            return counts.filterValues { it <= min }.keys.toTuple()
        }
    }
}

private fun <P : Position2D<P>> Position2D<P>.toTuple(): Tuple = ArrayTupleImpl(x, y)
private fun Collection<*>.toTuple(): Tuple = with(iterator()) { ArrayTupleImpl(*Array(size) { next() }) }
private fun Tuple.toPosition(): Euclidean2DPosition {
    require(size() == 2)
    val x = get(0)
    val y = get(1)
    require(x is Double && y is Double)
    return Euclidean2DPosition(x, y)
}