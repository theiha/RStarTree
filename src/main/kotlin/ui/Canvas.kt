package org.theiha.ui

import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.JPanel
import javax.swing.SwingUtilities
import org.theiha.tree.RStarTree
import org.theiha.tree.util.Point

class RectanglePanel : JPanel() {
    private val shapes = mutableMapOf<String, Shape>()
    private val index = RStarTree<String>()

    init {
        addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        val shape = createRandomShape(e.x, e.y)
                        val id = UUID.randomUUID().toString()
                        shapes[id] = shape
                        index.insert(id, shape.getMBR())
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        val possibleShapes = index.search(Point(e.x, e.y))
                        possibleShapes
                            .filter { id -> shapes[id]?.intersects(e.x, e.y) == true }
                            .forEach { id ->
                                val mbr = shapes[id]?.getMBR() ?: return@forEach
                                shapes.remove(id)
                                index.remove(id, mbr)
                            }
                    }
                    repaint()
                }
            }
        )
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val mbrSize = drawRStarTree(g, index)

        val lines = listOf(
            "Click to add a shape",
            "Right-click to remove a shape",
            "R* Tree with $mbrSize rectangles"
        )

        var y = 20
        for (line in lines) {
            g.drawString(line, 10, y)
            y += g.fontMetrics.height
        }

        shapes.forEach { (_, shape) ->
            g.color = shape.color
            shape.draw(g)
        }
    }
}

fun drawRStarTree(g: Graphics, tree: RStarTree<*>): Int {
    val mbrs = tree.getAllMBRs()


    mbrs.forEach { mbr ->
        g.drawRect(
            mbr.leftUpperCorner.x.toInt(),
            mbr.leftUpperCorner.y.toInt(),
            mbr.width.toInt(),
            mbr.height.toInt(),
        )
    }
    return mbrs.size
}
