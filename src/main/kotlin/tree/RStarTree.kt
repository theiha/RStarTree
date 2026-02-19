package org.theiha.tree

import org.theiha.tree.util.MinimumBoundingRectangle
import org.theiha.tree.util.Point
import kotlin.math.max

/**
 * Represents a 2 dimensional R*-tree, a spatial data structure used for indexing two-dimensional
 * objects.
 *
 * Useful resources:
 * - [Wikipedia: R*-tree](https://en.wikipedia.org/wiki/R*-tree)
 * - [R-tree (PDF)](https://dl.acm.org/doi/pdf/10.1145/971697.602266)
 * - [R*-tree (PDF)](https://infolab.usc.edu/csci599/Fall2001/paper/rstar-tree.pdf)
 *
 * @property maxEntriesPerNode The maximum number of entries a node can have before it needs to be
 *   split.
 * @property minEntriesPerNode The minimum number of entries a node must have to avoid underflow.
 * @author Riko Torun
 */
class RStarTree<V>(
    private val maxEntriesPerNode: Int = 5,
    private val minEntriesPerNode: Int = 1,
    private val pReinsert: Int = max(1, (0.35 * maxEntriesPerNode).toInt()),
) {
    private var root: RStarTreeNode<V>
    private var height: Int = 0

    init {
        require(minEntriesPerNode > 0) { "Minimum capacity must be 1" }
        require(maxEntriesPerNode >= minEntriesPerNode) {
            "Maximum capacity must be greater than or equal to minimum capacity"
        }
        require(minEntriesPerNode <= maxEntriesPerNode / 2) {
            "Minimum capacity must be less than or equal to Maximum capacity / 2"
        }

        root =
            RStarTreeNode(
                isLeaf = true,
                parent = null,
                maxEntries = maxEntriesPerNode,
                minEntries = minEntriesPerNode,
                level = 0,
            )
    }

    /**
     * Inserts an element at the appropriate position based on its minimum bounding rectangle.
     *
     * @param element The element to insert into the tree.
     */
    fun insert(element: V, boundary: MinimumBoundingRectangle) {
        val entry = RStarDataEntry(boundary, element)
        if (root.entries.isEmpty() && root.isLeaf) {
            root.addEntry(entry)
            return
        }

        val reinsertedOnLevel = mutableSetOf<Int>()
        insertRecursive(root, entry, 0, reinsertedOnLevel)
    }

    /**
     * Removes an element located by its minimum bounding rectangle. The element to remove must
     * match the given element exactly.
     *
     * @param element The element to remove.
     * @param boundary The minimum bounding rectangle of the element to remove.
     * @return `true` if the element was successfully removed, `false` if it was not found.
     */
    fun remove(element: V, boundary: MinimumBoundingRectangle): Boolean {
        val findResult =
            findLeaf(root, element, boundary) ?: return false // element not found in the tree

        val (leafNode, entryToRemove) = findResult

        // remove the entry from the leaf node.
        leafNode.removeEntry(entryToRemove)

        // condense the tree, handling potential underflow and fixing MBRs.
        condenseTree(leafNode)

        return true
    }

    /**
     * Searches for all elements whose minimum bounding rectangles
     * [intersect][MinimumBoundingRectangle.intersects] with the given rectangle.
     *
     * @param mbr The minimum bounding rectangle of the element to search around.
     */
    fun search(mbr: MinimumBoundingRectangle): List<V> {
        val results = mutableListOf<V>()
        searchRecursive(root, mbr, results)
        return results
    }

    /**
     * Searches for all elements whose minimum bounding rectangles
     * [intersect][MinimumBoundingRectangle.intersects] with the given points
     * [minimum bounding rectangle][MinimumBoundingRectangle].
     *
     * @param point The point to search around.
     */
    fun search(point: Point): List<V> {
        return search(MinimumBoundingRectangle.of(point))
    }

    /**
     * Searches for the k nearest neighbors of a given point. This method is not implemented yet.
     *
     * @param point The point to search around.
     * @param k The number of nearest neighbors to find.
     */
    fun searchKNearest(point: Point, k: Int): List<V> {
        TODO()
    }

    /**
     * Searches for the k nearest neighbors of a given
     * [minimum bounding rectangle][MinimumBoundingRectangle]. This method is not implemented yet.
     *
     * @param mbr The minimum bounding rectangle to search around.
     * @param k The number of nearest neighbors to find.
     */
    fun searchKNearest(mbr: MinimumBoundingRectangle, k: Int): List<V> {
        TODO()
    }

    /**
     * Recursively searches for all elements whose minimum bounding rectangles
     * [intersect][MinimumBoundingRectangle.intersects] with the given rectangle.
     *
     * @param node The current node to search in.
     * @param queryMBR The minimum bounding rectangle to search around.
     * @param results The list to store the found elements.
     */
    private fun searchRecursive(
        node: RStarTreeNode<V>,
        queryMBR: MinimumBoundingRectangle,
        results: MutableList<V>,
    ) {
        // no intersection with the node's MBR -> skip
        if (!node.minimumBoundingRectangle.intersects(queryMBR)) {
            return
        }

        // leaves hold data entries -> check them
        if (node.isLeaf) {
            for (entry in node.entries) {
                if (entry.boundary.intersects(queryMBR)) {
                    results.add((entry as RStarDataEntry<V>).data)
                }
            }
        } else { // internal node
            for (entry in node.entries) {
                // recursively call search on the child if its MBR intersects -> to get the leaves
                if (entry.boundary.intersects(queryMBR)) {
                    val branchEntry = entry as RStarBranchEntry<V>
                    searchRecursive(branchEntry.child, queryMBR, results)
                }
            }
        }
    }

    /**
     * Inserts an entry into the tree recursively, finding the appropriate node based on the minimum
     * bounding rectangle of the entry.
     *
     * @param node The current node to insert into or traverse through.
     * @param entry The [RStarDataEntry] or [RStarBranchEntry] to insert.
     * @param targetLevel The level at which the `entry` should reside (`0` for data entry,
     *   `node.nodeLevel + 1` for a branch entry being reinserted). For inserting a data entry a
     *   leaf (level `0`) should be targeted. A branch entry (a whole subtree), should be inserted
     *   at its original level.
     * @param reinsertedOnLevels Tracks levels where reinsertion has occurred for **this specific**
     *   insertion call (prevents infinite reinsertion loops).
     */
    private fun insertRecursive(
        node: RStarTreeNode<V>,
        entry: RStarTreeEntry<V>,
        targetLevel: Int,
        reinsertedOnLevels: MutableSet<Int>,
    ) {
        if (node.level == targetLevel) {
            // correct node level for insertion
            // entry is a branch -> child needs to update its parent pointer
            if (entry is RStarBranchEntry) {
                entry.child.parent = node
            }
            node.addEntry(entry)

            // check if readjustment is needed
            if (node.isOverflowing()) {
                overFlowTreatment(node, reinsertedOnLevels)
            } else { // even without overflow, MBR adjustment might be needed
                adjustTree(
                    node,
                    null,
                    reinsertedOnLevels,
                ) // null for siblingNode as no split happened at this exact step
            }
        } else { // go deeper into the tree
            check(!node.isLeaf) { "Reached a leaf before target level" }
            val chosenChildNode = chooseSubTree(node, entry.boundary)
            insertRecursive(chosenChildNode, entry, targetLevel, reinsertedOnLevels)
        }
    }

    /**
     * Finds the leaf node for a given element and its minimum bounding rectangle. The leaf node is
     * the node that contains the entry with the smallest enlargement of its minimum bounding
     * rectangle when the new entry is added.
     *
     * @param node The current node to examine (starts with root).
     * @param element The element to find the leaf for.
     * @param mbr The minimum bounding rectangle of the element.
     * @return A pair containing the lead node and the corresponding data entry, or null if not
     *   found.
     */
    private fun findLeaf(
        node: RStarTreeNode<V>,
        element: V,
        mbr: MinimumBoundingRectangle,
    ): Pair<RStarTreeNode<V>, RStarDataEntry<V>>? {
        if (node.isLeaf) {
            // check if the node contains the element
            for (entry in node.entries) {
                val dataEntry = entry as RStarDataEntry<V>
                if (dataEntry.boundary == mbr && dataEntry.data == element) {
                    return node to dataEntry
                }
            }
            return null // not found in this leaf
        }

        // internal node -> check children
        for (entry in node.entries) {
            // element to remove is in a child of this branch entry
            if (entry.boundary.intersects(mbr)) {
                val branchEntry = entry as RStarBranchEntry<V>
                val result =
                    findLeaf(branchEntry.child, element, mbr) // recursive search in the child
                if (result != null) {
                    return result // found in the child
                }
            }
        }
        return null // not found
    }

    /**
     * Condenses the tree after a deletion by re-inserting orphaned nodes (nodes that were removed
     * from the tree due to underflow) and adjusting their minimum bounding rectangles.
     */
    private fun condenseTree(leaf: RStarTreeNode<V>) {
        check(leaf.isLeaf) { "CondenseTree should start from a leaf node" }

        var currentNode = leaf
        val orphanedEntries = mutableListOf<RStarTreeEntry<V>>()

        while (currentNode !== root) {
            val parent = currentNode.parent ?: break // should not happen if not root, but safe
            val entryInParent = // get the entry in the parent that points to the current node
                parent.entries.find { (it as RStarBranchEntry).child === currentNode }!!

            // if underflow occurs, remove the entry from the parent and collect all entries
            if (currentNode.isUnderflow()) {
                parent.removeEntry(entryInParent)
                // node will be effectively deleted since not pointed to by the branch entry
                orphanedEntries.addAll(currentNode.entries)
            } else {
                // no underflow -> update bounding box in the parent's entry.
                val updatedEntry =
                    (entryInParent as RStarBranchEntry).copy(
                        boundary = currentNode.minimumBoundingRectangle
                    )
                parent.removeEntry(entryInParent)
                parent.addEntry(updatedEntry)
            }
            // go up
            currentNode = parent
        }

        // re-insert all orphaned entries from the root of the tree
        val reinsertedOnLevels = mutableSetOf<Int>()
        for (orphan in orphanedEntries) {
            when (orphan) {
                is RStarDataEntry -> insertRecursive(root, orphan, 0, reinsertedOnLevels)
                is RStarBranchEntry ->
                    insertRecursive(root, orphan, orphan.child.level + 1, reinsertedOnLevels)
            }
        }

        // check if the root has become a leaf node after all removals and reinsertions
        // if yes, the child becomes the new root
        if (!root.isLeaf && root.entries.size == 1) {
            val oldRoot = root
            root = (oldRoot.entries.first() as RStarBranchEntry).child // child is now the new root
            root.parent = null
            height-- // decrease height as we removed the old root
        }
    }

    /**
     * Finds the sub-tree node in which to insert a new entry with the given minimum bounding
     * rectangle.
     *
     * This method does not modify the tree.
     *
     * @param node The current node to examine (starts with root).
     * @param entryMBR The minimum bounding rectangle of the entry to insert.
     * @return The leaf node chosen for insertion.
     */
    private fun chooseSubTree(
        node: RStarTreeNode<V>,
        entryMBR: MinimumBoundingRectangle,
    ): RStarTreeNode<V> {
        if (node.isLeaf) {
            return node // found it
        }

        // no data inserted yet
        if (node.entries.isEmpty()) {
            return node
        }

        val childrenAreLeaves = (node.entries.first() as RStarBranchEntry<V>).child.isLeaf

        // case 1: children are leaves -> choose the entry with the least overlap enlargement
        // if they are equal, choose the one with the least area enlargement, then the smallest
        // area.
        if (childrenAreLeaves) {
            // initialize the "best" candidate with the first entry to avoid nulls
            val firstEntry = node.entries.first() as RStarBranchEntry<V>
            var bestChild: RStarTreeNode<V> = firstEntry.child
            var minAreaEnlargement = firstEntry.boundary.enlargementArea(entryMBR)
            var minArea = firstEntry.boundary.area()

            // calculate initial overlap for the first entry
            val enlargedMBRForFirst = firstEntry.boundary.union(entryMBR)
            var originalOverlapForFirst = 0.0
            var enlargedOverlapForFirst = 0.0
            for (otherEntry in node.entries) {
                if (firstEntry === otherEntry) continue
                originalOverlapForFirst += firstEntry.boundary.intersectionArea(otherEntry.boundary)
                enlargedOverlapForFirst += enlargedMBRForFirst.intersectionArea(otherEntry.boundary)
            }
            var minOverlapEnlargement = enlargedOverlapForFirst - originalOverlapForFirst

            for (i in 1 until node.entries.size) { // check the current node's entries
                val currentBranchEntry = node.entries[i] as RStarBranchEntry<V>
                val childMBR = currentBranchEntry.boundary
                val enlargedMBR = childMBR.union(entryMBR)

                // calculate overlap enlargement
                val areaEnlargement = enlargedMBR.area() - childMBR.area()
                val area = childMBR.area()
                var originalOverlap = 0.0
                var enlargedOverlap = 0.0
                for (otherEntry in node.entries) {
                    if (currentBranchEntry === otherEntry) continue
                    originalOverlap += childMBR.intersectionArea(otherEntry.boundary)
                    enlargedOverlap += enlargedMBR.intersectionArea(otherEntry.boundary)
                }
                val overlapEnlargement = enlargedOverlap - originalOverlap

                // compare with the current best
                if (overlapEnlargement < minOverlapEnlargement) {
                    minOverlapEnlargement = overlapEnlargement
                    minAreaEnlargement = areaEnlargement
                    minArea = area
                    bestChild = currentBranchEntry.child
                } else if (overlapEnlargement == minOverlapEnlargement) {
                    if (areaEnlargement < minAreaEnlargement) {
                        minAreaEnlargement = areaEnlargement
                        minArea = area
                        bestChild = currentBranchEntry.child
                    } else if (areaEnlargement == minAreaEnlargement) {
                        if (area < minArea) {
                            minArea = area
                            bestChild = currentBranchEntry.child
                        }
                    }
                }
            }
            return bestChild
        } else {
            // case 2: children are internal nodes. Choose entry with least area enlargement. If
            // they are equal, choose the one with the smallest area. (original R-tree algorithm)

            // initialize the "best" candidate with the first entry to avoid nulls
            val firstEntry = node.entries[0] as RStarBranchEntry<V>
            var bestChild = firstEntry.child
            var minEnlargement = firstEntry.boundary.enlargementArea(entryMBR)
            var minArea = firstEntry.boundary.area()

            for (i in 1 until node.entries.size) {
                val currentEntry = node.entries[i] as RStarBranchEntry<V>
                val enlargement = currentEntry.boundary.enlargementArea(entryMBR)
                val area = currentEntry.boundary.area()

                // compare with the current best
                if (enlargement < minEnlargement) {
                    minEnlargement = enlargement
                    minArea = area
                    bestChild = currentEntry.child
                } else if (enlargement == minEnlargement) {
                    if (area < minArea) {
                        minArea = area
                        bestChild = currentEntry.child
                    }
                }
            }
            return bestChild
        }
    }

    /**
     * Handles the overflow of a node by either reinserting entries or splitting the node and
     * adjusting the tree afterwards.
     *
     * @param node The overflowing node.
     * @param reinsertedOnLevels A set of levels where `overflowTreatment` has already been called.
     */
    private fun overFlowTreatment(node: RStarTreeNode<V>, reinsertedOnLevels: MutableSet<Int>) {
        if (node != root && !reinsertedOnLevels.contains(node.level)) {
            reinsertedOnLevels.add(node.level)
            reInsert(node, reinsertedOnLevels)
            return
        }
        val (newNode1, newNode2) = splitNode(node)
        adjustTree(newNode1, newNode2, reinsertedOnLevels)
    }

    /**
     * Re-inserts entries from an overflowing node into the tree. This is done by sorting the
     * entries based on their distance to the node's minimum bounding rectangle center, dividing
     * them into two parts, and re-inserting the first `pReinsert` entries into the tree.
     *
     * @param node The node to reinsert entries from.
     * @param reinsertedOnLevels Tracks levels where reinsertion has occurred for this specific
     *   insertion call (prevents infinite reinsertion loops).
     */
    private fun reInsert(node: RStarTreeNode<V>, reinsertedOnLevels: MutableSet<Int>) {
        val nodeMBR = node.minimumBoundingRectangle
        val sortedEntries: List<RStarTreeEntry<V>> =
            node.entries.sortedByDescending { it.boundary.distanceSquaredToCenter(nodeMBR) }

        // divide the entries into two parts
        val firstPEntries = sortedEntries.take(pReinsert)
        val remainingEntries = sortedEntries.drop(pReinsert)

        node.clearEntriesAndMBR() // clear the node's entries and MBR
        // rebuild the node with the remaining entries
        remainingEntries.forEach { node.addEntry(it) }

        // reinsert the first P entries into the tree

        for (entry in firstPEntries) {
            when (entry) {
                is RStarDataEntry -> {
                    insertRecursive(root, entry, 0, reinsertedOnLevels)
                }
                is RStarBranchEntry -> {
                    // branch entries must be re-inserted at their original level.
                    // the targeted level is the level of the node that will contain it,
                    // which is one level above the entry's child node.
                    insertRecursive(root, entry, entry.child.level + 1, reinsertedOnLevels)
                }
            }
        }
    }

    /**
     * Splits an overflowing node into two new nodes. The split is done by choosing the best axis
     * and index to minimize overlap and area enlargement.
     *
     * @param overflowingNode The node that is overflowing and needs to be split.
     * @return A pair of new nodes created from the split.
     */
    private fun splitNode(
        overflowingNode: RStarTreeNode<V>
    ): Pair<RStarTreeNode<V>, RStarTreeNode<V>> {
        val bestAxis = chooseSplitAxis(overflowingNode)
        val (splitIndex, sortedEntries) = chooseSplitIndex(overflowingNode, bestAxis)

        // perform the split of the entries into two new nodes
        val group1Entries = sortedEntries.subList(0, splitIndex)
        val group2Entries = sortedEntries.subList(splitIndex, sortedEntries.size)

        // only keep entries up to the split index in the overflowing node
        overflowingNode.clearEntriesAndMBR()
        group1Entries.forEach { overflowingNode.addEntry(it) }

        // create a new node for the second group of entries
        val newNode =
            RStarTreeNode(
                isLeaf = overflowingNode.isLeaf,
                parent = overflowingNode.parent,
                maxEntries = overflowingNode.maxEntries,
                minEntries = overflowingNode.minEntries,
                level = overflowingNode.level,
            )
        group2Entries.forEach { newNode.addEntry(it) }

        // if the overflowing node is not a leaf the parent pointers of the children are updated
        if (!overflowingNode.isLeaf) {
            group1Entries.forEach { (it as RStarBranchEntry).child.parent = overflowingNode }
            group2Entries.forEach { (it as RStarBranchEntry).child.parent = newNode }
        }

        return overflowingNode to newNode
    }

    /**
     * Determines the best axis (0 for X, 1 for Y) to split a node. The goal is to minimize the sum
     * of the margins (circumference) of the resulting nodes.
     *
     * @param node The node to split.
     * @return The axis index (0 for X, 1 for Y) that minimizes the margin sum.
     */
    private fun chooseSplitAxis(node: RStarTreeNode<V>): Int {
        val entries = node.entries
        var minMargin = Double.POSITIVE_INFINITY
        var bestAxis = -1

        // 2 dimensional
        for (axis in 0..1) {
            var totalMargin = 0.0

            // sort entries by lower value and calculate margin sum
            val sortedByLower =
                entries.sortedBy {
                    if (axis == 0) it.boundary.leftUpperCorner.x else it.boundary.leftUpperCorner.y
                }
            totalMargin += calculateMarginSum(sortedByLower)

            // sort entries by upper value and calculate margin sum
            val sortedByUpper =
                entries.sortedBy {
                    if (axis == 0) it.boundary.rightLowerCorner.x
                    else it.boundary.rightLowerCorner.y
                }
            totalMargin += calculateMarginSum(sortedByUpper)

            // if the total margin for this axis is less than the current minimum, update the best
            // axis
            if (totalMargin < minMargin) {
                minMargin = totalMargin
                bestAxis = axis
            }
        }
        return bestAxis
    }

    /**
     * Chooses the best index to split the entries in a node along a given axis.
     *
     * @param node The node to split.
     * @param axis The axis along which to split (0 for X, 1 for Y).
     * @return A pair containing the split index and the sorted list of entries.
     */
    private fun chooseSplitIndex(
        node: RStarTreeNode<V>,
        axis: Int,
    ): Pair<Int, List<RStarTreeEntry<V>>> {
        val entries = node.entries
        val m = minEntriesPerNode
        val M = maxEntriesPerNode

        var minOverlap = Double.POSITIVE_INFINITY
        var minArea = Double.POSITIVE_INFINITY
        var bestSplitIndex = -1
        var bestSortedList: List<RStarTreeEntry<V>>? = null

        // sort by lower and upper value to test both along the chosen axis
        val sortedByLower =
            entries.sortedBy {
                if (axis == 0) it.boundary.leftUpperCorner.x else it.boundary.leftUpperCorner.y
            }

        val sortedByUpper =
            entries.sortedBy {
                if (axis == 0) it.boundary.rightLowerCorner.x else it.boundary.rightLowerCorner.y
            }

        for (sortedList in listOf(sortedByLower, sortedByUpper)) {
            // see 4.2 in the paper
            for (k in 1..(M - 2 * m + 2)) {
                val splitIndex = m - 1 + k
                val group1 = sortedList.subList(0, splitIndex)
                val group2 = sortedList.subList(splitIndex, sortedList.size)

                val group1MBR = MinimumBoundingRectangle.of(group1.map { it.boundary })
                val group2MBR = MinimumBoundingRectangle.of(group2.map { it.boundary })

                val overlap = group1MBR.intersectionArea(group2MBR)
                val area = group1MBR.area() + group2MBR.area()

                // choose the split that minimizes overlap
                if (overlap < minOverlap) {
                    minOverlap = overlap
                    minArea = area
                    bestSplitIndex = splitIndex
                    bestSortedList = sortedList
                } else if (overlap == minOverlap && area < minArea) { // if tied prioritize area
                    minArea = area
                    bestSplitIndex = splitIndex
                    bestSortedList = sortedList
                }
            }
        }
        return bestSplitIndex to bestSortedList!!
    }

    /**
     * Calculates the sum of margins for all possible splits of the entries in the node. The margin
     * is the sum of the lengths of the edges (circumference) of a minimum bounding rectangle. This
     * method evaluates all valid distributions to find the margin sum for one sorted list.
     *
     * @param sortedEntries The list of entries sorted by the chosen axis.
     */
    private fun calculateMarginSum(sortedEntries: List<RStarTreeEntry<V>>): Double {
        var marginSum = 0.0
        val m = minEntriesPerNode
        val M = maxEntriesPerNode

        // see 4.2 in the paper
        for (k in 1..(M - 2 * m + 2)) {
            val splitIndex = m - 1 + k
            val group1 = sortedEntries.subList(0, splitIndex)
            val group2 = sortedEntries.subList(splitIndex, sortedEntries.size)

            // calculate the minimum bounding rectangles for both groups
            val group1MBR = MinimumBoundingRectangle.of(group1.map { it.boundary })
            val group2MBR = MinimumBoundingRectangle.of(group2.map { it.boundary })

            marginSum += group1MBR.circumference() + group2MBR.circumference()
        }
        return marginSum
    }

    /**
     * Adjusts the tree structure after a modification (e.g., insertion, split) by propagating
     * changes upwards in the tree until the root is reached or no further adjustments are needed.
     *
     * @param node The node that was modified (e.g., had an entry added, or is the first node from a
     *   split).
     * @param siblingNode The new sibling node if a split occurred, otherwise null.
     */
    private fun adjustTree(
        node: RStarTreeNode<V>,
        siblingNode: RStarTreeNode<V>?,
        reinsertedOnLevels: MutableSet<Int>,
    ) {
        var currentNode = node
        var newSiblingNode = siblingNode

        while (currentNode != root) {
            val parent = currentNode.parent ?: break // should not happen if not root, but safe

            // 1. update the parent's entry for the current node
            // mbr of the currentNode might have changed (e.g., after a re-insert or split)
            // -> find the entry in the parent that points to currentNode and update its boundary
            val entryInParent =
                parent.entries.find { (it as? RStarBranchEntry<V>)?.child === currentNode }
                    as? RStarBranchEntry<V>

            // update the actual entry in the parent if it exists
            if (entryInParent != null) {
                val updatedEntry =
                    entryInParent.copy(boundary = currentNode.minimumBoundingRectangle)
                parent.removeEntry(entryInParent)
                parent.addEntry(updatedEntry)
            }

            // if a sibling node was created, add it to the parent
            if (newSiblingNode != null) {
                val siblingEntry =
                    RStarBranchEntry(
                        boundary = newSiblingNode.minimumBoundingRectangle,
                        child = newSiblingNode,
                    )
                // Link the new sibling to its parent
                newSiblingNode.parent = parent
                parent.addEntry(siblingEntry)
            }

            // check if the parent is overflowing
            if (parent.isOverflowing()) {
                overFlowTreatment(parent, reinsertedOnLevels)
                // after overflow of the parent node, the tree might have changed significantly
                // (e.g. parent split) -> will be handled by the adjustTree() call that follows it
                return
            }

            // go up one level, sibling has been handled
            currentNode = parent
            newSiblingNode = null
        }

        // handle the root node separately
        if (newSiblingNode != null) {
            val oldRoot = root
            val newRoot =
                RStarTreeNode<V>(
                    isLeaf = false,
                    parent = null, // the new root has no parent
                    maxEntries = maxEntriesPerNode,
                    minEntries = minEntriesPerNode,
                    level = oldRoot.level + 1,
                )

            // the new root will have two children: the old root and its new sibling.
            val entryForOldRoot = RStarBranchEntry(oldRoot.minimumBoundingRectangle, oldRoot)
            val entryForNewSibling =
                RStarBranchEntry(newSiblingNode.minimumBoundingRectangle, newSiblingNode)

            newRoot.addEntry(entryForOldRoot)
            newRoot.addEntry(entryForNewSibling)

            // update parent pointers and the tree's root reference
            oldRoot.parent = newRoot
            newSiblingNode.parent = newRoot
            root = newRoot
            height++ // update the tree height
        }
    }

    /**
     * Returns a list of all minimum bounding rectangles (MBRs) in the R*-tree.
     *
     * **This method is not part of the R*-tree specification.**
     *
     * @return A list of all [MBRs][MinimumBoundingRectangle] in the tree.
     */
    fun getAllMBRs(): List<MinimumBoundingRectangle> {
        val mbrs = mutableListOf<MinimumBoundingRectangle>()
        collectMBRs(root, mbrs)
        return mbrs
    }

    /** Recursively collects all MBRs from the R*-tree nodes. */
    private fun collectMBRs(node: RStarTreeNode<V>, mbrs: MutableList<MinimumBoundingRectangle>) {
        mbrs.add(node.minimumBoundingRectangle)
        if (node.isLeaf) {
            return
        }

        for (entry in node.entries) {
            if (entry is RStarBranchEntry<V>) {
                collectMBRs(entry.child, mbrs)
            }
        }
    }
}
