package kyklab.humphreysbus.data

data class Building(
    val no: String,
    override val name: String,
    override val xCenter: Int,
    override val yCenter: Int
) : Spot(name, xCenter, yCenter)
