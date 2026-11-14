package com.locutus5382.coremetrics.monitor

/** Pure geometry. No Android APIs, screen overlays or device privileges.
 * Coordinate origin is upper-left of the usable screen. */
internal enum class DockEdge { LEFT, RIGHT, TOP }

internal object HudDockGeometry {
    fun place(edge:DockEdge,screenWidth:Int,screenHeight:Int,
        tabWidth:Int,tabHeight:Int,expandedX:Int,expandedY:Int):Pair<Int,Int> {
        val maxX=(screenWidth-tabWidth).coerceAtLeast(0)
        val maxY=(screenHeight-tabHeight).coerceAtLeast(0)
        return when(edge) {
            DockEdge.LEFT -> 0 to expandedY.coerceIn(0,maxY)
            DockEdge.RIGHT -> maxX to expandedY.coerceIn(0,maxY)
            DockEdge.TOP -> expandedX.coerceIn(0,maxX) to 0
        }
    }
}
