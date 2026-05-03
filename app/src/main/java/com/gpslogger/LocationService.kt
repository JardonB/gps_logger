package com.gpslogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationService : Service() {

    companion object {
        const val ACTION_START = "com.gpslogger.ACTION_START"
        const val ACTION_STOP = "com.gpslogger.ACTION_STOP"
        const val ACTION_LOCATION_UPDATE = "com.gpslogger.LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ELEVATION = "elevation"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_POINT_COUNT = "point_count"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "gps_logger_channel"
        const val PREF_INTERVAL = "logging_interval"
        const val PREF_LOG_LENGTH = "log_length"
        // Minimum update interval is half the requested interval to allow slight early updates
        private const val MIN_INTERVAL_DIVISOR = 2L
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var gpxWriter: GpxWriter? = null
    private var gpxFile: File? = null
    private var startTimeMs: Long = 0L
    private var maxDurationMs: Long = 0L
    private var pointCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLogging()
            ACTION_STOP -> stopLogging()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLogging() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val intervalMs = prefs.getString(PREF_INTERVAL, "10000")?.toLongOrNull() ?: 10000L
        maxDurationMs = prefs.getString(PREF_LOG_LENGTH, "0")?.toLongOrNull() ?: 0L
        startTimeMs = System.currentTimeMillis()
        pointCount = 0

        val dir = getExternalFilesDir(null) ?: filesDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        gpxFile = File(dir, "track_$timestamp.gpx")
        gpxWriter = GpxWriter(gpxFile!!)
        gpxWriter?.startTrack("Track $timestamp")

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                onLocationReceived(location)
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / MIN_INTERVAL_DIVISOR)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_logging)))
    }

    private fun onLocationReceived(location: Location) {
        if (maxDurationMs > 0 && System.currentTimeMillis() - startTimeMs >= maxDurationMs) {
            stopLogging()
            return
        }

        gpxWriter?.addTrackPoint(
            lat = location.latitude,
            lon = location.longitude,
            ele = location.altitude,
            time = location.time
        )
        pointCount++

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(getString(
                R.string.notification_update,
                pointCount,
                "%.5f".format(location.latitude),
                "%.5f".format(location.longitude)
            ))
        )

        val broadcastIntent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_ELEVATION, location.altitude)
            putExtra(EXTRA_FILE_PATH, gpxFile?.absolutePath ?: "")
            putExtra(EXTRA_POINT_COUNT, pointCount)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
    }

    private fun stopLogging() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        gpxWriter?.endTrack()
        gpxWriter = null

        val broadcastIntent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_FILE_PATH, gpxFile?.absolutePath ?: "")
            putExtra(EXTRA_POINT_COUNT, pointCount)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_logging), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Logger",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "GPS logging status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        gpxWriter?.endTrack()
        gpxWriter = null
    }
}
