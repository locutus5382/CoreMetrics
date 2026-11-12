package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale

/**
 * v0.8: offline, read-only interpretations. Source labels/points are immutable.
 * The number of times a core is busiest is NOT CPU residency or core time.
 */
object V08Analytics {
    data class CoreWinner(val core:Int,val count:Int,val sharePct:Double)
    data class CoreDistribution(val observations:Int,val ignored:Int,
        val winners:List<CoreWinner>)
    fun busiestCores(points:List<SessionStore.Point>):CoreDistribution {
        val counts=IntArray(8)
        var valid=0
        for(point in points) {
            val id=point.busiestCore?.takeIf{it.matches(Regex("cpu[0-7]"))}
                ?.removePrefix("cpu")?.toIntOrNull()
            if(id!=null) {counts[id]++;valid++}
        }
        return CoreDistribution(valid,points.size-valid,(0..7).map {
            CoreWinner(it,counts[it],if(valid>0)counts[it]*100.0/valid else 0.0)
        })
    }

    data class ObservedPhase(val startMs:Long,val endMs:Long,
        val sampleCount:Int,val signature:String,val description:String)
    data class Profile(val selected:String,val phases:List<ObservedPhase>,
        val conflict:Boolean,val note:String)

    fun profile(selected:String,points:List<SessionStore.Point>):Profile {
        val raw=points.sortedBy{it.timeMs}
        val phases=SessionRegimeAnalyzer.analyze(raw.map { p ->
            SessionRegimeAnalyzer.Sample(p.timeMs,p.observedCpuLimits,p.cpu,p.gpu,
                p.cpuLoad,p.busiestCorePct,p.gpuLoad,p.ram,
                p.batteryWatts,p.batteryPowerStatus)
        }).filterNot {it.key=="P3:?|P7:?"}
            .map{ObservedPhase(it.startMs,it.endMs,it.sampleCount,it.key,it.label)}
        val names=phases.map{it.description}
        val selectedLower=selected.lowercase(Locale.US)
        val mismatch=selectedLower.contains("stock") && names.any{
            it.startsWith("Large UC")||it.startsWith("Medium UC")
        }
        val note=when {
            phases.isEmpty() -> "No readable P3/P7 frequency limits"
            phases.size>1 -> "Multiple measured phases; do not reduce to one applied profile"
            else -> "Observed cpufreq ceilings; tuner attribution is not proven"
        }
        return Profile(selected,phases,mismatch,note)
    }

    data class Timeline(
        val startMs:Long,val endMs:Long,
        val firstStart:Long,val firstEnd:Long,
        val secondStart:Long,val secondEnd:Long,
        val gapMs:Long,val overlap:Boolean,
        val orderedIds:List<Long>
    )

    /** Absolute measured time. No resampling or implied continuity. */
    fun linkedTimeline(first:SessionStore.Session,pa:List<SessionStore.Point>,
        second:SessionStore.Session,pb:List<SessionStore.Point>):Timeline? {
        if(pa.isEmpty()||pb.isEmpty())return null
        val a=pa.sortedBy{it.timeMs}
        val b=pb.sortedBy{it.timeMs}
        val entries=listOf(Triple(first.id,a.first().timeMs,a.last().timeMs),
            Triple(second.id,b.first().timeMs,b.last().timeMs)).sortedBy{it.second}
        val left=entries[0];val right=entries[1]
        val gap=right.second-left.third
        return Timeline(left.second,maxOf(left.third,right.third),
            left.second,left.third,right.second,right.third,
            gap,gap<0L,listOf(left.first,right.first))
    }
}
