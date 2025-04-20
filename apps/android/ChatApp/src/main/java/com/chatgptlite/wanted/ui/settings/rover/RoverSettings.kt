package com.chatgptlite.wanted.ui.settings.rover

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatgptlite.wanted.ui.common.AppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RoverSettingsViewModel = viewModel(),
    onBackPressed: () -> Unit
) {
    var ipAddress by remember { mutableStateOf("10.0.0.120") }
    var port by remember { mutableStateOf("8000") }
    var textToSend by remember { mutableStateOf("ros2 topic list") }

//    val xCoordinate by viewModel.xCoordinate.collectAsState()
//    val yCoordinate by viewModel.yCoordinate.collectAsState()
//    val heading by viewModel.heading.collectAsState()
//    val linearVelocity by viewModel.linear_velocity.collectAsState()
//    val angularVelocity by viewModel.angular_velocity.collectAsState()
//    val battery by viewModel.battery.collectAsState()

    val xCoordinate by viewModel.xCoordinate.collectAsState() // Collecting the state
    val yCoordinate by viewModel.yCoordinate.collectAsState()
    val zCoordinate by viewModel.zCoordinate.collectAsState()
    val linear_velocity by viewModel.linear_velocity.collectAsState()
    val angular_velocity by viewModel.angular_velocity.collectAsState()
    val heading by viewModel.heading.collectAsState()
    val battery by viewModel.battery.collectAsState()

    LaunchedEffect(Unit) {
        val config = viewModel.loadConfig()
        config?.let {
            ipAddress = it.ipAddress
            port = it.port
            textToSend = it.textToSend
        }

        viewModel.startCoordinatesWebSocket()
        viewModel.startVelocityWebSocket()
        viewModel.startBatteryWebSocket()
        viewModel.startOccupancyWebSocket()
        viewModel.receiveFeed("10.0.0.1", "8080", "/stream?topic=/camera/image_raw&type=ros_compressed") //different port number and IP Address
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Status",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.height(80.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                //.verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
                    .background(Color.Black)
            ) {
                VideoFeedDisplay(viewModel.currentFrame.value)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column: Telemetry Details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    TelemetryBlock("Coordinates (X, Y)", "$xCoordinate, $yCoordinate")
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryBlock("Heading (W)", "$heading")
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryBlock("Linear Velocity (x)", "$linear_velocity")
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryBlock("Angular Velocity (z)", "$angular_velocity")
                }

                // Right Column: Occupancy Map & Battery
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    OccupancyDisplay(viewModel.occupancyBitmap.value)
                    Spacer(modifier = Modifier.height(16.dp))
                    TelemetryBlock("Battery", "$battery%")
                }
            }
        }
    }
}

@Composable
fun TelemetryBlock(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFFAFFD86).copy(alpha = 0.3f),
                        Color(0xFF7A8A80).copy(alpha = 0.3f)
                    )),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Divider(color = Color.White, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun OccupancyDisplay(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.Black)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Video Feed",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Loading occupancy map...",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun VideoFeedDisplay(bitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Video Feed",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "No video feed",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}



