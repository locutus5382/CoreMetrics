package com.locutus5382.coremetrics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Theme selection uses native, focusable one-shot clickable tiles rather than
 * expensive snapshots or a web engine. All four previews are STATIC drawings.
 */
@Composable
internal fun ThemePicker(
    selected:SurfaceFinish,
    onSelect:(SurfaceFinish)->Unit
) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("APP APPEARANCE · CHOOSE A FINISH",
            color=iceBlue,fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleSmall)
        Text("Only CoreMetrics screens change. All telemetry, recording, "+
            "touch-lock, floating HUD, CSVs and analysis stay identical.",
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            SurfaceFinish.values().forEach { finish ->
                ThemePreviewTile(finish,selected==finish){ onSelect(finish) }
            }
        }
        Text("Selected: "+selected.displayName+"  ·  "+selected.description,
            color=coreSecondary,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ThemePreviewTile(finish:SurfaceFinish,selected:Boolean,onClick:()->Unit) {
    val p=finish.palette
    val radius=if(p.sharpCorners)7.dp else 16.dp
    val shape=RoundedCornerShape(radius)
    Column(Modifier.width(162.dp).height(148.dp)
        .background(Brush.verticalGradient(listOf(p.top,p.bottom)),shape)
        .border(if(selected)2.dp else 1.dp,
            if(selected)solarGold else p.borderSoft,shape)
        .cmInteractive(if(p.sharpCorners)7 else 16,
            accent=if(selected)solarGold else p.accent,onClick=onClick)
        .padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("CM",fontSize=16.sp,fontWeight=FontWeight.Black,
                color=p.accent,modifier=Modifier.weight(1f))
            Text(if(selected)"✓ ACTIVE" else "PREVIEW",
                color=if(selected)solarGold else p.muted,fontSize=9.sp,
                fontWeight=FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            val w=size.width;val h=size.height
            fun block(x:Float,y:Float,bw:Float,bh:Float,c:Color) {
                drawRoundRect(c,Offset(w*x,h*y),Size(w*bw,h*bh),
                    cornerRadius=androidx.compose.ui.geometry.CornerRadius(
                        if(p.sharpCorners)2.dp.toPx() else 6.dp.toPx()))
            }
            val cell=if(finish==SurfaceFinish.SOLAR_PULSE)
                solarOrange else p.accent.copy(alpha=.46f)
            block(.01f,.02f,.30f,.73f,cell)
            block(.36f,.02f,.62f,.35f,p.emphasisFill.copy(alpha=.75f))
            block(.36f,.44f,.62f,.31f,p.accent.copy(alpha=.42f))
            drawLine(p.border,Offset(w*.01f,h*.92f),Offset(w*.98f,h*.92f),
                1.3.dp.toPx())
            if(finish==SurfaceFinish.COBALT_MATRIX)for(i in 0..12){
                val x=w*(.02f+.96f*i/12f)
                drawLine(p.accent.copy(alpha=.5f),Offset(x,h*.80f),
                    Offset(x,h*(if(i%3==0).93f else .88f)),1.dp.toPx())
            }
            if(finish==SurfaceFinish.CRYSTAL_AERO){
                val prism=Path().apply {
                    moveTo(w*.03f,h*.65f);lineTo(w*.70f,0f)
                    lineTo(w*.93f,0f);lineTo(w*.23f,h*.76f);close()
                }
                drawPath(prism,Color.White.copy(alpha=.27f))
            }
        }
        Text(finish.displayName,color=p.foreground,
            fontWeight=FontWeight.Bold,fontSize=12.sp,maxLines=1,
            overflow=TextOverflow.Ellipsis)
        Text(when(finish){
            SurfaceFinish.FROSTED->"Soft glass · original"
            SurfaceFinish.CRYSTAL_AERO->"Optical · transparent"
            SurfaceFinish.SOLAR_PULSE->"Bold · colorful"
            SurfaceFinish.COBALT_MATRIX->"Matte · precise"
        },color=p.muted,fontSize=10.sp,maxLines=1,
            overflow=TextOverflow.Ellipsis)
    }
}
