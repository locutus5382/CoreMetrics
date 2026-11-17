package com.locutus5382.coremetrics.ui

import android.content.Context
import android.app.ActivityManager
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locutus5382.coremetrics.analysis.DukoComparisonLoader
import com.locutus5382.coremetrics.analysis.DukoReportLoader
import com.locutus5382.coremetrics.analysis.DukoReportScheduler
import com.locutus5382.coremetrics.analysis.DukoReportTask
import com.locutus5382.coremetrics.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Single-activity, configuration-safe report coordinator. Reports ONLY enter
 * via an explicit user button; no launch on app start, service, timer or REC.
 * The currently displayed SAF picker is also a serialized queue turn.
 *
 * ViewModel scope survives Activity rotation but is cancelled when the UI
 * finishes permanently. It never starts a background Android service.
 */
internal class DukoExportQueueViewModel:ViewModel() {
    private val scheduler=DukoReportScheduler(12)
    var current by mutableStateOf<DukoReportTask?>(null)
        private set
    var waitingCount by mutableIntStateOf(0)
        private set
    var pickerLaunched by mutableStateOf(false)
        private set
    var percent by mutableIntStateOf(0)
        private set
    var stage by mutableStateOf("")
        private set
    var resultMessage by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    private var work:Job?=null
    private var cancelBeforeDocument=false

    private fun publish() {
        current=scheduler.active
        waitingCount=scheduler.waitingCount
    }

    fun enqueue(task:DukoReportTask):Boolean {
        val added=scheduler.submit(task)
        if(!added) {
            resultMessage="DUKO report queue is full (12 waiting); "+
                "finish or remove a request first."
            return false
        }
        publish()
        if(!busy && !pickerLaunched) {
            stage="Choose a save location for "+current?.title
            percent=0
        }
        resultMessage=task.title+" queued. Requests are processed in click order."
        return true
    }

    fun markPickerLaunched(task:DukoReportTask):Boolean {
        if(current!==task || pickerLaunched || busy)return false
        pickerLaunched=true
        stage="Choose destination · "+task.title
        return true
    }

    private fun complete(task:DukoReportTask) {
        if(scheduler.active!==task)return
        scheduler.complete(task)
        pickerLaunched=false
        cancelBeforeDocument=false
        busy=false
        percent=0
        stage=if(scheduler.active==null)"" else
            "Next queued report · "+scheduler.active!!.title
        publish()
    }

    fun cancelCurrent() {
        val task=current?:return
        val running=work
        if(running!=null) {
            stage="Cancelling "+task.title+"…"
            running.cancel(CancellationException("User cancelled DUKO report"))
        }else if(pickerLaunched) {
            cancelBeforeDocument=true
            stage="Cancellation requested: close the Android document picker."
        }else {
            resultMessage=task.title+" cancelled before opening destination."
            complete(task)
        }
    }

    fun cancelQueued(index:Int) {
        val removed=scheduler.cancelWaiting(index)?:return
        publish()
        resultMessage=removed.title+" removed from the queue."
    }

    fun waitingTitles():List<String> =
        scheduler.waitingSnapshot.map {it.title}

    /** Called only in response to one registered Android SAF chooser result. */
    fun onDocumentResult(
        kind:DukoReportTask.Kind,format:String,uri:Uri?,
        context:Context,db:SessionStore
    ) {
        val resolver=context.applicationContext.contentResolver
        val appContext=context.applicationContext
        val task=current
        if(task==null || task.kind!=kind || task.format!=format ||
            !pickerLaunched) {
            if(uri!=null)viewModelScope.launch(Dispatchers.IO) {
                runCatching { resolver.delete(uri,null,null) }
            }
            return
        }
        if(uri==null) {
            resultMessage=task.title+" destination cancelled."
            complete(task)
            return
        }
        if(cancelBeforeDocument) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { resolver.delete(uri,null,null) }
                }
                resultMessage=task.title+" cancelled."
                complete(task)
            }
            return
        }
        busy=true
        stage="Analyzing · "+task.title
        percent=0
        // Enter try/finally synchronously before onDocumentResult returns.
        // Otherwise a rapid Cancel can cancel a not-yet-dispatched job and
        // strand the FIFO request with busy=true indefinitely.
        val job=viewModelScope.launch(start=CoroutineStart.UNDISPATCHED) {
            val startedAt=SystemClock.elapsedRealtime()
            try {
                val metrics=withContext(Dispatchers.IO) {
                    // Early warning only. PSS is still logged during report
                    // construction; lowMemory can change after this check.
                    val memory=ActivityManager.MemoryInfo()
                    appContext.getSystemService(ActivityManager::class.java)
                        ?.getMemoryInfo(memory)
                    if(memory.lowMemory)
                        error("Android reports low memory. Close the game " +
                            "or other apps and retry the report.")
                    val worker=coroutineContext[Job]
                    var latest=-1
                    var lastMemoryBucket=-1
                    var highestPssKiB=0L
                    var highestJavaHeapBytes=0L
                    fun sampleMemory(percent:Int,force:Boolean=false) {
                        // Samples at 10% increments DURING a user-requested
                        // export only. This is approximate PSS, NOT exact peak
                        // working set or a background monitor.
                        val bucket=percent/10
                        if(!force && bucket<=lastMemoryBucket)return
                        lastMemoryBucket=bucket
                        val heap=Runtime.getRuntime().let{
                            it.totalMemory()-it.freeMemory()
                        }
                        if(heap>highestJavaHeapBytes)highestJavaHeapBytes=heap
                        val pss=runCatching{Debug.getPss()}.getOrDefault(0L)
                        if(pss>highestPssKiB)highestPssKiB=pss
                    }
                    fun checkpoint() { worker?.ensureActive() }
                    fun progress(value:Int,label:String) {
                        val v=value.coerceIn(0,99)
                        if(v<=latest)return
                        latest=v
                        sampleMemory(v)
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            if(current===task) {
                                percent=v
                                stage=label
                            }
                        }
                    }
                    val generated=when(task.kind) {
                        DukoReportTask.Kind.SESSION -> {
                            val report=DukoReportLoader.build(
                                db,task.ids.single(),
                                checkpoint={checkpoint()},
                                onProgress={n,total->
                                    progress(5+(n*79L/total.coerceAtLeast(1L)).toInt(),
                                        "Analyzing saved session #"+task.ids.single())
                                })
                            checkpoint()
                            progress(91,"Rendering "+task.format.uppercase()+"…")
                            resolver.openOutputStream(uri,"w")?.use {stream ->
                                if(task.format=="pdf")
                                    DukoSessionRenderer.pdf(report,stream,::checkpoint)
                                else DukoSessionRenderer.png(report,stream,::checkpoint)
                            }?:error("Cannot open selected document destination")
                            report.sampleCount.toString()+" stored samples"
                        }
                        DukoReportTask.Kind.COMPARISON -> {
                            val report=DukoComparisonLoader.build(
                                db,task.ids,task.baselineId,task.variable,
                                checkpoint={checkpoint()},
                                onProgress={completed,total,runPct->
                                    val p=(completed*79+runPct*79/100)/
                                        total.coerceAtLeast(1)
                                    progress(5+p,
                                        "Analyzing recording "+(completed+1)+
                                            "/"+total)
                                })
                            checkpoint()
                            progress(91,"Rendering comparison "+task.format.uppercase()+"…")
                            resolver.openOutputStream(uri,"w")?.use {stream ->
                                if(task.format=="pdf")
                                    DukoComparisonRenderer.pdf(report,stream,::checkpoint)
                                else DukoComparisonRenderer.png(report,stream,::checkpoint)
                            }?:error("Cannot open selected document destination")
                            report.runs.size.toString()+" compared recordings"
                        }
                    }
                    checkpoint()
                    progress(99,"Saving report…")
                    sampleMemory(100,force=true)
                    Triple(generated,highestPssKiB,highestJavaHeapBytes)
                }
                val seconds=(SystemClock.elapsedRealtime()-startedAt)/1000.0
                percent=100
                val pss=if(metrics.second>0L)
                    " · sampled PSS up to "+(metrics.second/1024L)+" MiB"
                    else ""
                val heap=if(metrics.third>0L)
                    " · sampled Java heap up to "+(metrics.third/1_048_576L)+
                        " MiB" else ""
                resultMessage=task.title+" saved ("+metrics.first+
                    "), "+String.format(java.util.Locale.US,"%.1f",seconds)+
                    " s"+pss+heap+"; samples only, not verified peak."
            }catch(e:CancellationException) {
                val removed=withContext(NonCancellable+Dispatchers.IO) {
                    runCatching {resolver.delete(uri,null,null)>0}
                        .getOrDefault(false)
                }
                resultMessage=task.title+" cancelled"+
                    if(removed) "." else "; check destination for a partial file."
            }catch(e:OutOfMemoryError) {
                // Avoid an uncaught coroutine error that would kill the UI
                // and leave a partially written SAF document.
                val removed=withContext(NonCancellable+Dispatchers.IO) {
                    runCatching {resolver.delete(uri,null,null)>0}
                        .getOrDefault(false)
                }
                resultMessage=task.title+
                    " stopped: insufficient available memory."+
                    if(removed) " The partial report was removed."
                    else " Check the destination for a partial file."
            }catch(e:Exception) {
                val removed=withContext(NonCancellable+Dispatchers.IO) {
                    runCatching {resolver.delete(uri,null,null)>0}
                        .getOrDefault(false)
                }
                resultMessage=task.title+" failed: "+
                    (e.message?:e.javaClass.simpleName)+
                    if(removed) "" else "; check destination for partial output."
            }finally {
                work=null
                complete(task)
            }
        }
        work=job
    }
}
