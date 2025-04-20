package com.chatgptlite.wanted.ui.settings.video

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.chatgptlite.wanted.ui.common.AppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoStreamingSetting(
    viewModel: VideoCamSettingsViewModel,
    onBackPressed: () -> Unit
) {
    var ipAddress by remember { mutableStateOf("10.0.0.1") }
    var port by remember {
        mutableStateOf("8080")
    }
    var route by remember {
        mutableStateOf("/stream?topic=/camera/image_raw&type=ros_compressed")
    }

    LaunchedEffect(Unit) {
        val config = viewModel.loadConfig()
        config?.let {
            ipAddress = it.ipAddress
            port = it.port
            route = it.route
        }
        viewModel.receiveFeed(ipAddress, port, route)
        viewModel.createWebSocket()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Controller",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                            .wrapContentHeight(align = Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.height(80.dp), // Reduce top bar height
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
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
            // IP Address and Port row (keep existing code)
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//                OutlinedTextField(
//                    value = ipAddress,
//                    onValueChange = { ipAddress = it },
//                    label = { Text("IP Address") },
//                    modifier = Modifier.weight(2f)
//                )
//                Spacer(modifier = Modifier.width(8.dp))
//                OutlinedTextField(
//                    value = port,
//                    onValueChange = { port = it },
//                    label = { Text("Port") },
//                    modifier = Modifier.weight(1f)
//                )
//            }
            Spacer(modifier = Modifier.height(16.dp))

//            OutlinedTextField(
//                value = route,
//                onValueChange = { route = it },
//                label = { Text("route") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            Spacer(modifier = Modifier.height(16.dp))

            // Add the video feed display
            VideoFeedDisplay(viewModel.currentFrame.value, viewModel.occupancyBitmap.value)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.receiveFeed(ipAddress, port, route) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Receive Video", color = MaterialTheme.colorScheme.background)
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.controlRover(0.5, 0.0) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("forward", color = MaterialTheme.colorScheme.background)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { viewModel.controlRover(-0.5, 0.0) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("backward", color = MaterialTheme.colorScheme.background)
                }

            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.controlRover(0.0, 1.0) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("left", color = MaterialTheme.colorScheme.background)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { viewModel.controlRover(0.0, -1.0) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("right", color = MaterialTheme.colorScheme.background)
                }

            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.returnToBase() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Return to Base", color = MaterialTheme.colorScheme.background)
                }

            }


        }
    }
}

@Composable
fun VideoFeedDisplay(videoBitmap: Bitmap?, occupancyBitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        // Display video feed placeholder if no videoBitmap is provided
        if (videoBitmap != null) {
            Image(
                bitmap = videoBitmap.asImageBitmap(),
                contentDescription = "Video Feed",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder for video feed
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("Video Feed Placeholder", color = Color.White)
            }
        }

        // Overlay occupancy map in the bottom-right corner
        if (occupancyBitmap != null) {
            Image(
                bitmap = occupancyBitmap.asImageBitmap(),
                contentDescription = "Occupancy Map",
                modifier = Modifier
                    .align(Alignment.BottomEnd) // Position in the bottom-right corner
                    .size(100.dp) // Set size for the overlay
                    .padding(8.dp) // Add some padding from edges
                    .graphicsLayer(alpha = 0.7f) // Set transparency
            )
        } else {
            // Placeholder for occupancy map
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(100.dp)
                    .padding(8.dp)
                    .background(Color.Red.copy(alpha = 0.7f)), // Semi-transparent red
                contentAlignment = Alignment.Center
            ) {
                Text("Occupancy Map", color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

//fun VideoFeedDisplay(bitmap: Bitmap?) {
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(300.dp)
//    ) {
//        if (bitmap != null) {
//            Image(
//                bitmap = bitmap.asImageBitmap(),
//                contentDescription = "Video Feed",
//                modifier = Modifier.fillMaxSize()
//            )
//        } else {
//            Text(
//                text = "No video feed",
//                modifier = Modifier.align(Alignment.Center)
//            )
//        }
//    }
//}