package com.das.mobile.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DataChange { VISIT, VISIT_PLAN, SETTLEMENT }

/**
 * Repositories publish here after a successful mutation, so list and detail ViewModels
 * refresh themselves without screens passing results back through navigation.
 */
@Singleton
class ChangeNotifier @Inject constructor() {
    private val _changes = MutableSharedFlow<DataChange>(extraBufferCapacity = 16)
    val changes: SharedFlow<DataChange> = _changes.asSharedFlow()

    fun notify(change: DataChange) {
        _changes.tryEmit(change)
    }
}
