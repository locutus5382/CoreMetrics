package com.locutus5382.coremetrics.analysis

import com.locutus5382.coremetrics.data.SessionStore
import com.locutus5382.coremetrics.monitor.BatteryTemperatureAdvisory
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Pure offline full-session analysis; never samples hardware or claims FPS.
 * One cursor pass, exact bounded primitive quantiles, fixed-size chart traces.
 */
object DukoSessionInsight {
    const val MODEL_VERSION="DUKO-SESSION-1"
    const val MAX_SAMPLES=250_000
    const val CHART_BUCKETS=280
    private const val MAX_EVENTS=32

    enum class Metric(val label:String,val unit:String) {
        CPU_TEMP("CPU sensor temperature","°C"),
        GPU_TEMP("GPU sensor temperature","°C"),
        BATTERY_TEMP("Battery temperature","°C"),
        CPU_LOAD("System CPU utilization","%"),
        GPU_LOAD("GPU utilization","%"),
        BUSIEST("Busiest system CPU core","%"),
        POWER("Battery-terminal discharge","W"),
        GPU_CLOCK("GPU frequency","MHz"),
        MEMORY_PSI("Memory PSI some avg10","%")
    }
    enum class Evidence { OBSERVED, PATTERN, USER_DECLARED }
    data class Stats(val metric:Metric,val count:Int,val p50:Double?,
        val p95:Double?,val peak:Double?,val minimum:Double?,
        val coveragePct:Double)
    data class Event(val offsetMs:Long,val title:String,
        val detail:String,val evidence:Evidence)
    data class Finding(val text:String,val evidence:Evidence)
    data class Trace(val offsetMs:Long,val value:Double,val segment:Int)
    data class Chart(val metric:Metric,val points:List<Trace>)
    data class Report(
        val session:SessionStore.Session,val formatVersion:String,
        val appVersion:String,val firstMs:Long,val lastMs:Long,
        val sampleCount:Int,val sourceSampleCount:Long,
        val cadenceMs:Long,val gapThresholdMs:Long,val gapCount:Int,
        val stats:List<Stats>,val measuredEnergyWh:Double?,
        val validDischargeSeconds:Double,val dischargeCoveragePct:Double,
        val weightedDischargeW:Double?,
        val batteryWarmSeconds:Double,val batteryAbove42Seconds:Double,
        val maximumOsThermalStatus:Int?,
        val events:List<Event>,val additionalEvents:Int,
        val charts:List<Chart>,val findings:List<Finding>,
        val cautions:List<String>,val gpuSources:List<String>,
        val batterySources:List<String>
    ) {
        fun stat(m:Metric):Stats=stats.first{it.metric==m}
        val observedSeconds:Double get()=(lastMs-firstMs)/1000.0
    }

    private class Quantiles(val metric:Metric) {
        private var data=DoubleArray(128)
        private var length=0
        fun add(v:Double?) {
            if(v==null || !v.isFinite())return
            if(length>=data.size)data=data.copyOf(data.size*2)
            data[length++]=v
        }
        fun finish(total:Int):Stats {
            val values=data.copyOf(length).apply{sort()}
            fun pct(q:Double):Double? {
                if(length==0)return null
                val index=(length-1)*q
                val i=index.toInt()
                val j=ceil(index).toInt()
                return values[i]+(values[j]-values[i])*(index-i)
            }
            return Stats(metric,length,pct(.5),pct(.95),
                values.lastOrNull(),values.firstOrNull(),
                if(total==0)0.0 else length*100.0/total)
        }
    }

    /** Retains min and max per time bucket, preserving meaningful spikes
     * within a fixed graph budget. Segment IDs break lines across gaps.
     */
    private class Buckets(val metric:Metric,private val start:Long,
        private val end:Long) {
        private data class Extreme(val at:Long,val v:Double,val segment:Int)
        private val minima=arrayOfNulls<Extreme>(CHART_BUCKETS)
        private val maxima=arrayOfNulls<Extreme>(CHART_BUCKETS)
        fun add(at:Long,value:Double?,segment:Int) {
            if(value==null || !value.isFinite())return
            val span=max(1L,end-start)
            val slot=(((at-start).coerceIn(0L,span).toDouble()/span)*
                CHART_BUCKETS).toInt().coerceIn(0,CHART_BUCKETS-1)
            val item=Extreme(at,value,segment)
            if(minima[slot]==null || value<minima[slot]!!.v)minima[slot]=item
            if(maxima[slot]==null || value>maxima[slot]!!.v)maxima[slot]=item
        }
        fun finish():Chart {
            val items=ArrayList<Trace>(CHART_BUCKETS*2)
            for(i in 0 until CHART_BUCKETS){
                listOfNotNull(minima[i],maxima[i])
                    .distinctBy{it.at to it.v}
                    .sortedBy{it.at}
                    .forEach{items+=Trace(it.at-start,it.v,it.segment)}
            }
            return Chart(metric,items)
        }
    }

    fun reading(m:Metric,p:SessionStore.Point):Double? {
        val value=when(m) {
            Metric.CPU_TEMP->p.cpu
            Metric.GPU_TEMP->p.gpu
            Metric.BATTERY_TEMP->p.batteryTemp
            Metric.CPU_LOAD->p.cpuLoad
            Metric.GPU_LOAD->p.gpuLoad
            Metric.BUSIEST->p.busiestCorePct
            Metric.POWER->DischargeSample.watts(p.batteryWatts,p.batteryPowerStatus)
            Metric.GPU_CLOCK->p.gpuMHz
            Metric.MEMORY_PSI->p.memoryPsiSomeAvg10
        }?.takeIf{it.isFinite()}?:return null
        return when(m) {
            Metric.CPU_TEMP,Metric.GPU_TEMP->value.takeIf{it in -20.0..160.0}
            Metric.BATTERY_TEMP->value.takeIf{it in -10.0..90.0}
            Metric.CPU_LOAD,Metric.GPU_LOAD,Metric.BUSIEST,
            Metric.MEMORY_PSI->value.takeIf{it in 0.0..100.0}
            Metric.POWER->value
            Metric.GPU_CLOCK->value.takeIf{it in 30.0..3000.0}
        }
    }

    /** Requires complete, timestamp-sorted source. Max sample count is
     * enforced; a changed/incomplete recording is rejected, never trimmed.
     */
    class Accumulator(
        private val session:SessionStore.Session,
        private val bounds:SessionStore.ReportBounds,
        cadenceEstimateMs:Long
    ){
        private val cadence=cadenceEstimateMs.coerceIn(500L,15_000L)
        private val gapLimit=max(6_000L,min(15_000L,cadence*3L))
        private val quantiles=Metric.entries.associateWith{Quantiles(it)}
        private val plots=listOf(Metric.CPU_TEMP,Metric.GPU_TEMP,Metric.GPU_LOAD)
            .associateWith{Buckets(it,bounds.firstMs,bounds.lastMs)}
        private val events=ArrayList<Event>()
        private val gpuSources=LinkedHashSet<String>()
        private val batterySources=LinkedHashSet<String>()
        private val warmth=BatteryTemperatureAdvisory()
        private var lastWarmth=BatteryTemperatureAdvisory.State.UNAVAILABLE
        private var prior:SessionStore.Point?=null
        private var samples=0
        private var segment=0
        private var gaps=0
        private var eventCount=0
        private var powerWms=0.0
        private var powerMs=0L
        private var warmMs=0L
        private var above42Ms=0L
        private var maximumThermal:Int?=null
        private var currentCap:String?=null
        private var candidateCap:String?=null
        private var candidateCount=0
        private var priorPsiHigh=false
        private val recentGpu=ArrayDeque<Pair<Long,Double?>>()
        private val recentCore=ArrayDeque<Pair<Long,Double?>>()
        private val highEmitted=mutableSetOf<String>()
        private var previousThermal:Int?=null

        private fun emit(p:SessionStore.Point,title:String,detail:String,
            evidence:Evidence) {
            eventCount++
            if(events.size<MAX_EVENTS)events+=Event(
                p.timeMs-bounds.firstMs,title,detail,evidence)
        }

        private fun sustained(queue:ArrayDeque<Pair<Long,Double?>>,
            name:String,p:SessionStore.Point,v:Double?) {
            queue.addLast(p.timeMs to v)
            while(queue.isNotEmpty() && queue.first.first<p.timeMs-120_000L)
                queue.removeFirst()
            if(name in highEmitted || queue.size<3)return
            if(queue.last.first-queue.first.first<120_000L-cadence)return
            val valid=queue.mapNotNull{it.second}
            val expected=max(2,(120_000L/cadence).toInt())
            if(valid.size<expected*0.9)return
            if(valid.count{it>=95.0}/valid.size.toDouble()>=0.95) {
                emit(p,name+" sustained high utilization",
                    "95%+ readings in 95%+ of valid samples over ~120s; "+
                    "game FPS and bottleneck cause unmeasured.",Evidence.PATTERN)
                highEmitted+=name
            }
        }

        fun accept(p:SessionStore.Point) {
            require(samples<MAX_SAMPLES){"Too many samples for bounded DUKO export"}
            val previous=prior
            require(previous==null || p.timeMs>previous.timeMs) {
                "Non-monotonic timestamps; cannot produce truthful timeline"
            }
            require(p.timeMs in bounds.firstMs..bounds.lastMs) {
                "Sample outside source recording bounds"
            }
            val dt=if(previous==null)0L else p.timeMs-previous.timeMs
            if(previous!=null && dt>gapLimit) {
                gaps++;segment++
                emit(p,"Recording gap","Preceding interval "+
                    (dt/1000.0)+" s, missing behavior unknown.",Evidence.OBSERVED)
                recentGpu.clear();recentCore.clear()
                candidateCap=null;candidateCount=0
                warmth.reset()
            }

            for(m in Metric.entries) {
                val value=reading(m,p)
                quantiles.getValue(m).add(value)
                plots[m]?.add(p.timeMs,value,segment)
            }
            // Missing data splits all relevant traces. No interpolation.
            if(reading(Metric.CPU_TEMP,p)==null ||
                reading(Metric.GPU_TEMP,p)==null ||
                reading(Metric.GPU_LOAD,p)==null)segment++

            p.gpuTelemetrySource?.takeIf{it.isNotBlank()}?.let{
                if(gpuSources.size<16)gpuSources+=it
            }
            p.batteryPowerSource?.takeIf{it.isNotBlank()}?.let{
                if(batterySources.size<16)batterySources+=it
            }
            if(previous!=null && dt<=gapLimit) {
                val a=reading(Metric.POWER,previous)
                val b=reading(Metric.POWER,p)
                if(a!=null && b!=null) {
                    powerWms+=(a+b)*0.5*dt
                    powerMs+=dt
                }
                val old=reading(Metric.BATTERY_TEMP,previous)
                val now=reading(Metric.BATTERY_TEMP,p)
                if(old!=null && now!=null){
                    if(old>=39.0 && now>=39.0)warmMs+=dt
                    if(old>42.0 && now>42.0)above42Ms+=dt
                }
            }

            p.thermalStatus?.takeIf{it in 0..6}?.let {status->
                maximumThermal=maxOf(maximumThermal?:status,status)
                if(status>=2 && (previousThermal?:0)<2)
                    emit(p,"Android thermal policy pressure",
                        "Device-wide status "+status+
                        "; not proof of CPU/GPU throttling.",Evidence.OBSERVED)
                previousThermal=status
            }
            val psiHigh=reading(Metric.MEMORY_PSI,p)?.let{it>=10.0}==true
            if(psiHigh && priorPsiHigh &&
                events.none{it.title=="Memory pressure observed"})
                emit(p,"Memory pressure observed",
                    "System-wide PSI some avg10 >=10% in consecutive readings; "+
                    "not necessarily caused by the game.",Evidence.OBSERVED)
            priorPsiHigh=psiHigh

            val state=warmth.update(reading(Metric.BATTERY_TEMP,p),
                p.timeMs-bounds.firstMs)
            if(state==BatteryTemperatureAdvisory.State.HOT &&
                lastWarmth!=BatteryTemperatureAdvisory.State.HOT)
                emit(p,"BAT T orange HOT advisory",
                    "Over 42°C continuously for 15s, application advisory "+
                    "rather than device safety limit.",Evidence.OBSERVED)
            lastWarmth=state

            val key=SessionRegimeAnalyzer.key(p.observedCpuLimits)
                .takeUnless{it.contains("?")}
            if(key==null){candidateCap=null;candidateCount=0}
            else if(key==candidateCap){
                candidateCount++
                if(candidateCount==3){
                    if(currentCap!=null && currentCap!=key)
                        emit(p,"CPU frequency ceiling changed",
                            SessionRegimeAnalyzer.label(currentCap!!)+" to "+
                            SessionRegimeAnalyzer.label(key)+
                            "; controlling application/cause unknown.",
                            Evidence.OBSERVED)
                    currentCap=key
                }
            }else{candidateCap=key;candidateCount=1}

            sustained(recentGpu,"GPU",p,reading(Metric.GPU_LOAD,p))
            sustained(recentCore,"Busiest core",p,reading(Metric.BUSIEST,p))
            prior=p;samples++
        }

        fun finish():Report {
            require(samples>=6 && samples.toLong()==bounds.count) {
                "Report source changed or does not contain six samples"
            }
            require(bounds.lastMs-bounds.firstMs>=20_000L) {
                "DUKO Report requires >=20s observed sample span"
            }
            val span=bounds.lastMs-bounds.firstMs
            val coverage=100.0*powerMs/span
            val results=Metric.entries.map{quantiles.getValue(it).finish(samples)}
            val findings=mutableListOf<Finding>()
            if(gaps==0)findings+=Finding(
                "No sampling gaps exceeded "+(gapLimit/1000.0)+" seconds.",
                Evidence.OBSERVED)
            else findings+=Finding(
                gaps.toString()+" recording gaps were detected; "+
                    "missing periods were excluded from energy integration.",
                Evidence.OBSERVED)
            if(maximumThermal!=null && maximumThermal!!>=2)
                findings+=Finding("Android reported device-wide thermal "+
                    "pressure (maximum status "+maximumThermal+"). "+
                    "CPU/GPU throttle causation is unverified.",Evidence.OBSERVED)
            if(warmMs>0L)
                findings+=Finding("BAT T was >=39°C for "+(warmMs/1000)+
                    " seconds of continuously observed intervals.",
                    Evidence.OBSERVED)
            if(events.any{it.title=="CPU frequency ceiling changed"})
                findings+=Finding("Observed CPU policy frequency ceilings "+
                    "changed; this does not identify the responsible tuner.",
                    Evidence.OBSERVED)
            if(powerMs>=30_000L && coverage>=60.0)
                findings+=Finding("Time-weighted battery-terminal power "+
                    "uses "+(powerMs/1000)+" seconds of valid discharge.",
                    Evidence.OBSERVED)
            if(events.any{it.title.contains("sustained high")})
                findings+=Finding("Sustained high system utilization "+
                    "does not establish game FPS or frame pacing.",
                    Evidence.PATTERN)
            if(findings.size<3)
                findings+=Finding("These sensor observations describe "+
                    "system behavior, not verified game-thread activity.",
                    Evidence.OBSERVED)

            val cautions=mutableListOf(
                "Game FPS, frame times and frame efficiency are not verified.",
                "Sensor thermal zones and GPU vendor read paths require "+
                    "hardware/ROM validation.",
                "Battery-terminal watts are whole-device net power, not chip power.",
                "Elapsed time starts at the first recorded sample, not a verified game scene."
            )
            if(session.deviceModel.isNullOrBlank())
                cautions+="Original recording device was not identified."
            if(gpuSources.isEmpty() || gpuSources.any{
                it.contains("UNAVAILABLE") || it.contains("READABLE_NOT_VALIDATED")
            })cautions+="GPU read paths are absent or not calibrated."
            if(batterySources.isEmpty() || batterySources.any{
                it.contains("UNAVAILABLE") || it.contains("SNAPSHOT_AGE_UNVERIFIED")
            })cautions+="Battery current/voltage update ages may differ."
            if(coverage<80.0)
                cautions+="Discharge data cover less than 80% of observed time."
            if(session.status !in setOf("COMPLETED","IMPORTED_COMPLETED"))
                cautions+="Recording was not a clean completed session."

            val energy=if(powerMs>0L)powerWms/3_600_000.0 else null
            val weighted=if(powerMs>0L)powerWms/powerMs else null
            return Report(session,MODEL_VERSION,ExportVersion.APP_VERSION,
                bounds.firstMs,bounds.lastMs,samples,bounds.count,
                cadence,gapLimit,gaps,results,energy,
                powerMs/1000.0,coverage,weighted,
                warmMs/1000.0,above42Ms/1000.0,
                maximumThermal,events.toList(),max(0,eventCount-events.size),
                plots.values.map{it.finish()},findings.take(5),
                cautions,gpuSources.toList(),batterySources.toList())
        }
    }
}
