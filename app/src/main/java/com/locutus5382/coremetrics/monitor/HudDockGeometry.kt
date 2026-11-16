package com.locutus5382.coremetrics.monitor

/** Pure geometry. No Android APIs, screen overlays or device privileges.
 * Coordinate origin is upper-left of the usable screen. */
internal enum class DockEdge { LEFT, RIGHT, TOP }

internal object HudDockGeometry {
    data class Parked(val x:Int,val y:Int,val anchoredRight:Boolean)

    /**
     * Parking is NOT collapse. Snap the expanded window close to a bezel,
     * including corners, with a small guaranteed inset. Pure JVM-testable
     * coordinates so a drag cannot accidentally change the HUD mode.
     */
    fun park(screenWidth:Int,screenHeight:Int,hudWidth:Int,hudHeight:Int,
        x:Int,y:Int,margin:Int,snapDistance:Int):Parked {
        require(margin>=0 && snapDistance>=0)
        val maxX=(screenWidth-hudWidth).coerceAtLeast(0)
        val maxY=(screenHeight-hudHeight).coerceAtLeast(0)
        val edge=margin.coerceAtMost(maxX)
        val right=(maxX-margin).coerceAtLeast(0)
        val cx=x.coerceIn(0,maxX)
        val cy=y.coerceIn(0,maxY)
        val leftSide=cx<=snapDistance
        val rightSide=cx>=maxX-snapDistance
        val parkedX=when {
            leftSide->edge
            rightSide->right
            else->cx
        }.coerceIn(0,maxX)
        val parkedY=if(cy<=snapDistance)
            margin.coerceAtMost(maxY) else cy
        return Parked(parkedX,parkedY,anchoredRight=rightSide && !leftSide)
    }

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
