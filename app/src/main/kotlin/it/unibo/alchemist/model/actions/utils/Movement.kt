package it.unibo.alchemist.model.actions.utils

class Movement(val lateralVelocity: Double, val forwardVelocity: Double, val probability: Double = 1.0) {
    fun addVelocityModifier(lateralModifier: Double, forwardModifier: Double): Movement {
        return Movement(
            lateralVelocity + lateralVelocity * lateralModifier,
            forwardVelocity + forwardVelocity * forwardModifier,
        )
    }

    fun multiplyVelocity(factor: Double): Movement {
        return Movement(lateralVelocity * factor, forwardVelocity * factor)
    }

    override fun toString(): String {
        return "Movement(lateralVelocity=$lateralVelocity, forwardVelocity=$forwardVelocity, probability=$probability)"
    }
}
