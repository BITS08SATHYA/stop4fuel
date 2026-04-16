package com.stopforfuel.app.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InAppNotification(
    val title: String,
    val body: String,
    val requestId: Long?,
)

object InAppNotificationBus {
    private val _events = MutableSharedFlow<InAppNotification>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<InAppNotification> = _events.asSharedFlow()

    fun emit(notification: InAppNotification) {
        _events.tryEmit(notification)
    }
}
