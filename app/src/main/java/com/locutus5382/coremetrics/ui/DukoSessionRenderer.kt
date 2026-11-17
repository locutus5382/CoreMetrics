package com.locutus5382.coremetrics.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.locutus5382.coremetrics.analysis.DukoSessionInsight
import com.locutus5382.coremetrics.analysis.DukoSessionInsight.Metric
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Shared vector Canvas layout for PDF and a single bounded PNG summary.
 * Native Android PdfDocument: selectable text, no screenshot/HTML engine,
 * network requests, external font, or per-frame telemetry sampling.
 *
 * Call from Dispatchers.IO after the SAF destination is selected.
 */
internal object DukoSessionRenderer {
    const val WIDTH=1600
    const val HEIGHT=2200
    private const val A4_W=595
    private const val A4_H=842

    private val INK=Color.rgb(25,45,67)
    private val MUTED=Color.rgb(88,111,132)
    private val NAVY=Color.rgb(14,43,83)
    private val BLUE=Color.rgb(25,120,195)
    private val CYAN=Color.rgb(9,163,174)
    private val ORANGE=Color.rgb(210,110,38)
    private val GREY=Color.rgb(226,233,239)
    private val BG=Color.rgb(249,251,253)
    private val WHITE=Color.WHITE

    fun png(report:DukoSessionInsight.Report,out:OutputStream) {
        val bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        try {
            drawSummary(Canvas(bitmap),report)
            check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) {
                "Could not encode DUKO PNG"
            }
            out.flush()
        }finally {bitmap.recycle()}
    }

    fun pdf(report:DukoSessionInsight.Report,out:OutputStream) {
        val pdf=PdfDocument()
        try {
            var pageNumber=0
            fun add(render:(Canvas)->Unit){
                pageNumber++
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(
                    A4_W,A4_H,pageNumber).create())
                try {
                    val c=page.canvas
                    c.drawColor(WHITE)
                    c.save()
                    // Preserve source aspect ratio and leave a PDF bottom gutter.
                    val scale=min(A4_W.toFloat()/WIDTH,A4_H.toFloat()/HEIGHT)
                    c.translate((A4_W-WIDTH*scale)/2f,
                        (A4_H-HEIGHT*scale)/2f)
                    c.scale(scale,scale)
                    render(c)
                    c.restore()
                }finally {pdf.finishPage(page)}
            }
            add{drawSummary(it,report)}
            add{drawEvidence(it,report)}
            if(report.events.isNotEmpty()) {
                report.events.chunked(10).forEachIndexed{idx,events->
                    add{drawEventPage(it,report,events,idx)}
                }
            }
            pdf.writeTo(out)
            out.flush()
        }finally {pdf.close()}
    }

    private class Art(val c:Canvas) {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(x:Float,y:Float,w:Float,h:Float,color:Int,round:Float=0f) {
            p.reset();p.isAntiAlias=true;p.color=color;p.style=Paint.Style.FILL
            if(round>0) c.drawRoundRect(RectF(x,y,x+w,y+h),round,round,p)
            else c.drawRect(x,y,x+w,y+h,p)
        }
        fun rule(x:Float,y:Float,end:Float,color:Int=GREY,thick:Float=2f) {
            fill(x,y,end-x,thick,color)
        }
        fun text(value:String?,x:Float,y:Float,size:Float=26f,
            color:Int=INK,bold:Boolean=false,maxWidth:Float=1400f):String {
            val original=value?.replace('\n',' ')?.replace('\r',' ')
                ?.trim().orEmpty()
            p.reset();p.isAntiAlias=true;p.color=color;p.textSize=size
            p.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            var result=original
            if(p.measureText(result)>maxWidth) {
                while(result.length>1 && p.measureText(result+"...")>maxWidth)
                    result=result.dropLast(1)
                result+="..."
            }
            c.drawText(result,x,y,p)
            return result
        }
        fun wrap(value:String,x:Float,y:Float,width:Float,
            size:Float=24f,color:Int=MUTED,bold:Boolean=false,
            line:Float=size*1.30f,maxLines:Int=3):Float {
            val words=value.replace('\n',' ').split(Regex("""\s+"""))
            var row="";var cursor=y;var used=0
            for(word in words) {
                val possible=if(row.isEmpty())word else row+" "+word
                p.textSize=size;p.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                if(row.isNotEmpty() && p.measureText(possible)>width) {
                    text(row,x,cursor,size,color,bold,width)
                    cursor+=line;used++
                    row=word
                    if(used>=maxLines-1)break
                }else row=possible
            }
            if(used<maxLines){
                text(row,x,cursor,size,color,bold,width)
                cursor+=line
            }
            return cursor
        }
        fun bar(x:Float,y:Float,w:Float,h:Float,color:Int=BLUE){
            fill(x,y,w,h,color,3f)
        }
    }

    private fun fmt(v:Double?,unit:String="",precision:Int=1):String =
        v?.takeIf{it.isFinite()}?.let {
            String.format(Locale.US,"%."+precision+"f",it)+unit
        } ?: "Not measured"

    private fun time(seconds:Double):String {
        val s=seconds.toLong().coerceAtLeast(0L)
        return String.format(Locale.US,"%02d:%02d",s/60,s%60)
    }
    private fun recordedDate(epochMs:Long):String =
        SimpleDateFormat("dd MMM yyyy · HH:mm",Locale.US).format(Date(epochMs))

    private fun header(a:Art,title:String,subtitle:String,page:String) {
        a.fill(0f,0f,WIDTH.toFloat(),HEIGHT.toFloat(),BG)
        a.fill(0f,0f,WIDTH.toFloat(),16f,NAVY)
        a.text("D U K O   /   C O R E M E T R I C S",
            76f,86f,33f,BLUE,true,1410f)
        a.text(page,1330f,88f,24f,MUTED,true,200f)
        a.text(title,76f,177f,69f,NAVY,true,1430f)
        a.text(subtitle,78f,230f,28f,MUTED,false,1400f)
        a.rule(76f,262f,1525f)
    }

    private fun card(a:Art,x:Float,y:Float,w:Float,h:Float,title:String,
        value:String,detail:String,accent:Int=BLUE) {
        a.fill(x,y,w,h,WHITE,22f)
        a.fill(x,y,8f,h,accent,4f)
        a.text(title,x+31f,y+50f,26f,MUTED,true,w-55f)
        a.text(value,x+31f,y+119f,48f,INK,true,w-58f)
        a.text(detail,x+31f,y+168f,23f,MUTED,false,w-58f)
    }

    private fun drawSummary(canvas:Canvas,r:DukoSessionInsight.Report) {
        val a=Art(canvas)
        header(a,"Session Insight",
            "Recording #"+r.session.id+" · "+recordedDate(r.firstMs)+
                " · "+time(r.observedSeconds)+" observed",
            "01 / DUKO")
        a.text("TEST IDENTITY",80f,317f,25f,BLUE,true)
        val name=(r.session.note?:r.session.label).take(90)
        a.text(name,80f,357f,31f,INK,true,920f)
        a.text("Device: "+listOfNotNull(r.session.deviceManufacturer,
            r.session.deviceModel).joinToString(" ").ifBlank{"Unknown original device"},
            80f,400f,25f,MUTED,false,1100f)
        a.text("Driver: "+(r.session.graphicsDriver?:"Not recorded")+
            "   ·   Scale: "+(r.session.resolutionScale?:"Not recorded"),
            80f,433f,23f,MUTED,false,1450f)
        val keys=listOf(
            Triple("CPU SENSOR P50 / P95",
                fmt(r.stat(Metric.CPU_TEMP).p50," °C")+" / "+
                    fmt(r.stat(Metric.CPU_TEMP).p95," °C"),
                "Sample quantiles"),
            Triple("GPU SENSOR P50 / P95",
                fmt(r.stat(Metric.GPU_TEMP).p50," °C")+" / "+
                    fmt(r.stat(Metric.GPU_TEMP).p95," °C"),
                "Sensor identity requires validation"),
            Triple("BATTERY TEMP P50 / PEAK",
                fmt(r.stat(Metric.BATTERY_TEMP).p50," °C")+" / "+
                    fmt(r.stat(Metric.BATTERY_TEMP).peak," °C"),
                "Warm band: >=39°C (app-defined)"),
            Triple("SYSTEM CPU / GPU P50",
                fmt(r.stat(Metric.CPU_LOAD).p50,"%")+" / "+
                    fmt(r.stat(Metric.GPU_LOAD).p50,"%"),
                "Not verified game FPS"),
            Triple("MEAN DISCHARGE",
                fmt(r.weightedDischargeW," W",2),
                "Battery-terminal net watts"),
            Triple("RECORDED ENERGY",
                fmt(r.measuredEnergyWh," Wh",3),
                fmt(r.dischargeCoveragePct,"%")+" of span eligible")
        )
        keys.forEachIndexed{i,k->
            val col=i%3;val row=i/3
            card(a,78f+col*488f,473f+row*200f,462f,185f,
                k.first,k.second,k.third,if(i==2)ORANGE else if(i==4)CYAN else BLUE)
        }
        a.text("RECORDING EVIDENCE",80f,911f,28f,BLUE,true)
        a.text(r.sampleCount.toString()+" original samples  ·  "+
            "cadence ~"+fmt(r.cadenceMs/1000.0," s")+"  ·  "+
            r.gapCount+" detected gaps",80f,953f,25f,MUTED)
        chart(a,r,960f,370f,listOf(Metric.CPU_TEMP,Metric.GPU_TEMP),
            "CPU & GPU SENSOR TEMPERATURES",true)
        chart(a,r,1352f,294f,listOf(Metric.GPU_LOAD),
            "GPU UTILIZATION (SYSTEM SENSOR)",false)
        a.text("EVIDENCE-QUALIFIED FINDINGS",81f,1695f,28f,BLUE,true)
        var y=1740f
        r.findings.take(4).forEachIndexed{i,f->
            a.bar(83f,y-21f,9f,26f,if(f.evidence==
                DukoSessionInsight.Evidence.PATTERN)ORANGE else CYAN)
            a.wrap((i+1).toString()+". "+f.text,113f,y,1370f,
                25f,INK,false,32f,2)
            y+=68f
        }
        a.rule(80f,2021f,1520f)
        a.text("INTERPRETATION",82f,2061f,23f,ORANGE,true)
        a.wrap("Observed system telemetry, not proof of driver superiority, "+
            "FPS, game-thread scheduling or thermal-throttling cause. "+
            "Full methodology, provenance and event timeline are in the PDF.",
            82f,2101f,1412f,21f,MUTED,false,27f,3)
        a.text("DUKO SESSION-1  ·  App v"+r.appVersion+
            "  ·  Offline  ·  No samples omitted  ·  Session ID #"+r.session.id,
            82f,2172f,18f,MUTED)
    }

    private fun chart(a:Art,r:DukoSessionInsight.Report,
        y:Float,h:Float,metrics:List<Metric>,title:String,
        autoScale:Boolean) {
        a.fill(77f,y,1450f,h,WHITE,18f)
        a.text(title,102f,y+45f,26f,NAVY,true)
        val left=146f;val right=1452f
        val top=y+82f;val bottom=y+h-65f
        val series=r.charts.filter{it.metric in metrics}
        val vals=series.flatMap{it.points.map(DukoSessionInsight.Trace::value)}
        val lo=if(autoScale) {
            vals.minOrNull()?.let{min(-0.0,it-2.0)} ?:20.0
        }else 0.0
        val hi=if(autoScale) {
            vals.maxOrNull()?.let{max(lo+5.0,it+2.0)}?:80.0
        }else 100.0
        val spread=max(1.0,hi-lo)
        val duration=max(1L,r.lastMs-r.firstMs)
        fun x(at:Long)=left+(at.toDouble()/duration*(right-left)).toFloat()
        fun yy(v:Double)=bottom-((v-lo)/spread*(bottom-top)).toFloat()
        for(i in 0..4) {
            val level=lo+spread*i/4
            val at=yy(level)
            a.rule(left,at,right,GREY,1.5f)
            a.text(fmt(level,"",0),99f,at+6f,19f,MUTED)
        }
        val colors=listOf(ORANGE,CYAN,BLUE)
        series.forEachIndexed{i,seriesData->
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
                color=colors[i%colors.size];style=Paint.Style.STROKE
                strokeWidth=3.4f
            }
            val path=Path()
            var lastSegment:Int?=null
            var previousOffset:Long?=null
            for(p in seriesData.points){
                val px=x(p.offsetMs);val py=yy(p.value)
                if(lastSegment!=p.segment || previousOffset==null ||
                    p.offsetMs-previousOffset!!>r.gapThresholdMs)
                    path.moveTo(px,py)
                else path.lineTo(px,py)
                lastSegment=p.segment;previousOffset=p.offsetMs
            }
            a.c.save()
            a.c.clipRect(left,top,right,bottom)
            a.c.drawPath(path,paint)
            a.c.restore()
            a.bar(850f+i*290f,y+22f,22f,10f,colors[i%colors.size])
            a.text(seriesData.metric.label.take(19),
                882f+i*290f,y+32f,20f,MUTED,false,260f)
        }
        a.text("00:00",left,bottom+35f,19f,MUTED)
        a.text(time(r.observedSeconds),right-95f,bottom+35f,19f,MUTED)
        if(vals.isEmpty()) a.text("NOT MEASURED / UNSUPPORTED",540f,
            top+(bottom-top)/2,32f,ORANGE,true)
    }

    private fun drawEvidence(canvas:Canvas,r:DukoSessionInsight.Report) {
        val a=Art(canvas)
        header(a,"Evidence & interpretation",
            "Device provenance · measurement limits · glossary",
            "02 / DUKO")
        a.text("DATA QUALITY",80f,324f,29f,BLUE,true)
        a.wrap("Session #"+r.session.id+" · "+r.sampleCount+
            " of "+r.sourceSampleCount+" stored samples analyzed · "+
            fmt(r.observedSeconds,"s")+" observed span · "+
            r.gapCount+" gap(s) above "+fmt(r.gapThresholdMs/1000.0,"s"),
            84f,367f,1380f,26f,INK,false,33f,2)
        a.text("Discharge integration: "+fmt(r.validDischargeSeconds," s")+
            " · "+fmt(r.dischargeCoveragePct,"%")+" valid interval coverage",
            84f,453f,27f,INK)
        a.wrap("No missing samples were inferred. Adjacent valid battery "+
            "readings alone contribute to integrated Wh; gaps and charging "+
            "intervals never become discharge energy.",
            84f,503f,1410f,23f,MUTED,false,31f,3)
        a.rule(82f,604f,1510f)
        a.text("THERMAL AND EVENT EVIDENCE",82f,659f,29f,BLUE,true)
        a.text("BAT T >=39°C (valid time): "+fmt(r.batteryWarmSeconds," s")+
            "   ·   >42°C: "+fmt(r.batteryAbove42Seconds," s"),
            85f,704f,25f,INK)
        a.text("Maximum Android thermal status: "+
            (r.maximumOsThermalStatus?.toString()?:"Not measured"),
            85f,755f,25f,INK)
        a.text("Detected event observations: "+
            (r.events.size+r.additionalEvents)+" · "+
            r.events.count{it.evidence==DukoSessionInsight.Evidence.PATTERN}+
            " stored pattern event(s)",85f,804f,25f,INK)
        a.wrap("Event times describe sensor observations. A change in clock "+
            "ceiling, system-core activity or utilization cannot identify "+
            "the tuner, a game thread, a throttle cause, or a gameplay event.",
            85f,857f,1410f,23f,MUTED,false,31f,3)
        a.rule(82f,960f,1510f)
        a.text("ORIGINAL DEVICE AND SIGNAL SOURCES",82f,1014f,29f,BLUE,true)
        a.wrap("Device: "+listOfNotNull(r.session.deviceManufacturer,
            r.session.deviceModel,r.session.deviceCode).joinToString(" / ")
                .ifBlank{"Original device unavailable"}+
            "   ·   Android API "+(r.session.androidApi?.toString()?:"unknown"),
            85f,1060f,1400f,26f,INK,false,33f,2)
        a.wrap("User-declared settings: "+r.session.configuration+
            "; driver "+(r.session.graphicsDriver?:"unknown")+
            "; scale "+(r.session.resolutionScale?:"unknown")+
            "; fan "+(r.session.fanMode?:"unknown")+
            "; ClusterTune "+(r.session.clusterTuneMode?:"unknown"),
            85f,1141f,1400f,22f,MUTED,false,29f,3)
        a.wrap("GPU source(s): "+r.gpuSources.joinToString(" · ")
            .ifBlank{"not recorded / unavailable"},
            85f,1240f,1400f,22f,MUTED,false,29f,3)
        a.wrap("Battery current/voltage source(s): "+
            r.batterySources.joinToString(" · ").ifBlank{"not recorded"},
            85f,1335f,1400f,22f,MUTED,false,29f,3)
        a.rule(82f,1441f,1510f)
        a.text("GLOSSARY · HOW TO READ THESE NUMBERS",82f,1493f,29f,BLUE,true)
        val glossary=listOf(
            "P50 / P95" to "Median and 95th percentile of sampled sensor readings; NOT FPS percentiles.",
            "W / Wh" to "Whole-device battery-terminal discharge power and integrated energy; NOT CPU/GPU chip power.",
            "Valid coverage" to "Share of observed duration eligible for an analysis; no interpolation through missing time.",
            "CPU cap event" to "A change in observed cpufreq policy ceiling, not attribution to thermal throttling or an app.",
            "Thermal status" to "Android's device-wide thermal policy response; no universal CPU/GPU critical temperature.",
            "GPU activity" to "Vendor-reported system GPU load; lower load does NOT establish a smoother game."
        )
        var y=1542f
        glossary.forEach{(name,description)->
            a.text(name,87f,y,23f,NAVY,true,260f)
            a.wrap(description,355f,y,1120f,21f,MUTED,false,28f,2)
            y+=88f
        }
        a.text("Data are observational. See additional pages for event details.",
            85f,2149f,21f,ORANGE,true)
    }

    private fun drawEventPage(canvas:Canvas,r:DukoSessionInsight.Report,
        events:List<DukoSessionInsight.Event>,index:Int) {
        val a=Art(canvas)
        header(a,"Recorded events",
            "Session #"+r.session.id+" · "+
                "timestamps relative to first stored sample",
            "EVENTS")
        a.text("EVENT TIMELINE · PART "+(index+1),82f,327f,30f,BLUE,true)
        var y=382f
        events.forEach {event->
            a.fill(79f,y-33f,1448f,148f,WHITE,12f)
            a.bar(83f,y-27f,8f,125f,
                if(event.evidence==DukoSessionInsight.Evidence.PATTERN)
                    ORANGE else CYAN)
            a.text(time(event.offsetMs/1000.0)+"  ·  "+event.title,
                110f,y,27f,INK,true,1360f)
            a.text(event.evidence.name.lowercase(Locale.US)+
                " evidence",110f,y+37f,21f,BLUE)
            a.wrap(event.detail,110f,y+70f,1350f,22f,MUTED,false,28f,2)
            y+=165f
        }
        a.rule(81f,2078f,1516f)
        a.wrap("All times are source-sample offsets. "+
            "The first "+r.events.size+" events are listed; "+
            r.additionalEvents+" additional event(s) exceeded the report event cap. "+
            "An event is not proof of causality.",
            84f,2114f,1398f,21f,MUTED,false,28f,3)
    }
}
