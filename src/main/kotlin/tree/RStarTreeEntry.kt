package org.theiha.tree

import org.theiha.tree.util.MinimumBoundingRectangle

/** Represents a generic entry in an [RStarTree]. */
internal interface RStarTreeEntry<V> {
    val boundary: MinimumBoundingRectangle
}

/**
 * Represents a data entry in an [RStarTree]. This entry contains actual data and its associated
 * boundary.
 *
 * @param V The type of data stored in this entry.
 * @property boundary The minimal bounding rectangle that contains the data. It is the boundary of
 *   the data object itself.
 * @property data The actual data object stored in this entry.
 */
internal data class RStarDataEntry<V>(
    override val boundary: MinimumBoundingRectangle,
    val data: V,
) : RStarTreeEntry<V>

/**
 * Represents an internal branch entry in an [RStarTree]. This entry contains child and its
 * boundary.
 *
 * @param V The type of data stored in the tree.
 * @property boundary The minimal bounding rectangle that contains the data. It is the boundary of
 *   the child itself.
 * @property child The single child [RStarTreeNode] this branch entry points to.
 */
internal data class RStarBranchEntry<V>(
    override val boundary: MinimumBoundingRectangle,
    val child: RStarTreeNode<V>,
) : RStarTreeEntry<V>
