package com.yt8492.speedmeter

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.yt8492.speedmeter.ui.theme.SpeedMeterTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            RequestPermission(
              permissions = listOfNotNull(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
              ),
              grantedContent = {
                SpeedMeterDashboard(speed = speed.doubleValue)
              },
              notGrantedContent = {
                PermissionDeniedScreen(deniedPermissions = it)
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

@Composable
fun SpeedMeterDashboard(speed: Double) {
  val maxSpeed = 180f
  val clampedSpeed = speed.coerceIn(0.0, maxSpeed.toDouble()).toFloat()
  val animatedSpeed by animateFloatAsState(
    targetValue = clampedSpeed,
    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
    label = "speedometer_speed",
  )
  val accentColor = when {
    animatedSpeed < 40f -> Color(0xFF4CD964)
    animatedSpeed < 100f -> Color(0xFFFFC145)
    animatedSpeed < 140f -> Color(0xFFFF8C42)
    else -> Color(0xFFFF5A5F)
  }
  val progress = animatedSpeed / maxSpeed

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(Color(0xFF08111F), Color(0xFF101C31), Color(0xFF17263F)),
        ),
      )
      .padding(horizontal = 20.dp, vertical = 28.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Speed Meter",
          style = MaterialTheme.typography.titleMedium,
          color = Color(0xFF9CB3D1),
        )
        Text(
          text = speedBandLabel(animatedSpeed),
          style = MaterialTheme.typography.headlineSmall,
          color = Color.White,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 420.dp)
          .weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .size(360.dp),
        ) {
          val strokeWidth = size.minDimension * 0.075f
          val outerRadius = size.minDimension / 2.15f
          val arcSize = Size(outerRadius * 2, outerRadius * 2)
          val arcTopLeft = Offset(
            x = center.x - outerRadius,
            y = center.y - outerRadius,
          )
          val startAngle = 150f
          val sweepAngle = 240f
          val labelRadius = outerRadius - strokeWidth * 1.8f
          val tickOuterRadius = outerRadius - strokeWidth * 0.3f

          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(Color(0xFF22324A), Color(0xFF0B1322)),
              center = center,
              radius = size.minDimension / 1.6f,
            ),
            radius = size.minDimension / 2.35f,
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
            val angle = Math.toRadians((startAngle + (sweepAngle * (tickValue / maxSpeed))).toDouble())
            val isMajorTick = index % 2 == 0
            val tickLength = if (isMajorTick) strokeWidth * 1.15f else strokeWidth * 0.65f
            val tickStroke = if (isMajorTick) 7f else 4f
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
              val label = (index * 15).toString()
              val labelPoint = Offset(
                x = center.x + cos(angle).toFloat() * labelRadius,
                y = center.y + sin(angle).toFloat() * labelRadius,
              )
              drawContext.canvas.nativeCanvas.drawText(
                label,
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

          val needleAngle = Math.toRadians((startAngle + (sweepAngle * progress)).toDouble())
          val needleLength = outerRadius - strokeWidth * 1.65f
          val needleTip = Offset(
            x = center.x + cos(needleAngle).toFloat() * needleLength,
            y = center.y + sin(needleAngle).toFloat() * needleLength,
          )
          val needleTail = Offset(
            x = center.x - cos(needleAngle).toFloat() * strokeWidth * 0.9f,
            y = center.y - sin(needleAngle).toFloat() * strokeWidth * 0.9f,
          )

          drawLine(
            color = accentColor,
            start = needleTail,
            end = needleTip,
            strokeWidth = strokeWidth * 0.42f,
            cap = StrokeCap.Round,
          )
          drawCircle(
            color = Color(0xFFF1F5F9),
            radius = strokeWidth * 0.55f,
            center = center,
          )
          drawCircle(
            color = Color(0xFF111A29),
            radius = strokeWidth * 0.27f,
            center = center,
          )
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(top = 144.dp),
        ) {
          Text(
            text = animatedSpeed.toInt().toString(),
            color = Color.White,
            fontSize = 60.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 60.sp,
          )
          Text(
            text = "km/h",
            color = Color(0xFFB7C6DA),
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(28.dp))
          .background(Color(0x1FFFFFFF))
          .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DashboardMetric(
          label = "Current",
          value = "%.1f".format(speed.coerceAtLeast(0.0)),
        )
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(accentColor),
        )
        DashboardMetric(
          label = "Zone",
          value = speedBandLabel(animatedSpeed),
          textAlign = TextAlign.End,
        )
      }
    }
  }
}

@Composable
private fun DashboardMetric(
  label: String,
  value: String,
  textAlign: TextAlign = TextAlign.Start,
) {
  Column(horizontalAlignment = if (textAlign == TextAlign.End) Alignment.End else Alignment.Start) {
    Text(
      text = label,
      color = Color(0xFF9CB3D1),
      style = MaterialTheme.typography.labelMedium,
      textAlign = textAlign,
    )
    Text(
      text = value,
      color = Color.White,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
      textAlign = textAlign,
    )
  }
}

@Composable
private fun PermissionDeniedScreen(deniedPermissions: List<String>) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF111827))
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(28.dp))
        .background(Color(0xFF1F2937))
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Location Permission Required",
        style = MaterialTheme.typography.headlineSmall,
        color = Color.White,
        textAlign = TextAlign.Center,
      )
      Text(
        text = "GPS speed cannot be displayed until location access is granted.",
        style = MaterialTheme.typography.bodyLarge,
        color = Color(0xFFD1D5DB),
        textAlign = TextAlign.Center,
      )
      Text(
        text = deniedPermissions.joinToString(separator = "\n"),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF93C5FD),
        textAlign = TextAlign.Center,
      )
    }
  }
}

private fun speedBandLabel(speed: Float): String {
  return when {
    speed < 1f -> "Stopped"
    speed < 30f -> "Rolling"
    speed < 80f -> "Cruise"
    speed < 120f -> "Fast Lane"
    speed < 150f -> "High Speed"
    else -> "Red Zone"
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
