// VlmServiceConnection.kt
package com.jabby.vlm.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper class to manage binding to the VlmService.
 * Provides a StateFlow to observe connection status.
 *
 * Usage:
 *   val vlmConnection = VlmServiceConnection(context)
 *   vlmConnection.bind()
 *   vlmConnection.connected.collect { if (it) vlmConnection.getService()?.... }
 */
class VlmServiceConnection(private val context: Context) {

    companion object {
        private const val TAG = "VlmServiceConnection"
    }

    private var service: IVlmService? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "Connected to VlmService")
            service = IVlmService.Stub.asInterface(binder)
            _connected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Disconnected from VlmService")
            service = null
            _connected.value = false
        }
    }

    /**
     * Bind to the VlmService.
     * @return true if binding was initiated successfully
     */
    fun bind(): Boolean {
        val intent = Intent("com.jabby.vlm.service.BIND").apply {
            setPackage(context.packageName) // Or target package for cross-app
        }
        // Also explicitly start the service as a foreground service. With just
        // BIND_AUTO_CREATE, the service dies whenever the Activity that bound
        // to it goes through stop/start (rotation, brief background, etc.) —
        // foreground notification alone doesn't keep a bind-only service alive.
        // startForegroundService gives the service independent lifetime; bind
        // is then purely for the AIDL channel.
        try { context.startForegroundService(intent) } catch (e: Throwable) {
            Log.w(TAG, "startForegroundService failed (continuing with bind only)", e)
        }
        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Unbind from the VlmService.
     */
    fun unbind() {
        try {
            context.unbindService(connection)
            _connected.value = false
            service = null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Service not registered", e)
        }
    }

    /**
     * Get the IVlmService interface for making AIDL calls.
     * @return IVlmService instance if connected, null otherwise
     */
    fun getService(): IVlmService? = service
}
