package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import java.util.Locale

/**
 * v0.8: offline, read-only interpretations. Source labels/points are immutable.
 * The number of times a core is busiest is NOT CPU residency or core time.
 */
object V08Analytics {
    private val coreId=Regex("""cpu[0-7]""")

    /** All time filtering is non-destructive; relative to first MEASURED
     * point, not confirmed game loading or a manually linked recording. */
    fun afterWarmUp(points:List<SessionStore.Point>,seconds:Int):List<SessionStore.Point> {
        if(points.isEmpty() || seconds<=0)return points
        val first=points.minOf{it.timeMs}
        val threshold=first+seconds.coerceAtMost(3600)*1000L
        return points.filter{it.timeMs>=threshold}
    }

    /** Same read-only selected-vs-observed rule across Live, recorded
     * profiles, and benchmark quality. Generic Underclocking could mean
     * GPU-only tuning, so mismatches are review prompts, not test failures. */
    fun profileConflict(selected:String,labels:List<String>):Boolean {
        if(labels.isEmpty())return false
        val choice=selected.lowercase(Locale.US)
        return (choice.contains("stock") && labels.any{
            it.startsWith("Medium UC") || it.startsWith("Large UC")
        }) || (choice.contains("medium") && labels.any{
            it.startsWith("Large UC")
        }) || (choice.contains("large") && labels.any{
            it.startsWith("Medium UC")
        }) || (choice.contains("underclock") && labels.all{
            it.startsWith("Stock (")
        })
    }

    /** A single Live snapshot is valid for an observed-ceiling caution;
     * no 3-sample debounce or tuner identity is inferred here. */
    fun liveProfileWarning(selected:String,rawLimits:String?):String? {
        val label=SessionRegimeAnalyzer.label(SessionRegimeAnalyzer.key(rawLimits))
        if(!profileConflict(selected,listOf(label)))return null
        return if(selected.lowercase(Locale.US).contains("underclock"))
            "CHECK CPU LIMITS: Underclocking selected, but measured P3/P7 " +
            "ceilings are stock-like. GPU-only tuning may still be active."
        else "CHECK CPU LIMITS: Selected "+selected+"; observed "+label+
            ". CPU ceilings do not authenticate the applied tuner."
    }
    /** These are sample-based HUD comparisons, NOT time-weighted energy rates.
     * Never mix charging current into a discharge-power delta. */
    data class SampleAverages(val cpuC:Double?,val peakCpuC:Double?,
        val gpuC:Double?,val dischargeW:Double?)
    fun sampleAverages(points:List<SessionStore.Point>):SampleAverages {
        fun finiteMean(values:List<Double?>):Double? {
            val valid=values.filterNotNull().filter{it.isFinite()}
            return valid.takeIf{it.isNotEmpty()}?.average()
        }
        return SampleAverages(
            finiteMean(points.map{it.cpu}),
            points.mapNotNull{it.cpu?.takeIf(Double::isFinite)}.maxOrNull(),
            finiteMean(points.map{it.gpu}),
            finiteMean(points.map {
                it.batteryWatts?.takeIf { watts ->
                    watts.isFinite() && watts>0.1 &&
                        it.batteryPowerStatus?.startsWith("DISCHARGING")==true
                }
            })
        )
    }
    data class CoreWinner(val core:Int,val count:Int,val sharePct:Double)
    data class CoreDistribution(val observations:Int,val ignored:Int,
        val winners:List<CoreWinner>)
    fun busiestCores(points:List<SessionStore.Point>):CoreDistribution {
        val counts=IntArray(8)
        var valid=0
        for(point in points) {
            val id=point.busiestCore?.takeIf{it.matches(coreId)}
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
        // A generic "Underclocking" label may describe GPU-only tuning,
        // so this is a warning to CHECK the configured CPU caps, never proof
        // that a particular tuner failed or was disabled.
        val stockLikeOnly=names.isNotEmpty() && names.all {
            it.startsWith("Stock (")
        }
        val mismatch=profileConflict(selected,names)
        val note=when {
            phases.isEmpty() -> "No readable P3/P7 frequency limits"
            selectedLower.contains("underclock") && stockLikeOnly ->
                "Selected Underclocking but all readable P3/P7 ceilings are " +
                "stock-like. Check CPU tuning; GPU-only underclocking can " +
                "still be active. Tuner identity is NOT measured."
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
