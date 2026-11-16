package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import kotlin.math.max

/**
 * Small repeated A/A experiment summary. This is a run-level descriptive
 * comparison, NOT frame-rate performance, thermal cause, statistical
 * significance, or proof that the game scenes were equivalent.
 *
 * All samples of each run are used. No pseudo-replication: 1000 sensor
 * samples from one run still count as ONE experimental replicate.
 */
object RepeatabilityAnalyzer {
    data class Run(val session:SessionStore.Session,val points:List<SessionStore.Point>)
    data class Metric(
        val label:String,val unit:String,val medianOfRuns:Double?,
        val minimumRun:Double?,val maximumRun:Double?,val runCount:Int
    ) {
        val spread:Double? get()=if(minimumRun!=null && maximumRun!=null)
            maximumRun-minimumRun else null
    }
    data class Result(
        val profile:String,val runIds:List<Long>,val measurements:List<Metric>,
        val cautions:List<String>,val complete:Boolean,
        val eligiblePowerRuns:Int
    )

    private fun median(values:List<Double>):Double? {
        val sorted=values.filter{it.isFinite()}.sorted()
        if(sorted.isEmpty())return null
        val i=sorted.size/2
        return if(sorted.size%2==1)sorted[i]
            else (sorted[i-1]+sorted[i])/2.0
    }
    private fun metric(label:String,unit:String,values:List<Double?>):Metric {
        val valid=values.filterNotNull().filter { it.isFinite() }.sorted()
        return Metric(label,unit,median(valid),
            valid.firstOrNull(),valid.lastOrNull(),valid.size)
    }

    /** Strict metadata equality; missing tags remain cautions, not
     * guessed identical settings. Profile names are user-entered snapshots.
     */
    fun analyze(runs:List<Run>,skipFirstSeconds:Int=30):Result {
        require(runs.size in 2..5) { "Select two to five completed runs" }
        require(runs.map{it.session.id}.distinct().size==runs.size) {
            "Each selected run must be different"
        }
        require(skipFirstSeconds in 0..600) { "Invalid warm-up exclusion" }
        val profiles=runs.map{it.session.profileName?.trim()}
        val profile=profiles.firstOrNull()?.takeIf{it.isNotBlank()}
            ?:error("Saved Quick Test Profile labels are required for repeatability")
        require(profiles.all{it==profile}) {
            "All repeats must share the same named Quick Test Profile"
        }
        val cautions=mutableListOf<String>()
        val first=runs.first().session
        val metadata=listOf(
            "Graphics driver" to runs.map{it.session.graphicsDriver},
            "Resolution/scale" to runs.map{it.session.resolutionScale},
            "Odin performance" to runs.map{it.session.performanceMode},
            "Fan setting" to runs.map{it.session.fanMode},
            "ClusterTune" to runs.map{it.session.clusterTuneMode},
            "Game/track note" to runs.map{it.session.note}
        )
        for((name,values) in metadata) {
            if(values.any{it.isNullOrBlank()})
                cautions+=name+" is not documented for every run"
            else if(values.distinct().size>1)
                cautions+=name+" differs between repetitions; these are not matched settings"
        }
        val full=runs.all{
            it.points.size>=it.session.count && it.session.count>=2
        }
        require(full){
            "At least one run is incomplete in the 12,000-sample preview. "+
            "Never silently rank or summarize truncated recordings."
        }
        val reports=runs.map {run->
            val points=run.points.sortedBy{it.timeMs}
            BenchmarkAnalyzer.analyze(run.session,points,skipFirstSeconds)
        }
        for((i,run) in runs.withIndex()) {
            val report=reports[i]
            if(run.session.status=="RECORDING" ||
                run.session.status.contains("INTERRUPTED") ||
                run.session.status.contains("FAILED"))
                cautions+="Session #"+run.session.id+
                    " is "+run.session.status+" (not a clean completed repeat)"
            if(report.elapsedSeconds<60.0)
                cautions+="Session #"+run.session.id+
                    " has less than 60 seconds after exclusion"
            if(report.quality.score<65)
                cautions+="Session #"+run.session.id+
                    " has reduced recording-quality evidence"
        }
        fun runMedian(i:Int,selector:(SessionStore.Point)->Double?):Double? {
            val samples=runs[i].points.sortedBy{it.timeMs}
            val firstTime=samples.firstOrNull()?.timeMs ?:return null
            val after=samples.filter{
                it.timeMs-firstTime>=skipFirstSeconds*1000L
            }
            return median(after.mapNotNull(selector))
        }
        val cpu=runs.indices.map{i->runMedian(i){it.cpu}}
        val gpu=runs.indices.map{i->runMedian(i){it.gpu}}
        val gpuLoad=runs.indices.map{i->runMedian(i){it.gpuLoad}}
        val discharge=reports.map{report->
            if(report.energy.powerCondition==BenchmarkAnalyzer.PowerCondition.DISCHARGING &&
                report.energy.coveragePct>=80.0 && report.energy.measuredSeconds>=60.0)
                report.energy.weightedMeanW else null
        }
        val eligible=discharge.count{it!=null}
        if(eligible<runs.size)
            cautions+="Some runs lack >=60s and >=80% valid discharging "+
                "coverage; unavailable watts are excluded, not zero."
        if(runs.map{it.session.configuration}.distinct().size>1)
            cautions+="General recording configuration differs"
        cautions+="Matching manually entered tags do not prove identical "+
            "game scenes, emulator settings or thermal starting conditions."
        return Result(profile,runs.map{it.session.id},
            listOf(metric("CPU sensor median","°C",cpu),
                metric("GPU sensor median","°C",gpu),
                metric("GPU utilization median","%",gpuLoad),
                metric("Battery terminal power","W",discharge)),
            cautions.distinct(),full,eligible)
    }
}
