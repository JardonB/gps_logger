package com.gpslogger

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.gpslogger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private val NO_VALUE = Double.NaN
    }

    private lateinit var binding: ActivityMainBinding
    private var isLogging = false
    private var currentFilePath: String = ""

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val lat = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, NO_VALUE)
            val lon = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, NO_VALUE)
            val ele = intent.getDoubleExtra(LocationService.EXTRA_ELEVATION, NO_VALUE)
            val filePath = intent.getStringExtra(LocationService.EXTRA_FILE_PATH) ?: ""
            val count = intent.getIntExtra(LocationService.EXTRA_POINT_COUNT, 0)

            if (filePath.isNotEmpty()) {
                currentFilePath = filePath
                binding.tvFilePath.text = getString(R.string.file_path_label, filePath)
            }
            if (!lat.isNaN() && !lon.isNaN()) {
                binding.tvCoordinates.text = getString(
                    R.string.coordinates_label,
                    "%.6f".format(lat),
                    "%.6f".format(lon),
                    "%.1f".format(ele)
                )
            }
            binding.tvPointCount.text = getString(R.string.points_logged_label, count)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            requestBackgroundLocationIfNeeded()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.btnStartStop.setOnClickListener {
            if (isLogging) stopLogging() else startLogging()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter(LocationService.ACTION_LOCATION_UPDATE)
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startLogging() {
        if (!hasLocationPermission()) {
            requestLocationPermissions()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        isLogging = true
        updateUI()
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, R.string.logging_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopLogging() {
        isLogging = false
        updateUI()
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, R.string.logging_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        if (isLogging) {
            binding.btnStartStop.text = getString(R.string.stop_logging)
            binding.tvStatus.text = getString(R.string.status_logging)
        } else {
            binding.btnStartStop.text = getString(R.string.start_logging)
            binding.tvStatus.text = getString(R.string.status_idle)
            binding.tvCoordinates.text = getString(R.string.coordinates_none)
            binding.tvPointCount.text = ""
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        requestPermissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.background_permission_hint, Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }
}
