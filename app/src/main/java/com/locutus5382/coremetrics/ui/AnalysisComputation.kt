package com.locutus5382.coremetrics.ui

/** Explicit state prevents missing-data/invalid-range failures from looking
 * like calculations that are permanently loading.
 */
internal sealed interface AnalysisComputation<out T> {
    data object Loading : AnalysisComputation<Nothing>
    data class Ready<T>(val result:T):AnalysisComputation<T>
    data class Failed(val message:String):AnalysisComputation<Nothing>
}
