package com.locutus5382.coremetrics.monitor

/**
 * Pure content model. Layouts are not just visual skins around an identical
 * block of telemetry: each has its own grouping, order and label density.
 * A null metric means disabled; an em-dash means enabled but unavailable.
 */
internal object HudReadoutFormatter {
    data class Values(
        val cpuTemp:String?=null,
        val gpuTemp:String?=null,
        val cpuUse:String?=null,
        val gpuUse:String?=null,
        val ram:String?=null,
        val power:String?=null,
        val batteryTemp:String?=null
    )
    data class Columns(
        val primary:String,
        val secondary:String="",
        val tertiary:String=""
    )
    private fun text(label:String,value:String?)=
        if(value==null)null else "$label $value"
    private fun join(vararg value:String?)=
        value.filterNotNull().joinToString("  |  ")
    private fun compact(label:String,value:String?)=
        if(value==null)null else "$label$value"
    private fun fixed(label:String,value:String?)=
        if(value==null)null else label.padEnd(7,' ')+value

    fun render(theme:String,v:Values,recording:Boolean):Columns {
        val layout=HudLayoutPolicy.normalize(theme)
        if(!recording)return when(layout) {
            HudLayoutPolicy.DUKO->Columns("STATUS  READY\nTAP REC TO START")
            HudLayoutPolicy.TACTICAL->Columns("STANDBY\nSTART RECORDING")
            HudLayoutPolicy.STRIKER->Columns("CM / STANDBY\nTAP REC")
            else->Columns("CM · READY  |  TAP REC")
        }
        return when(layout){
            HudLayoutPolicy.STRIKER->{
                val thermal=listOfNotNull(
                    text("CPU",v.cpuTemp),text("GPU",v.gpuTemp),
                    text("BAT",v.batteryTemp))
                val data=listOfNotNull(
                    text("CPU",v.cpuUse),text("GPU",v.gpuUse),
                    text("RAM",v.ram),text("PWR",v.power))
                Columns((listOf("CM / LIVE")+thermal).joinToString("\n"),
                    data.joinToString("\n"))
            }
            HudLayoutPolicy.GHOST->Columns(join(
                compact("♨ ",v.cpuTemp),
                compact("◇ ",v.gpuTemp),
                compact("⚡ ",v.cpuUse),
                compact("▸ ",v.gpuUse),
                compact("MEM ",v.ram),
                compact("▣ ",v.power),
                compact("BT ",v.batteryTemp)
            ))
            HudLayoutPolicy.STRIP->Columns(join(
                text("CPU T:",v.cpuTemp),text("GPU T:",v.gpuTemp),
                text("CPU %:",v.cpuUse),text("GPU %:",v.gpuUse),
                text("RAM:",v.ram),text("BAT:",v.power),
                text("BAT T:",v.batteryTemp)))
            HudLayoutPolicy.TACTICAL->{
                val thermo=join(text("CPU",v.cpuTemp),
                    text("GPU",v.gpuTemp),text("BAT",v.batteryTemp))
                val util=join(text("CPU",v.cpuUse),text("GPU",v.gpuUse))
                val resources=join(text("RAM",v.ram),text("PWR",v.power))
                Columns("THERMALS\n"+thermo.ifEmpty{"No sources"},
                    "UTILIZATION\n"+util.ifEmpty{"No sources"},
                    "RESOURCES\n"+resources.ifEmpty{"No sources"})
            }
            HudLayoutPolicy.DUKO->Columns(
                listOfNotNull(
                    fixed("T-CPU",v.cpuTemp),fixed("T-GPU",v.gpuTemp),
                    fixed("U-CPU",v.cpuUse),fixed("U-GPU",v.gpuUse),
                    fixed("MEM",v.ram),fixed("PWR",v.power),
                    fixed("T-BAT",v.batteryTemp)
                ).ifEmpty{listOf("NO METRICS SELECTED")}.joinToString("\n"))
            else->Columns("CM · READY")
        }
    }
}
