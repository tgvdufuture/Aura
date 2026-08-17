package com.aura.led.notification

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide connection state of [AuraNotificationListener], kept in a singleton so
 * the UI can surface whether notifications actually reach the LED pipeline. HyperOS
 * sometimes leaves the listener unbound after a reboot or a process kill while the
 * system setting still says "allowed", which silently disables every LED.
 */
object NotificationListenerState {
    val connected = MutableStateFlow(false)
}
