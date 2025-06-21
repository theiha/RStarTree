package org.theiha.ui

import java.awt.Color
import java.awt.Graphics
import kotlin.random.Random
import org.theiha.tree.util.MinimumBoundingRectangle
import org.theiha.tree.util.Point

interface Shape {
    var color: Color

    fun draw(g: Graphics)

    fun getMBR(): MinimumBoundingRectangle

    fun intersects(other: Shape): Boolean {
        val thisMBR = this.getMBR()
        val otherMBR = other.getMBR()
        return thisMBR.leftUpperCorner.x < otherMBR.leftUpperCorner.x + otherMBR.width &&
            thisMBR.leftUpperCorner.x + thisMBR.width > otherMBR.leftUpperCorner.x &&
            thisMBR.leftUpperCorner.y < otherMBR.leftUpperCorner.y + otherMBR.height &&
            thisMBR.leftUpperCorner.y + thisMBR.height > otherMBR.leftUpperCorner.y
    }

    fun intersects(x: Int, y: Int): Boolean {
        val point = Point(x.toDouble(), y.toDouble())
        return this.getMBR().contains(point)
    }
}

data class Rectangle(
    override var color: Color =
        Color(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
    val centerX: Int,
    val centerY: Int,
    val width: Int = 60,
    val height: Int = 40,
) : Shape {

    private val x: Int = centerX - width / 2
    private val y: Int = centerY - height / 2

    override fun draw(g: Graphics) {
        g.fillRect(x, y, width, height)
    }

    override fun getMBR(): MinimumBoundingRectangle {
        return MinimumBoundingRectangle(
            leftUpperCorner = Point(x.toDouble(), y.toDouble()),
            width = width.toDouble(),
            height = height.toDouble(),
        )
    }
}

data class Circle(
    override var color: Color =
        Color(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
    val centerX: Int,
    val centerY: Int,
    val radius: Int,
) : Shape {
    override fun draw(g: Graphics) {
        val topLeftX = centerX - radius
        val topLeftY = centerY - radius
        val diameter = radius * 2
        g.fillOval(topLeftX, topLeftY, diameter, diameter)
    }


    override fun getMBR(): MinimumBoundingRectangle {
        return MinimumBoundingRectangle(
            leftUpperCorner = Point((centerX - radius).toDouble(), (centerY - radius).toDouble()),
            width = (radius * 2).toDouble(),
            height = (radius * 2).toDouble(),
        )
    }
}

data class Triangle(
    override var color: Color =
        Color(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
    val centerX: Int,
    val centerY: Int,
    val size: Int = 50,
) : Shape {
    // top
    private val x1: Int = centerX
    private val y1: Int = centerY - (size / 2)

    // left
    private val x2: Int = centerX - (size / 2)
    private val y2: Int = centerY + (size / 2)

    // right
    private val x3: Int = centerX + (size / 2)
    private val y3: Int = centerY + (size / 2)

    override fun draw(g: Graphics) {
        g.fillPolygon(intArrayOf(x1, x2, x3), intArrayOf(y1, y2, y3), 3)
    }

    override fun getMBR(): MinimumBoundingRectangle {
        return MinimumBoundingRectangle(
            leftUpperCorner = Point(x2.toDouble(), y1.toDouble()),
            width = (x3 - x2).toDouble(),
            height = (y2 - y1).toDouble(),
        )
    }
}

fun createRandomShape(centerX: Int, centerY: Int): Shape {
    val shapeFactories: List<(centerX: Int, centerY: Int) -> Shape> =
        listOf(
            { cx, cy -> Rectangle(centerX = cx, centerY = cy) },
            { cx, cy -> Circle(centerX = cx, centerY = cy, radius = 15) },
            { cx, cy -> Triangle(centerX = cx, centerY = cy, size = 50) },
        )

    return shapeFactories.random()(centerX, centerY)
}
