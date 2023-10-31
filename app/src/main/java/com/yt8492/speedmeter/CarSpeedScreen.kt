package com.yt8492.speedmeter

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class CarSpeedScreen(
    carContext: CarContext,
) : Screen(carContext) {
  private var speed = 0.0

  override fun onGetTemplate(): Template {
    val row = Row.Builder()
      .setTitle("Speed meter")
      .addText("%.1fkm/h".format(speed))
      .build()
    val pane = Pane.Builder()
      .addRow(row)
      .build()
    return PaneTemplate.Builder(pane)
      .setHeaderAction(Action.APP_ICON)
      .build()
  }

  fun updateSpeed(speed: Double) {
    this.speed = speed
    invalidate()
  }
}
