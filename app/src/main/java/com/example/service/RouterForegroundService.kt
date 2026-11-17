package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.HotspotStatus
import com.example.data.RouterProfile
import com.example.network.CellularMonitor
import com.example.network.HotspotController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class RouterForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var cellularMonitor: CellularMonitor
    private lateinit var hotspotController: HotspotController
    private var notifJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getCellularMonitor(): CellularMonitor = cellularMonitor
        fun getHotspotController(): HotspotController = hotspotController
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        cellularMonitor = CellularMonitor(applicationContext, serviceScope)
        hotspotController = HotspotController(applicationContext, serviceScope, cellularMonitor)
        cellularMonitor.startMonitoring(pinCellular = true)
        hotspotController.startPassiveMonitoring()
        createNotificationChannel()
        observeForNotificationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_BROADCAST -> {
                hotspotController.stopBroadcast()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }

            ACTION_PROMOTE_FOREGROUND -> {
                val notification = buildNotification(
                    ssid = intent.getStringExtra(EXTRA_SSID) ?: RouterProfile.DEFAULT_SSID,
                    detail = "Раздача Wi-Fi • WPA2-PSK",
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        notifJob?.cancel()
        hotspotController.stopBroadcast()
        hotspotController.stopPassiveMonitoring()
        cellularMonitor.stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeForNotificationUpdates() {
        notifJob = serviceScope.launch {
            combine(
                hotspotController.hotspotStatus,
                hotspotController.connectedClients,
                cellularMonitor.cellularTelemetry,
            ) { status, clients, cellular ->
                Triple(status, clients.size, cellular.networkTypeLabel)
            }.collect { (status, clientCount, netType) ->
                if (status is HotspotStatus.Broadcasting) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val detail = "$netType • Клиентов: $clientCount • Шлюз ${status.gatewayIp}:${status.proxyPort ?: 8888}"
                    manager.notify(NOTIFICATION_ID, buildNotification(status.actualSsid, detail))
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(ssid: String, detail: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, RouterForegroundService::class.java).apply {
            action = ACTION_STOP_BROADCAST
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_router_notif)
            .setContentTitle("4G Роутер: $ssid")
            .setContentText(detail)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.notif_stop_action), stopPending)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "router_4g_channel"
        const val NOTIFICATION_ID = 4041
        const val ACTION_PROMOTE_FOREGROUND = "com.example.action.PROMOTE_FOREGROUND"
        const val ACTION_STOP_BROADCAST = "com.example.action.STOP_BROADCAST"
        const val EXTRA_SSID = "extra_ssid"

        fun promoteForeground(context: Context, ssid: String) {
            val intent = Intent(context, RouterForegroundService::class.java).apply {
                action = ACTION_PROMOTE_FOREGROUND
                putExtra(EXTRA_SSID, ssid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
