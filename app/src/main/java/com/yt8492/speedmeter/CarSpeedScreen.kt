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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
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
  val accentColor = when {
    speed < 30.0 -> Color(0xFF4CD964)
    speed < 80.0 -> Color(0xFFFFC145)
    speed < 120.0 -> Color(0xFFFF8C42)
    else -> Color(0xFFFF5A5F)
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        brush = Brush.linearGradient(
          colors = listOf(Color(0xFF07111E), Color(0xFF10233A), Color(0xFF182D48)),
        )
      )
      .padding(32.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(28.dp))
        .background(Color(0x1AFFFFFF))
        .padding(28.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = "Compose Surface PoC",
        color = Color(0xFFB8C8DC),
        style = MaterialTheme.typography.titleLarge,
      )

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = speed.toInt().toString(),
          color = Color.White,
          fontSize = 88.sp,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "km/h",
          color = Color(0xFFB8C8DC),
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = speedBand(speed),
          color = accentColor,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Text(
        text = "Rendered with ComposeView inside Presentation on VirtualDisplay",
        color = Color(0xFFA8B7CB),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

private fun speedBand(speed: Double): String {
  return when {
    speed < 1.0 -> "Stopped"
    speed < 30.0 -> "Rolling"
    speed < 80.0 -> "Cruise"
    speed < 120.0 -> "Fast Lane"
    else -> "High Speed"
  }
}
