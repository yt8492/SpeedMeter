package com.yt8492.speedmeter

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class CarSpeedSession : Session() {
  private var carSpeedScreen: CarSpeedScreen? = null

  override fun onCreateScreen(intent: Intent): Screen {
    val screen = CarSpeedScreen(carContext)
    carSpeedScreen = screen
    return screen
  }

  fun updateSpeed(speed: Double) {
    carSpeedScreen?.updateSpeed(speed)
  }
}
