package org.theiha.tree.util

import kotlin.math.max
import kotlin.math.min

/**
 * A `MinimumBoundingRectangle` represents a rectangular partition of 2D space. Typically such
 * rectangles are used to define boundaries in data structures used in spatial partitioning
 * algorithms like [R* Trees][org.theiha.tree.RStarTree].
 *
 * @property leftUpperCorner The left upper corner of the rectangle.
 * @property width The width of the rectangle.
 * @property height The height of the rectangle.
 * @author Riko Torun
 */
data class MinimumBoundingRectangle(
    internal val leftUpperCorner: Point,
    val width: Double,
    val height: Double,
) {

    internal val rightLowerCorner: Point =
        Point(leftUpperCorner.x + width, leftUpperCorner.y + height)

    /** Computes the area of the rectangle. */
    fun area(): Double {
        return width * height
    }

    /**
     * Checks if the rectangle contains a given point. Inclusive of edges.
     *
     * @param point The point to check for containment.
     * @return `true` if the point is contained within the rectangle, `false` otherwise.
     */
    fun contains(point: Point): Boolean {
        return point.x >= leftUpperCorner.x &&
            point.x <= rightLowerCorner.x &&
            point.y >= leftUpperCorner.y &&
            point.y <= rightLowerCorner.y
    }

    /**
     * Checks if this rectangle contains another rectangle. Inclusive of edges.
     *
     * @param other The rectangle to check for containment.
     * @return `true` if this rectangle contains the other rectangle, `false` otherwise.
     */
    fun contains(other: MinimumBoundingRectangle): Boolean {
        return this.leftUpperCorner.x <= other.leftUpperCorner.x &&
            this.rightLowerCorner.x >= other.rightLowerCorner.x &&
            this.leftUpperCorner.y <= other.leftUpperCorner.y &&
            this.rightLowerCorner.y >= other.rightLowerCorner.y
    }

    /**
     * Checks if this rectangle intersects with another rectangle. Inclusive of edges.
     *
     * @param other The rectangle to check for intersection.
     * @return `true` if the rectangles intersect, `false` otherwise.
     */
    fun intersects(other: MinimumBoundingRectangle): Boolean {
        return this.leftUpperCorner.x <= other.rightLowerCorner.x &&
            this.rightLowerCorner.x >= other.leftUpperCorner.x &&
            this.leftUpperCorner.y <= other.rightLowerCorner.y &&
            this.rightLowerCorner.y >= other.leftUpperCorner.y
    }

    /** Computes the circumference of the rectangle. */
    fun circumference(): Double {
        return 2 * (width + height)
    }

    /**
     * Merges this rectangle with another rectangle to create a new rectangle that encompasses both.
     * The new rectangle will have the smallest area that contains both rectangles.
     */
    fun union(other: MinimumBoundingRectangle): MinimumBoundingRectangle {
        // If either rectangle is empty (width = 0 and height = 0), return the other rectangle.
        if (this.width == 0.0 && this.height == 0.0) {
            return other
        }
        if (other.width == 0.0 && other.height == 0.0) {
            return this
        }

        val left = minOf(leftUpperCorner.x, other.leftUpperCorner.x)
        val right = maxOf(rightLowerCorner.x, other.rightLowerCorner.x)
        val top = minOf(leftUpperCorner.y, other.leftUpperCorner.y)
        val bottom = maxOf(rightLowerCorner.y, other.rightLowerCorner.y)

        return MinimumBoundingRectangle(Point(left, top), right - left, bottom - top)
    }

    /**
     * Computes the area of enlargement required to include another rectangle.
     *
     * @param other The rectangle to be included.
     * @return The area of enlargement needed to include the other rectangle.
     */
    fun enlargementArea(other: MinimumBoundingRectangle): Double {
        val unionRect = union(other)
        return unionRect.area() - area()
    }

    /**
     * Computes the area of intersection with another rectangle.
     *
     * @param other The rectangle to intersect with.
     * @return The area of the overlapping region, or 0.0 if they do not intersect.
     */
    fun intersectionArea(other: MinimumBoundingRectangle): Double {
        val xOverlap =
            max(
                0.0,
                min(this.rightLowerCorner.x, other.rightLowerCorner.x) -
                    max(this.leftUpperCorner.x, other.leftUpperCorner.x),
            )

        val yOverlap =
            max(
                0.0,
                min(this.rightLowerCorner.y, other.rightLowerCorner.y) -
                    max(this.leftUpperCorner.y, other.leftUpperCorner.y),
            )

        return xOverlap * yOverlap
    }

    /**
     * Computes the squared distance from the center of this rectangle to the center of another
     * rectangle.
     *
     * @param other The other rectangle to compute the distance to.
     * @return The squared distance between the centers of the two rectangles.
     */
    fun distanceSquaredToCenter(other: MinimumBoundingRectangle): Double {
        val centerX = (leftUpperCorner.x + rightLowerCorner.x) / 2
        val centerY = (leftUpperCorner.y + rightLowerCorner.y) / 2
        val otherCenterX = (other.leftUpperCorner.x + other.rightLowerCorner.x) / 2
        val otherCenterY = (other.leftUpperCorner.y + other.rightLowerCorner.y) / 2

        val dx = centerX - otherCenterX
        val dy = centerY - otherCenterY

        return (dx * dx) + (dy * dy)
    }

    companion object {
        /** An empty `MinimumBoundingRectangle` (width = 0, height = 0) positioned at the origin */
        val empty: MinimumBoundingRectangle by lazy {
            MinimumBoundingRectangle(Point(0.0, 0.0), 0.0, 0.0)
        }

        /** * A 1x1 `MinimumBoundingRectangle` (width = 1, height = 1) positioned at the origin */
        val unitSquare: MinimumBoundingRectangle by lazy {
            MinimumBoundingRectangle(Point(0.0, 0.0), 1.0, 1.0)
        }

        /** Creates a `MinimumBoundingRectangle` encompassing all rectangles in the vararg. */
        fun of(vararg multiple: MinimumBoundingRectangle): MinimumBoundingRectangle {
            require(multiple.isNotEmpty()) {
                "At least one rectangle is required to create a union."
            }
            return multiple.reduce { acc, rect -> acc.union(rect) }
        }

        /** Creates a `MinimumBoundingRectangle` encompassing all rectangles in the collection. */
        fun of(multiple: Collection<MinimumBoundingRectangle>): MinimumBoundingRectangle {
            require(multiple.isNotEmpty()) {
                "At least one rectangle is required to create a union."
            }
            return multiple.reduce { acc, rect -> acc.union(rect) }
        }

        /**
         * Creates a `MinimumBoundingRectangle` from a point and dimensions. The given point will be
         * the center of the rectangle.
         */
        fun of(point: Point, size: Double = 0.001): MinimumBoundingRectangle {
            val halfSize = size / 2.0
            val newLeftUpperCorner = Point(point.x - halfSize, point.y - halfSize)
            return MinimumBoundingRectangle(newLeftUpperCorner, size, size)
        }
    }
}
