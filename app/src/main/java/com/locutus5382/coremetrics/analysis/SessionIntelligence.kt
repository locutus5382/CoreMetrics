package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import kotlin.math.max

/**
 * Offline, deterministic interpretation. All times refer to actual samples,
 * NEVER to game frames, game threads or proven causal effects.
 */
object SessionIntelligence {
    enum class Kind { CAP_CHANGED, CORE_ACTIVITY_SHIFT, ACTIVITY_SHIFT,
        SATURATION, OS_THERMAL_PRESSURE, MEMORY_PRESSURE, RECORDING_GAP }
    enum class Evidence { OBSERVED, PATTERN }
    data class Event(val offsetMs:Long,val kind:Kind,val evidence:Evidence,
        val title:String,val detail:String)
    enum class Activity { HIGHER, LOWER, UNKNOWN }
    data class Period(val startMs:Long,val endMs:Long,val activity:Activity,
        val samples:Int,val description:String)
    data class Result(val events:List<Event>,val periods:List<Period>,
        val cadenceMs:Long?,val usableSamples:Int)

    private fun core(value:String?):String? =
        value?.trim()?.takeIf {
            it.matches(Regex("cpu[0-9]{1,2}",RegexOption.IGNORE_CASE))
        }?.lowercase()

    fun analyze(input:List<SessionStore.Point>):Result {
        val points=input.sortedBy { it.timeMs }.distinctBy { it.timeMs }
        if(points.size<3)return Result(emptyList(),emptyList(),null,points.size)
        val start=points.first().timeMs
        val intervals=points.zipWithNext{a,b->b.timeMs-a.timeMs}
            .filter{it>0L && it<30_000L}.sorted()
        val cadence=intervals.getOrNull(intervals.size/2) ?: 5_000L
        val gapLimit=max(6_000L,cadence*2L)
        val events=mutableListOf<Event>()
        fun emit(at:Long,kind:Kind,evidence:Evidence,title:String,detail:String) {
            events+=Event((at-start).coerceAtLeast(0L),kind,evidence,title,detail)
        }
        for(i in 1 until points.size) {
            val gap=points[i].timeMs-points[i-1].timeMs
            if(gap>gapLimit)emit(points[i].timeMs,Kind.RECORDING_GAP,
                Evidence.OBSERVED,"Recording gap",
                "The preceding sample interval was "+gap+" ms; its contents are unknown.")
        }

        // Confirm at least 3 consecutive known P3/P7 limits before announcing
        // a regime change. Do not guess ClusterTune presets or causes.
        val caps=points.map {
            SessionRegimeAnalyzer.key(it.observedCpuLimits)
                .takeUnless { key->key.contains("?") }
        }
        var lastCap:String?=null
        for(i in 0..points.size-3) {
            val cap=caps[i] ?:continue
            if(cap==lastCap || caps[i+1]!=cap || caps[i+2]!=cap)continue
            if(points[i+2].timeMs-points[i].timeMs>gapLimit*2L)continue
            if(lastCap!=null && i>0 &&
                points[i].timeMs-points[i-1].timeMs<=gapLimit)
                emit(points[i].timeMs,Kind.CAP_CHANGED,Evidence.OBSERVED,
                    "Observed CPU frequency ceilings changed",
                    SessionRegimeAnalyzer.label(lastCap)+" → "+
                    SessionRegimeAnalyzer.label(cap)+
                    ". Which application or thermal policy caused this is unknown.")
            lastCap=cap
        }

        // A rolling 6+6 observation of system-wide busiest-core identity.
        // This is NOT per-game thread affinity or a game-thread migration.
        var lastShift=-12
        for(i in 6 until points.size-5) {
            if(i-lastShift<6)continue
            val window=points.subList(i-6,i+6)
            if(window.zipWithNext().any{(a,b)->b.timeMs-a.timeMs>gapLimit})continue
            fun winner(half:List<SessionStore.Point>):String? {
                val valid=half.mapNotNull{core(it.busiestCore)}
                if(valid.size<5)return null
                return valid.groupingBy{it}.eachCount().maxByOrNull{it.value}
                    ?.takeIf{it.value>=4}?.key
            }
            val previous=winner(window.take(6))
            val following=winner(window.takeLast(6))
            if(previous!=null && following!=null && previous!=following) {
                emit(points[i].timeMs,Kind.CORE_ACTIVITY_SHIFT,Evidence.PATTERN,
                    "Busiest system CPU core changed",
                    previous+" → "+following+
                    " across sampled system CPU activity; game-thread migration is unverified.")
                lastShift=i
            }
        }

        // Relative within-session GPU-activity segments. Do not label them
        // as gameplay/menu; different scenes can exhibit the same load.
        val gpu=points.mapNotNull{it.gpuLoad?.takeIf{v->v.isFinite()&&v in 0.0..100.0}}
        val periods=mutableListOf<Period>()
        if(gpu.size>=max(8,points.size/2)) {
            val sorted=gpu.sorted()
            val q1=sorted[sorted.size/4]
            val q3=sorted[sorted.size*3/4]
            if(q3-q1>=12.0) {
                val threshold=(q3+q1)/2.0
                val states=points.map {
                    it.gpuLoad?.takeIf{v->v.isFinite()&&v in 0.0..100.0}
                        ?.let{v->if(v>=threshold)Activity.HIGHER else Activity.LOWER}
                        ?: Activity.UNKNOWN
                }
                var i=0
                while(i<points.size) {
                    var j=i+1
                    while(j<points.size && states[j]==states[i] &&
                        points[j].timeMs-points[j-1].timeMs<=gapLimit)j++
                    if(j-i>=3 && states[i]!=Activity.UNKNOWN) {
                        val label=if(states[i]==Activity.HIGHER)
                            "Higher relative GPU activity" else "Lower relative GPU activity"
                        periods+=Period(points[i].timeMs-start,
                            points[j-1].timeMs-start,states[i],j-i,
                            label+" (not a confirmed game scene)")
                    }
                    i=j
                }
                for(k in 1 until periods.size) {
                    val before=periods[k-1]
                    val after=periods[k]
                    if(before.activity!=after.activity &&
                        after.startMs-before.endMs<=gapLimit*3L)
                        emit(start+after.startMs,Kind.ACTIVITY_SHIFT,
                            Evidence.PATTERN,"GPU activity pattern shifted",
                            "Relative activity changed; this is not proof of a race/menu boundary.")
                }
            }
        }

        // >=95% utilization in >=95% of valid samples over a real 120s
        // continuous time window; >=90% expected data coverage. Warning
        // is descriptive, not proof of stutter, throttling or a bottleneck.
        val sources=listOf<Pair<String,(SessionStore.Point)->Double?>>(
            "GPU" to {it.gpuLoad}, "Aggregate CPU" to {it.cpuLoad},
            "Busiest system CPU core" to {it.busiestCorePct}
        )
        for((name,read) in sources) {
            var lastEnd=Long.MIN_VALUE
            for(i in points.indices) {
                val first=points[i].timeMs
                var j=i
                while(j<points.size && points[j].timeMs-first<120_000L)j++
                if(j>=points.size || points[j].timeMs-first>120_000L+cadence)continue
                val slice=points.subList(i,j+1)
                if(slice.zipWithNext().any{(a,b)->
                    b.timeMs-a.timeMs>max(6_000L,cadence*3L/2L)})continue
                val expected=max(2,(120_000L/cadence).toInt())
                if(slice.size<expected*9/10)continue
                val valid=slice.mapNotNull{read(it)?.takeIf{v->
                    v.isFinite() && v in 0.0..100.0}}
                if(valid.size<expected*9/10)continue
                val saturated=valid.count{it>=95.0}
                if(saturated.toDouble()/valid.size<0.95 ||
                    first<lastEnd)continue
                emit(first,Kind.SATURATION,Evidence.PATTERN,
                    name+" sustained high utilization",
                    "At least 95% utilization in "+saturated+"/"+valid.size+
                    " valid samples over 120s; no game frame timing measured.")
                lastEnd=points[j].timeMs
            }
        }

        var previousStatus=0
        var previousPsiHigh=false
        var lastPsiEvent=Long.MIN_VALUE
        for(p in points) {
            val status=p.thermalStatus?.takeIf{it in 0..6}
            if(status!=null && status>=1 && previousStatus==0)
                emit(p.timeMs,Kind.OS_THERMAL_PRESSURE,Evidence.OBSERVED,
                    "Android device thermal pressure reported",
                    "Android thermal status "+status+
                    " is device-wide, not CPU/GPU-specific throttling proof.")
            if(status!=null)previousStatus=if(status>=1)1 else 0
            val psi=p.memoryPsiSomeAvg10?.takeIf{it.isFinite()&&it in 0.0..100.0}
            val high=psi!=null && psi>=10.0
            if(high && previousPsiHigh &&
                (lastPsiEvent==Long.MIN_VALUE || p.timeMs-lastPsiEvent>=60_000L)) {
                emit(p.timeMs,Kind.MEMORY_PRESSURE,Evidence.OBSERVED,
                    "System memory pressure observed",
                    "Two successive memory PSI some avg10 readings reached 10% or more. "+
                    "This cannot be attributed specifically to the game.")
                lastPsiEvent=p.timeMs
            }
            previousPsiHigh=high
        }
        return Result(events.sortedBy{it.offsetMs},periods,cadence,points.size)
    }
}
