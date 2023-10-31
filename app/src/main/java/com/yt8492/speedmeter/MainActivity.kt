package com.yt8492.speedmeter

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.yt8492.speedmeter.ui.theme.SpeedMeterTheme

class MainActivity : ComponentActivity() {
  private lateinit var getSpeedService: GetSpeedService
  private var bound = false
  private val speed = mutableDoubleStateOf(0.0)
  private val speedChangedListener = SpeedChangedListener {
    speed.doubleValue = it
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SpeedMeterTheme {
        // A surface container using the 'background' color from the theme
        Surface {
          Column {
            RequestPermission(
              permissions = listOfNotNull(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
              ),
              grantedContent = {
                Text(text = "Speed: %.1fkm/h".format(speed.doubleValue))
              },
              notGrantedContent = {
                Text(text = "許可されませんでした。")
                Text(text = it.joinToString())
              }
            )
          }
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    Intent(this, GetSpeedService::class.java).also { intent ->
      bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
  }

  override fun onStop() {
    super.onStop()
    getSpeedService.removeSpeedChangedListener(speedChangedListener)
    unbindService(connection)
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermission(
  permissions: List<String>,
  grantedContent: @Composable () -> Unit,
  notGrantedContent: @Composable (List<String>) -> Unit,
) {
  val context = LocalContext.current
  val notGrantedPermissions = permissions.filter {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_DENIED
  }
  val permissionsState = rememberMultiplePermissionsState(permissions = notGrantedPermissions)
  if (permissionsState.allPermissionsGranted) {
    grantedContent()
  } else {
    LaunchedEffect(permissionsState.revokedPermissions) {
      permissionsState.launchMultiplePermissionRequest()
    }
    notGrantedContent(permissionsState.revokedPermissions.map { it.permission })
  }
}