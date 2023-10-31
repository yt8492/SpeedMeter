package com.yt8492.speedmeter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.IBinder
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class CarSpeedService : CarAppService() {
  private lateinit var getSpeedService: GetSpeedService
  private var bound = false
  private var session: CarSpeedSession? = null
  private val speedChangedListener = SpeedChangedListener {
    session?.updateSpeed(it)
  }
  private val connection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName?, service: IBinder) {
      val binder = service as GetSpeedService.GetSpeedBinder
      getSpeedService = binder.getService()
      getSpeedService.addSpeedChangedListener(speedChangedListener)
      bound = true
    }

    override fun onServiceDisconnected(className: ComponentName?) {
      bound = false
    }
  }

  override fun createHostValidator(): HostValidator {
    return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
      HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    } else {
      HostValidator.Builder(applicationContext)
        .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
        .build()
    }
  }

  override fun onCreateSession(): Session {
    val session = CarSpeedSession()
    this.session = session
    return session
  }

  override fun onCreate() {
    super.onCreate()
    Intent(this, GetSpeedService::class.java).also { intent ->
      bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    getSpeedService.removeSpeedChangedListener(speedChangedListener)
    unbindService(connection)
    bound = false
  }
}