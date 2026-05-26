package com.yt8492.speedmeter

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.doOnDetach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.yt8492.speedmeter.ui.theme.SpeedMeterTheme
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CarSpeedScreen(
    carContext: CarContext,
) : Screen(carContext) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val speedState = mutableDoubleStateOf(0.0)

  private var surfaceReady = false
  private var virtualDisplay: VirtualDisplay? = null
  private var presentation: Presentation? = null
  private var presentationOwners: PresentationOwners? = null

  init {
    carContext.getCarService(androidx.car.app.AppManager::class.java)
      .setSurfaceCallback(object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
          if (!isSurfaceReady(surfaceContainer)) {
            return
          }
          surfaceReady = true
          showComposePresentation(surfaceContainer)
        }

        override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) = Unit

        override fun onStableAreaChanged(stableArea: android.graphics.Rect) = Unit

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
          surfaceReady = false
          releasePresentation()
        }
      })
  }

  override fun onGetTemplate(): Template {
    return NavigationTemplate.Builder()
      .setActionStrip(
        ActionStrip.Builder()
          .addAction(
            Action.Builder()
              .setTitle(if (surfaceReady) "Compose Surface" else "Waiting for surface")
              .build()
          )
          .build()
      )
      .build()
  }

  fun updateSpeed(speed: Double) {
    mainHandler.post {
      speedState.doubleValue = speed
    }
  }

  private fun isSurfaceReady(surfaceContainer: SurfaceContainer): Boolean {
    return surfaceContainer.surface != null &&
      surfaceContainer.dpi != 0 &&
      surfaceContainer.width != 0 &&
      surfaceContainer.height != 0
  }

  private fun showComposePresentation(surfaceContainer: SurfaceContainer) {
    releasePresentation()

    val displayManager = carContext.getSystemService(DisplayManager::class.java)
    val display = displayManager.createVirtualDisplay(
      "speedmeter-compose-surface",
      surfaceContainer.width,
      surfaceContainer.height,
      surfaceContainer.dpi,
      surfaceContainer.surface,
      DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
    )
    virtualDisplay = display

    val composeView = ComposeView(carContext).apply {
      setContent {
        SpeedMeterTheme {
          CarComposeSurface(speed = speedState.doubleValue)
        }
      }
      doOnDetach {
        disposeComposition()
      }
    }

    val owners = PresentationOwners().apply {
      performRestore()
      handleCreate()
      handleStart()
      handleResume()
    }
    presentationOwners = owners

    presentation = Presentation(carContext, display.display).apply {
      setContentView(composeView)
      window?.setGravity(Gravity.CENTER)
      window?.decorView?.apply {
        setViewTreeLifecycleOwner(owners)
        setViewTreeViewModelStoreOwner(owners)
        setViewTreeSavedStateRegistryOwner(owners)
      }
      show()
    }
  }

  private fun releasePresentation() {
    presentation?.dismiss()
    presentation = null
    presentationOwners?.handlePause()
    presentationOwners?.handleStop()
    presentationOwners?.handleDestroy()
    presentationOwners = null
    virtualDisplay?.release()
    virtualDisplay = null
  }
}

private class PresentationOwners : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this)
  private val viewModelStoreInstance = ViewModelStore()

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateController.savedStateRegistry

  override val viewModelStore: ViewModelStore
    get() = viewModelStoreInstance

  fun performRestore() {
    savedStateController.performRestore(Bundle())
  }

  fun handleCreate() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
  }

  fun handleStart() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
  }

  fun handleResume() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun handlePause() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  }

  fun handleStop() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
  }

  fun handleDestroy() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    viewModelStoreInstance.clear()
  }
}

@Composable
private fun CarComposeSurface(speed: Double) {
  val maxSpeed = 180f
  val clampedSpeed = speed.coerceIn(0.0, maxSpeed.toDouble()).toFloat()
  val animatedSpeed by animateFloatAsState(
    targetValue = clampedSpeed,
    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
    label = "car_surface_speed",
  )
  val accentColor = carMeterAccent(animatedSpeed)
  val progress = animatedSpeed / maxSpeed

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        brush = Brush.horizontalGradient(
          colors = listOf(Color(0xFF07111E), Color(0xFF0C182B), Color(0xFF152944)),
        )
      )
      .padding(horizontal = 28.dp, vertical = 20.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(28.dp))
        .background(
          Brush.horizontalGradient(
            colors = listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF)),
          )
        )
        .padding(horizontal = 28.dp, vertical = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.width(228.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          text = "Android Auto Compose",
          color = Color(0xFF9CB3D1),
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "Speed meter",
          color = Color.White,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = carSpeedBand(animatedSpeed),
          color = accentColor,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "Live speed is rendered by ComposeView inside a Presentation-backed VirtualDisplay.",
          color = Color(0xFFB8C8DC),
          style = MaterialTheme.typography.bodyMedium,
        )

        Column(
          modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CarMetricRow("Current", "${animatedSpeed.toInt()} km/h")
          CarMetricRow("Scale", "0 - ${maxSpeed.toInt()} km/h")
          CarMetricRow("Zone", carSpeedBand(animatedSpeed))
        }
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .padding(start = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(
          modifier = Modifier.size(width = 420.dp, height = 320.dp),
        ) {
          val strokeWidth = size.minDimension * 0.08f
          val outerRadius = size.minDimension / 2.25f
          val arcSize = Size(outerRadius * 2f, outerRadius * 2f)
          val arcTopLeft = Offset(
            x = center.x - outerRadius,
            y = center.y - outerRadius,
          )
          val startAngle = 150f
          val sweepAngle = 240f
          val labelRadius = outerRadius - strokeWidth * 1.8f
          val tickOuterRadius = outerRadius - strokeWidth * 0.2f

          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(Color(0xFF22324A), Color(0xFF09121F)),
              center = center,
              radius = size.minDimension / 1.7f,
            ),
            radius = size.minDimension / 2.45f,
          )

          drawArc(
            color = Color(0xFF243247),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )

          drawArc(
            brush = Brush.sweepGradient(
              colors = listOf(Color(0xFF4CD964), Color(0xFFFFC145), Color(0xFFFF5A5F)),
              center = center,
            ),
            startAngle = startAngle,
            sweepAngle = sweepAngle * progress,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )

          for (index in 0..12) {
            val tickValue = index * 15f
            val angle = Math.toRadians((startAngle + sweepAngle * (tickValue / maxSpeed)).toDouble())
            val isMajorTick = index % 2 == 0
            val tickLength = if (isMajorTick) strokeWidth * 1.1f else strokeWidth * 0.65f
            val tickStroke = if (isMajorTick) 6f else 3.5f
            val outerPoint = Offset(
              x = center.x + cos(angle).toFloat() * tickOuterRadius,
              y = center.y + sin(angle).toFloat() * tickOuterRadius,
            )
            val innerPoint = Offset(
              x = center.x + cos(angle).toFloat() * (tickOuterRadius - tickLength),
              y = center.y + sin(angle).toFloat() * (tickOuterRadius - tickLength),
            )
            drawLine(
              color = if (tickValue <= animatedSpeed) Color.White else Color(0xFF5B6B82),
              start = outerPoint,
              end = innerPoint,
              strokeWidth = tickStroke,
              cap = StrokeCap.Round,
            )

            if (isMajorTick) {
              val labelPoint = Offset(
                x = center.x + cos(angle).toFloat() * labelRadius,
                y = center.y + sin(angle).toFloat() * labelRadius,
              )
              drawContext.canvas.nativeCanvas.drawText(
                (index * 15).toString(),
                labelPoint.x,
                labelPoint.y,
                Paint().apply {
                  color = android.graphics.Color.argb(255, 210, 221, 235)
                  textAlign = Paint.Align.CENTER
                  textSize = min(size.width, size.height) * 0.06f
                  isAntiAlias = true
                  typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                },
              )
            }
          }

          val needleAngle = Math.toRadians((startAngle + sweepAngle * progress).toDouble())
          val needleLength = outerRadius - strokeWidth * 1.55f
          val needleTip = Offset(
            x = center.x + cos(needleAngle).toFloat() * needleLength,
            y = center.y + sin(needleAngle).toFloat() * needleLength,
          )
          val needleTail = Offset(
            x = center.x - cos(needleAngle).toFloat() * strokeWidth,
            y = center.y - sin(needleAngle).toFloat() * strokeWidth,
          )

          drawLine(
            color = accentColor,
            start = needleTail,
            end = needleTip,
            strokeWidth = strokeWidth * 0.36f,
            cap = StrokeCap.Round,
          )
          drawCircle(color = Color(0xFFF1F5F9), radius = strokeWidth * 0.54f, center = center)
          drawCircle(color = Color(0xFF101A29), radius = strokeWidth * 0.26f, center = center)
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(top = 126.dp),
        ) {
          Text(
            text = animatedSpeed.toInt().toString(),
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "km/h",
            color = Color(0xFFB8C8DC),
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun CarMetricRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      color = Color(0xFF9CB3D1),
      style = MaterialTheme.typography.labelLarge,
    )
    Text(
      text = value,
      color = Color.White,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.End,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

private fun carMeterAccent(speed: Float): Color {
  return when {
    speed < 30f -> Color(0xFF4CD964)
    speed < 80f -> Color(0xFFFFC145)
    speed < 120f -> Color(0xFFFF8C42)
    else -> Color(0xFFFF5A5F)
  }
}

private fun carSpeedBand(speed: Float): String {
  return when {
    speed < 1f -> "Stopped"
    speed < 30f -> "Rolling"
    speed < 80f -> "Cruise"
    speed < 120f -> "Fast Lane"
    else -> "High Speed"
  }
}
