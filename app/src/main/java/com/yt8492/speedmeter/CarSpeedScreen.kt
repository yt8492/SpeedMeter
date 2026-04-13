package com.yt8492.speedmeter

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class CarSpeedScreen(
    carContext: CarContext,
) : Screen(carContext) {
  companion object {
    private const val MAX_DISPLAY_SPEED_KMH = 180.0
    private const val GAUGE_SEGMENTS = 18
  }

  private var speed = 0.0

  override fun onGetTemplate(): Template {
    val speedRow = Row.Builder()
      .setTitle("Current speed")
      .addText(formatSpeed(speed))
      .addText(speedZoneLabel(speed))
      .build()
    val gaugeRow = Row.Builder()
      .setTitle("Speed gauge")
      .addText(buildGauge(speed))
      .build()
    val statusRow = Row.Builder()
      .setTitle("Drive status")
      .addText(speedZoneDescription(speed))
      .addText(buildStatusSummary(speed))
      .build()
    val pane = Pane.Builder()
      .addRow(speedRow)
      .addRow(gaugeRow)
      .addRow(statusRow)
      .build()
    val header = Header.Builder()
      .setStartHeaderAction(Action.APP_ICON)
      .setTitle("Speed meter")
      .build()
    return PaneTemplate.Builder(pane)
      .setHeader(header)
      .build()
  }

  fun updateSpeed(speed: Double) {
    this.speed = speed
    invalidate()
  }

  private fun formatSpeed(speed: Double): String {
    return "%.1f km/h".format(speed.coerceAtLeast(0.0))
  }

  private fun speedZoneLabel(speed: Double): String {
    return when {
      speed < 1.0 -> "Stopped"
      speed < 30.0 -> "Low speed zone"
      speed < 80.0 -> "City cruising"
      speed < 120.0 -> "Open road"
      speed < 140.0 -> "High speed"
      else -> "Watch your speed"
    }
  }

  private fun speedZoneDescription(speed: Double): String {
    return when {
      speed < 1.0 -> "Waiting for movement"
      speed < 30.0 -> "Gentle acceleration"
      speed < 80.0 -> "Balanced driving range"
      speed < 120.0 -> "Stable cruising range"
      speed < 140.0 -> "Approaching upper range"
      else -> "Near the top of the meter"
    }
  }

  private fun buildGauge(speed: Double): String {
    val normalizedSpeed = speed.coerceIn(0.0, MAX_DISPLAY_SPEED_KMH)
    val filledSegments = ((normalizedSpeed / MAX_DISPLAY_SPEED_KMH) * GAUGE_SEGMENTS).toInt()
    val emptySegments = GAUGE_SEGMENTS - filledSegments
    return "[" + "=".repeat(filledSegments) + "-".repeat(emptySegments) + "]"
  }

  private fun buildStatusSummary(speed: Double): String {
    return "Current reading ${formatSpeed(speed)} / scale max ${MAX_DISPLAY_SPEED_KMH.toInt()} km/h"
  }
}
