package com.locutus5382.coremetrics.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.locutus5382.coremetrics.analysis.DukoComparisonInsight
import java.io.OutputStream
import java.util.Locale
import kotlin.math.min

/**
 * Single immutable comparison model -> one printable PNG or vector A4 PDF.
 * Run on Dispatchers.IO only, after an explicit SAF destination was chosen.
 */
internal object DukoComparisonRenderer {
    const val WIDTH=1600
    const val HEIGHT=2200
    private const val PAGE_W=595
    private const val PAGE_H=842
    private val NAVY=Color.rgb(15,42,80)
    private val BLUE=Color.rgb(25,120,195)
    private val TEAL=Color.rgb(9,163,174)
    private val ORANGE=Color.rgb(207,110,35)
    private val INK=Color.rgb(27,46,64)
    private val MUTED=Color.rgb(89,111,132)
    private val BORDER=Color.rgb(224,232,239)
    private val PAPER=Color.rgb(248,251,253)

    fun png(result:DukoComparisonInsight.Result,out:OutputStream) {
        val bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        try {
            summary(Canvas(bitmap),result)
            check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)) {
                "Failed to encode DUKO comparison PNG"
            }
            out.flush()
        }finally {bitmap.recycle()}
    }

    fun pdf(result:DukoComparisonInsight.Result,out:OutputStream) {
        val pdf=PdfDocument()
        try {
            var pageNo=0
            fun page(draw:(Canvas)->Unit) {
                val page=pdf.startPage(PdfDocument.PageInfo.Builder(
                    PAGE_W,PAGE_H,++pageNo).create())
                try {
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.save()
                    val scale=min((PAGE_W-20f)/WIDTH,(PAGE_H-20f)/HEIGHT)
                    page.canvas.translate((PAGE_W-WIDTH*scale)/2,
                        (PAGE_H-HEIGHT*scale)/2)
                    page.canvas.scale(scale,scale)
                    draw(page.canvas)
                    page.canvas.restore()
                }finally {pdf.finishPage(page)}
            }
            page{summary(it,result)}
            page{evidence(it,result)}
            page{methods(it,result)}
            result.warnings.chunked(10).forEachIndexed{index,warnings ->
                page{warningPage(it,result,index,warnings)}
            }
            pdf.writeTo(out)
            out.flush()
        }finally {pdf.close()}
    }

    private class Art(val c:Canvas) {
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        fun rect(x:Float,y:Float,w:Float,h:Float,col:Int,round:Float=0f) {
            p.reset();p.isAntiAlias=true;p.color=col;p.style=Paint.Style.FILL
            if(round==0f)c.drawRect(x,y,x+w,y+h,p)
            else c.drawRoundRect(RectF(x,y,x+w,y+h),round,round,p)
        }
        fun text(raw:String,x:Float,y:Float,size:Float=26f,
                 color:Int=INK,bold:Boolean=false,width:Float=1400f) {
            p.reset();p.isAntiAlias=true;p.color=color;p.textSize=size
            p.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val value=raw.replace('\n',' ').replace('\r',' ').trim()
            var view=value
            if(p.measureText(view)>width) {
                while(view.length>1 && p.measureText(view+"…")>width)
                    view=view.dropLast(1)
                view+="…"
            }
            c.drawText(view,x,y,p)
        }
        fun wrap(value:String,x:Float,y:Float,width:Float,
                 size:Float=23f,color:Int=MUTED,maxLines:Int=3,
                 line:Float=31f):Float {
            if(value.isBlank() || maxLines<=0)return y
            p.reset();p.textSize=size
            val all=ArrayList<String>()
            var row=""
            for(word in value.replace('\n',' ').split(Regex("""\s+"""))) {
                val next=if(row.isEmpty())word else row+" "+word
                if(row.isEmpty() || p.measureText(next)<=width)row=next
                else {all+=row;row=word}
            }
            if(row.isNotEmpty())all+=row
            var at=y
            all.take(maxLines).forEachIndexed{index,item->
                text(if(index==maxLines-1 && all.size>maxLines)item+"…" else item,
                    x,at,size,color,false,width)
                at+=line
            }
            return at
        }
        fun rule(y:Float)=rect(73f,y,1454f,2f,BORDER)
    }

    private fun header(a:Art,title:String,sub:String,flag:String) {
        a.c.drawColor(PAPER)
        a.rect(0f,0f,WIDTH.toFloat(),15f,NAVY)
        a.text("D U K O   /   C O R E M E T R I C S",
            76f,87f,32f,BLUE,true)
        a.text(flag,1280f,87f,24f,MUTED,true,250f)
        a.text(title,75f,172f,68f,NAVY,true)
        a.text(sub,78f,220f,28f,MUTED,false)
        a.rule(253f)
    }

    private fun fmt(v:Double?,digits:Int=1):String =
        v?.takeIf{it.isFinite()}?.let {
            String.format(Locale.US,"%."+digits+"f",it)
        }?:"—"
    private fun pct(v:Double):String=fmt(v,0)+"%"
    private fun time(s:Double):String=String.format(Locale.US,
        "%.1f min",s/60.0)

    private fun summary(canvas:Canvas,r:DukoComparisonInsight.Result) {
        val a=Art(canvas)
        header(a,"Comparison Report",
            r.runs.size.toString()+" full recordings  ·  baseline #"+
                r.baselineId+"  ·  "+r.testedVariable.label,
            "01 / DUKO")
        a.text("COMPARABILITY: "+r.overall.label,
            79f,317f,29f,if(r.overall==DukoComparisonInsight.Confidence.OBSERVATIONAL)
                TEAL else ORANGE,true)
        a.wrap("Comparisons describe observed sensors, not verified equal scenes, "+
            "FPS, driver superiority or a performance ranking.",
            79f,355f,1440f,24f,INK,2,32f)

        val n=r.runs.size
        val startX=394f
        val colW=1120f/n
        for(i in 0 until n) {
            val run=r.runs[i]
            val x=startX+i*colW
            a.rect(x,437f,colW-9f,133f,
                if(run.report.session.id==r.baselineId)
                    Color.rgb(225,243,248) else Color.WHITE,13f)
            a.text("#"+run.report.session.id+
                (if(run.report.session.id==r.baselineId)" BASE" else ""),
                x+14f,475f,25f,NAVY,true,colW-20f)
            a.text(run.testLabel,x+14f,511f,20f,INK,false,colW-20f)
            a.text(time(run.report.observedSeconds),x+14f,544f,
                20f,MUTED,false,colW-20f)
        }
        a.text("SENSOR METRIC",79f,485f,27f,NAVY,true,305f)
        a.text("P50  /  P95  /  ΔP50 vs BASE",79f,529f,
            20f,MUTED,false,310f)
        a.rule(595f)
        val rows=r.rows
        val rowH=130f
        rows.forEachIndexed{i,row->
            val y=605f+i*rowH
            if(i%2==0)a.rect(73f,y-12f,1454f,rowH-2f,
                Color.rgb(239,245,249),8f)
            a.text(row.metric.label,80f,y+30f,25f,NAVY,true,297f)
            a.text(row.metric.unit,80f,y+64f,21f,MUTED)
            row.cells.forEachIndexed{j,cell->
                val x=startX+j*colW
                a.text(fmt(cell.p50)+" / "+fmt(cell.p95),
                    x+12f,y+26f,26f,INK,true,colW-15f)
                val delta=if(cell.comparable) {
                    val d=cell.deltaP50
                    "Δ "+(if(d!=null && d>0) "+" else "")+
                        fmt(d)+" "+row.metric.unit
                }else "Δ unavailable"
                a.text(delta,x+12f,y+62f,22f,
                    if(cell.comparable)TEAL else ORANGE,false,colW-15f)
                a.text("Coverage "+pct(cell.coveragePct),
                    x+12f,y+94f,19f,MUTED,false,colW-15f)
            }
        }
        a.rule(1790f)
        a.text("SUMMARY & EVIDENCE",78f,1841f,28f,BLUE,true)
        var y=1880f
        r.observations.take(2).forEach {
            y=a.wrap("• "+it,84f,y,1420f,22f,INK,2,29f)+9f
        }
        a.rect(74f,2075f,1452f,72f,Color.rgb(235,241,246),10f)
        a.text(r.warnings.size.toString()+" quality/provenance notices. "+
            "See PDF for every notice and the methods glossary.",
            91f,2121f,23f,ORANGE,true)
        a.text("DUKO-COMPARISON-1  ·  Offline  ·  No inferred FPS  ·  "+
            "Source: complete stored sessions",80f,2182f,20f,MUTED)
    }

    private fun evidence(canvas:Canvas,r:DukoComparisonInsight.Result) {
        val a=Art(canvas)
        header(a,"Sources & methodology",
            "Original device identity and user-declared test conditions",
            "02 / DUKO")
        var y=325f
        for(run in r.runs) {
            val s=run.report.session
            a.rect(75f,y-27f,1450f,282f,Color.WHITE,13f)
            a.text("SESSION #"+s.id+
                if(s.id==r.baselineId)" · SELECTED BASELINE" else "",
                92f,y+10f,29f,BLUE,true)
            a.text(run.deviceLabel,93f,y+50f,25f,INK,true)
            a.wrap("Game: "+run.testLabel+" · Driver: "+
                (s.graphicsDriver?:"unrecorded")+" · Scale: "+
                (s.resolutionScale?:"unrecorded"),93f,y+82f,1380f,
                22f,MUTED,2,29f)
            a.wrap("Declared mode: "+(s.performanceMode?:"unknown")+
                "; fan: "+(s.fanMode?:"unknown")+
                "; ClusterTune: "+(s.clusterTuneMode?:"unknown")+
                "; profile: "+(s.profileName?:"unknown"),
                93f,y+144f,1380f,21f,MUTED,2,27f)
            a.wrap("GPU source: "+run.report.gpuSources.joinToString(" · ")
                .ifBlank{"unavailable"},
                93f,y+193f,1380f,20f,MUTED,2,25f)
            a.wrap("Battery source: "+run.report.batterySources.joinToString(" · ")
                .ifBlank{"unavailable"},
                93f,y+240f,1380f,19f,MUTED,1,24f)
            y+=300f
        }
        y+=15f
        a.rule(y)
        a.text("METHODOLOGY FOLLOWS ON PAGE 3",
            82f,y+58f,29f,BLUE,true)
        a.wrap("Original device, game, driver and power-source metadata "+
            "are recorded observations or user-entered labels. "+
            "Source equality does not prove equal game scenes.",
            86f,y+100f,1400f,26f,INK,3,35f)
        a.text("All source warnings are printed on subsequent notice pages.",
            85f,2147f,22f,ORANGE,true)
    }

    private fun methods(canvas:Canvas,r:DukoComparisonInsight.Result) {
        val a=Art(canvas)
        header(a,"How comparisons work",
            "Eligibility rules, reporting boundaries and confidence labels",
            "03 / DUKO")
        a.text("COMPARABILITY: "+r.overall.label,
            81f,329f,30f,ORANGE,true)
        a.wrap("A sensor difference is an observation, not evidence "+
            "that a game became smoother, faster or more efficient.",
            84f,377f,1430f,26f,INK,3,38f)
        var y=515f
        r.methodology.forEachIndexed { index,item ->
            a.rect(78f,y-30f,1447f,152f,Color.WHITE,12f)
            a.rect(84f,y-20f,8f,127f,BLUE)
            a.text((index+1).toString(),112f,y+2f,27f,BLUE,true)
            a.wrap(item,161f,y,1320f,24f,INK,3,33f)
            y+=170f
        }
        a.rule(2055f)
        a.text("All notices follow. None is silently discarded.",
            86f,2114f,23f,ORANGE,true)
    }

    private fun warningPage(canvas:Canvas,r:DukoComparisonInsight.Result,
                            page:Int,items:List<String>) {
        val a=Art(canvas)
        header(a,"Evidence & limitations",
            "Recorded conditions, device provenance and comparison cautions",
            "NOTES "+(page+1))
        a.text("NOTICES "+(page*10+1)+"–"+(page*10+items.size)+
            " OF "+r.warnings.size,
            81f,323f,28f,BLUE,true)
        var y=399f
        items.forEachIndexed{i,item->
            a.rect(79f,y-28f,1445f,161f,Color.WHITE,12f)
            a.rect(85f,y-20f,8f,125f,ORANGE)
            a.text((page*10+i+1).toString().padStart(2,'0'),
                112f,y+5f,23f,ORANGE,true)
            a.wrap(item,168f,y+4f,1320f,24f,INK,3,33f)
            y+=174f
        }
        a.rule(2122f)
        a.text("Unverified test equivalence is never converted into a winner "+
            "or a statistical confidence score.",
            84f,2170f,21f,MUTED)
    }
}
