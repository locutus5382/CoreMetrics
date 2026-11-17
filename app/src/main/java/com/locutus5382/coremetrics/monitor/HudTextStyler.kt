package com.locutus5382.coremetrics.monitor

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface

/**
 * Presentation spans only, applied to real snapshot-backed readouts.
 * No additional telemetry, regex engine, font files or background threads.
 *
 * Styles can be regenerated every recording tick without retaining any
 * Bitmap, Canvas or Compose state in the separate :monitor process.
 */
internal object HudTextStyler {
    private val ice=0xFFEFF6FD.toInt()
    private val frost=0xFF9BBDCF.toInt()
    private val cyan=0xFF5FE2FA.toInt()
    private val paleBlue=0xFFB1D9ED.toInt()
    private val warm=0xFFFFA252.toInt()
    private val hot=0xFFFF4E60.toInt()

    private fun SpannableStringBuilder.color(start:Int,end:Int,color:Int) {
        if(start in 0 until end && end<=length)
            setSpan(ForegroundColorSpan(color),start,end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    private fun SpannableStringBuilder.size(start:Int,end:Int,multiplier:Float){
        if(start in 0 until end && end<=length)
            setSpan(RelativeSizeSpan(multiplier),start,end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    private fun SpannableStringBuilder.bold(start:Int,end:Int) {
        if(start in 0 until end && end<=length)
            setSpan(StyleSpan(Typeface.BOLD),start,end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun style(theme:String,text:String,cpuTemp:String?,advisoryBand:Int):CharSequence {
        if(text.isEmpty())return text
        val normalized=HudLayoutPolicy.normalize(theme)
        val out=SpannableStringBuilder(text)
        val lens=normalized==HudLayoutPolicy.DUKO
        // Keep DUKO pure monochrome except values crossing the stated
        // DISPLAY ADVISORY, never a certified hardware cut-off.
        if(lens) {
            var lineStart=0
            while(lineStart<text.length) {
                val end=text.indexOf('\n',lineStart).let {
                    if(it<0)text.length else it
                }
                val space=text.indexOf(' ',lineStart).let {
                    if(it<0 || it>=end)end else it
                }
                out.color(lineStart,space,0xFFCAD4E2.toInt())
                lineStart=end+1
            }
        }else if(normalized==HudLayoutPolicy.TACTICAL) {
            for(label in arrayOf("THERMALS","UTILIZATION","RESOURCES")){
                val idx=text.indexOf(label)
                if(idx>=0){
                    out.color(idx,idx+label.length,cyan)
                    out.size(idx,idx+label.length,.76f)
                    out.bold(idx,idx+label.length)
                }
            }
        }else if(normalized==HudLayoutPolicy.STRIKER){
            val title=text.indexOf("CM / LIVE")
            if(title>=0){
                out.color(title,title+9,cyan)
                out.size(title,title+9,.78f)
                out.bold(title,title+9)
            }
            for(word in arrayOf("CPU","GPU","BAT","PWR","RAM")){
                var index=0
                while(true){
                    val start=text.indexOf(word,index)
                    if(start<0)break
                    val atLine=start==0 || text[start-1]=='\n'
                    if(atLine)out.color(start,start+word.length,paleBlue)
                    index=start+word.length
                }
            }
        }else if(normalized==HudLayoutPolicy.STRIP){
            for(word in arrayOf("CPU T:","GPU T:","CPU %:","GPU %:","RAM:","BAT:")){
                var from=0
                while(true){
                    val idx=text.indexOf(word,from)
                    if(idx<0)break
                    out.color(idx,idx+word.length,frost)
                    from=idx+word.length
                }
            }
        }else {
            // Ghost: subtle dark drop shadow is supplied by the TextView;
            // readouts remain high-contrast white, separators cool grey.
            for(i in text.indices){
                if(text[i]=='|')out.color(i,i+1,frost)
            }
        }
        if(cpuTemp!=null && advisoryBand>0) {
            val index=text.indexOf(cpuTemp)
            if(index>=0){
                out.color(index,index+cpuTemp.length,
                    if(advisoryBand>=2)hot else warm)
                out.bold(index,index+cpuTemp.length)
            }
        }
        return out
    }
}
