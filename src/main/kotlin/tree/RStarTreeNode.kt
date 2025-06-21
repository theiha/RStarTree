package org.theiha.tree

import org.theiha.tree.util.MinimumBoundingRectangle

/**
 * Represents a node in an [RStarTree]. A node can be either a leaf node (containing
 * [RStarDataEntry] objects) or an internal (containing [RStarBranchEntry] objects pointing to child
 * nodes).
 *
 * @param V The type of data objects stored in the tree.
 * @property isLeaf True if this node is a leaf node, false otherwise.
 * @property parent The parent node, or null if this is the root.
 * @property _entries A list of [entries][RStarTreeEntry]. The specific type of entry depends if
 *   this is a leaf or an internal node.
 * @property minEntries The minimum number of entries this node must have to avoid underflow.
 * @property maxEntries The maximum number of entries this node can have before it needs to be
 *   split.
 * @property level The level of this node in the tree (leaves are level 0).
 * @property mbr The [MinimumBoundingRectangle] that spatially contains all entries in this node.
 * @author Riko Torun
 */
internal class RStarTreeNode<V>(
    internal val isLeaf: Boolean,
    internal var parent: RStarTreeNode<V>?,
    internal val maxEntries: Int,
    internal val minEntries: Int,
    private val _entries: MutableList<RStarTreeEntry<V>> = mutableListOf(),
    internal var level: Int = 0,
    private var mbr: MinimumBoundingRectangle = MinimumBoundingRectangle.empty,
) {

    /** The minimum bounding rectangle encompassing the space all entries in this node occupy */
    val minimumBoundingRectangle: MinimumBoundingRectangle
        get() = mbr

    /** The entries this node manages. */
    val entries: List<RStarTreeEntry<V>>
        get() = _entries.toList()

    init {
        require(minEntries > 0) { "Minimum capacity must be 1" }
        require(maxEntries >= minEntries) {
            "Maximum capacity must be greater than or equal to minimum capacity"
        }
        require(minEntries <= maxEntries / 2) {
            "Minimum capacity must be less than or equal to Maximum capacity / 2"
        }
        if (_entries.isNotEmpty()) {
            recalculateMBR()
        }
    }

    /**
     * Returns `true` if this node has `maxEntries + 1` entries and needs splitting/reinsertion,
     * `false` otherwise.
     */
    internal fun isOverflowing(): Boolean = _entries.size > maxEntries

    /**
     * Returns `true` if this node has fewer than `minEntries` entries **and** is **not** the root,
     * `false` otherwise.
     */
    internal fun isUnderflow(): Boolean = _entries.size < minEntries && parent != null

    /**
     * Adds an entry to this node and updates the minimum bounding rectangle.
     *
     * @param entry The entry to add to this node.
     */
    fun addEntry(entry: RStarTreeEntry<V>) {
        _entries.add(entry)
        mbr =
            if (_entries.size == 1) {
                entry.boundary
            } else {
                mbr.union(entry.boundary) // no complex recalculation needed for a single entry
            }
    }

    /**
     * Removes an entry from this node and updates the minimum bounding rectangle.
     *
     * @param entry The entry to remove from this node.
     * @return `true` if the entry was successfully removed, `false` otherwise.
     */
    fun removeEntry(entry: RStarTreeEntry<V>): Boolean {
        val removed = _entries.remove(entry)
        if (removed) {
            recalculateMBR()
        }
        return removed
    }

    /** Recalculates the minimum bounding rectangle for this node based on its current entries. */
    private fun recalculateMBR() {
        mbr = _entries.fold(MinimumBoundingRectangle.empty) { acc, e -> acc.union(e.boundary) }
    }

    /** Clears all entries in this node and resets the minimum bounding rectangle to empty. */
    internal fun clearEntriesAndMBR() {
        _entries.clear()
        mbr = MinimumBoundingRectangle.empty
    }
}
