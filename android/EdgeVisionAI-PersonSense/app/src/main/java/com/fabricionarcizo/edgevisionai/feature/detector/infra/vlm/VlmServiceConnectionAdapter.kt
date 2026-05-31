package com.fabricionarcizo.edgevisionai.feature.detector.infra.vlm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.fabricionarcizo.edgevisionai.feature.detector.domain.ports.VlmServiceConnectionPort
import com.jabby.vlm.service.IVlmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hilt-managed singleton wrapping the AIDL bind handshake for the in-process
 * VlmService. Mirrors the original `VlmServiceConnection` helper, but exposed
 * through a port so the application layer doesn't depend on Android binder
 * APIs directly.
 *
 * Note: because the manifest forces `android:process=""` on the merged service
 * entry, the bind here is in-process. We still call `startForegroundService()`
 * so the bind isn't tied to the Activity's lifetime.
 */
class VlmServiceConnectionAdapter(
    private val context: Context,
) : VlmServiceConnectionPort {

    private var service: IVlmService? = null

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

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

    override fun bind() {
        val intent = Intent(ACTION_BIND).apply { setPackage(context.packageName) }
        try {
            context.startForegroundService(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "startForegroundService failed (continuing with bind only)", e)
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun unbind() {
        try {
            context.unbindService(connection)
            _connected.value = false
            service = null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Service not registered", e)
        }
    }

    override fun getService(): IVlmService? = service

    private companion object {
        const val TAG = "VlmServiceConnAdapter"
        const val ACTION_BIND = "com.jabby.vlm.service.BIND"
    }
}
