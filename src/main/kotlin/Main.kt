package org.theiha

import org.theiha.ui.RectanglePanel
import javax.swing.JFrame

fun main() {
    val frame = JFrame("Click to test the R* tree")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.setSize(600, 400)
    frame.contentPane.add(RectanglePanel())
    frame.isVisible = true
}
