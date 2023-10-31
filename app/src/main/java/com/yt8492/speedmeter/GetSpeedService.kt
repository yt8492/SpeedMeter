package com.yt8492.speedmeter

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class GetSpeedService : Service() {
  private val binder = GetSpeedBinder()
  private var locationProviderClient: FusedLocationProviderClient? = null
  private val speedChangedListeners = mutableListOf<SpeedChangedListener>()
  private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
      locationResult.lastLocation?.let { location ->
        speedChangedListeners.forEach {
          val speed = location.speed * 3.6
          it.onChanged(speed)
        }
      }
    }
  }

  inner class GetSpeedBinder : Binder() {
    fun getService(): GetSpeedService = this@GetSpeedService
  }

  override fun onBind(intent: Intent): IBinder {
    return binder
  }

  fun addSpeedChangedListener(listener: SpeedChangedListener) {
    speedChangedListeners.add(listener)
  }

  fun removeSpeedChangedListener(listener: SpeedChangedListener) {
    speedChangedListeners.remove(listener)
  }

  override fun onCreate() {
    super.onCreate()
    locationProviderClient = LocationServices
      .getFusedLocationProviderClient(this)
    val granted = ActivityCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) != PackageManager.PERMISSION_GRANTED &&
      ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    if (granted) {
      return
    }
    locationProviderClient?.requestLocationUpdates(
      LocationRequest
        .Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
        .build(),
      locationCallback,
      null,
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    locationProviderClient?.removeLocationUpdates(locationCallback)
  }
}
