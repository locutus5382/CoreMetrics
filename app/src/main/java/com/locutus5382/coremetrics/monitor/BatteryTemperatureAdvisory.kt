package com.locutus5382.coremetrics.monitor

/**
 * Passive, app-defined BAT T warmth advisories. These thresholds are NOT
 * manufacturer safety limits. Consumes existing monotonic sampler timestamps:
 * no timer, independent polling, database write, or hardware control.
 */
internal class BatteryTemperatureAdvisory {
    enum class State { UNAVAILABLE, NORMAL, WARM, HOT }
    private var latched=State.NORMAL
    private var lastAtMs:Long?=null
    private var hotSince:Long?=null
    private var warmReleaseSince:Long?=null
    private var hotReleaseSince:Long?=null

    fun reset() {
        latched=State.NORMAL
        lastAtMs=null
        clearTransitions()
    }

    private fun clearTransitions() {
        hotSince=null;warmReleaseSince=null;hotReleaseSince=null
    }

    /**
     * - Below 39 C: no BAT T border once NORMAL.
     * - >=39 C: yellow WARM border.
     * - >42 C continuously for >=15s: orange HOT.
     * - HOT -> WARM after <41 C continuously for >=10s.
     * - WARM -> NORMAL after <38 C continuously for >=10s.
     *
     * Uses actual time deltas, not counts. Invalid readings and sampling
     * interruptions reset candidate timers, never imply a normal/safe state.
     * The latest valid state is latched for subsequent readings.
     */
    fun update(temperatureC:Double?,elapsedMs:Long?):State {
        val valid=temperatureC?.takeIf { it.isFinite() && it in -10.0..90.0 }
        if(valid==null || elapsedMs==null || elapsedMs<0L) {
            lastAtMs=null
            clearTransitions()
            return State.UNAVAILABLE
        }
        val previous=lastAtMs
        if(previous!=null && (elapsedMs<previous || elapsedMs-previous>10_000L))
            clearTransitions()
        lastAtMs=elapsedMs
        when(latched) {
            State.NORMAL,State.UNAVAILABLE -> {
                if(valid>=39.0) {
                    latched=State.WARM
                    clearTransitions()
                    if(valid>42.0)hotSince=elapsedMs
                }
            }
            State.WARM -> {
                if(valid>42.0) {
                    if(hotSince==null)hotSince=elapsedMs
                    if(elapsedMs-(hotSince?:elapsedMs)>=15_000L) {
                        latched=State.HOT
                        clearTransitions()
                    }
                } else hotSince=null

                if(latched==State.WARM && valid<38.0) {
                    if(warmReleaseSince==null)warmReleaseSince=elapsedMs
                    if(elapsedMs-(warmReleaseSince?:elapsedMs)>=10_000L) {
                        latched=State.NORMAL
                        clearTransitions()
                    }
                }else warmReleaseSince=null
            }
            State.HOT -> {
                if(valid<41.0) {
                    if(hotReleaseSince==null)hotReleaseSince=elapsedMs
                    if(elapsedMs-(hotReleaseSince?:elapsedMs)>=10_000L) {
                        latched=State.WARM
                        clearTransitions()
                    }
                }else hotReleaseSince=null
            }
        }
        return latched
    }
}
