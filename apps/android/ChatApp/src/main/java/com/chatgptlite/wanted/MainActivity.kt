package com.chatgptlite.wanted

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chatgptlite.wanted.ui.NavRoute
import com.chatgptlite.wanted.ui.NavRoute.ROVER_SETTINGS
import com.chatgptlite.wanted.ui.common.AppBar
import com.chatgptlite.wanted.ui.common.AppScaffold
import com.chatgptlite.wanted.ui.settings.rover.SettingsScreen
import com.chatgptlite.wanted.ui.theme.ChatGPTLiteTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment

import com.chatgptlite.wanted.ui.settings.advance.AdvanceViewModel
import com.chatgptlite.wanted.ui.settings.advance.AdvanceScreen
import com.chatgptlite.wanted.ui.settings.rover.RoverSettingsViewModel
import com.chatgptlite.wanted.ui.settings.video.VideoCamSettingsViewModel
import com.chatgptlite.wanted.ui.settings.video.VideoStreamingSetting
import com.chatgptlite.wanted.ui.settings.occupancy.Occupancy
import com.chatgptlite.wanted.ui.settings.occupancy.OccupancyViewModel
import com.chatgptlite.wanted.ui.settings.rover.TelemetryScreen
import com.chatgptlite.wanted.ui.settings.rover.TelemetryViewModel
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialogDefaults.containerColor
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.layout.BeyondBoundsLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chatgptlite.wanted.data.whisper.asr.IRecorderListener
import com.chatgptlite.wanted.data.whisper.asr.IWhisperListener
import com.chatgptlite.wanted.data.whisper.asr.Recorder
import com.chatgptlite.wanted.data.whisper.asr.Whisper
import com.chatgptlite.wanted.data.whisper.utils.WaveUtil
import com.chatgptlite.wanted.services.AudioService
import com.chatgptlite.wanted.services.getFilePath
//import com.chatgptlite.wanted.ui.conversations.components.startBackgroundRecorder
import com.chatgptlite.wanted.ui.settings.terminal.TerminalScreen
import com.chatgptlite.wanted.ui.settings.terminal.TerminalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.*
import com.chatgptlite.wanted.helpers.sendMessage


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val TAG = "MainActivity"
    private val micVisibleState = mutableStateOf(false)

    var text = mutableStateOf("")
    private val isForegroundRecording = mutableStateOf(false)

    private val keywordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //if (true) {
            if (intent.action == "com.chatgptlite.wanted.ACTION_KEYWORD_DETECTED") {
                val detectedKeyword = intent.getStringExtra("keyword")
                Log.d(TAG, "Keyword received in MainActivity: $detectedKeyword")
                // Handle the keyword detection event
                startRecorder()
//                micVisibleState.value = true
            }
        }
    }

    private fun processWhisperCommand(command: String, addr: String, port: String) {
        val TAG = "processWhisperCommand"
        val textToSend = when {
            command.contains("forward", ignoreCase = true) -> "python3 drive_forward.py"
            command.contains("backward", ignoreCase = true) -> "python3 drive_backward.py"
            command.contains("left", ignoreCase = true) -> "python3 drive_left.py"
            command.contains("right", ignoreCase = true) -> "python3 drive_right.py"
            command.contains("base", ignoreCase = true) -> "python3 return_to_base.py"
            else -> {
                Log.d(TAG, "Command not recognized: $command")
                return
            }
        }

        Log.d(TAG, "Sending command to rover: $textToSend")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = sendMessage(addr, port, textToSend)
                if (response.isSuccessful) {
                    Log.d(TAG, "Command successfully executed: $textToSend")
                } else {
                    Log.e(TAG, "Failed to execute command: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while sending command: ${e.message}", e)
            }
        }
    }

    private fun startRecorder() {
        var modelPath = getFilePath(this, "whisper-tiny-en.tflite")
        var vocabPath = getFilePath(this, "filters_vocab_en.bin")
        Log.d(TAG, "Starting recorder from MainActivity.")
        updateForegroundRecordingSignal(true)
        isForegroundRecording.value = true
        micVisibleState.value = true

        val whisper = Whisper(this).apply {
                loadModel(modelPath, vocabPath, false)
                setListener(object : IWhisperListener {
                    override fun onUpdateReceived(message: String?) {
                        Log.d("foreground", "onUpdateReceived: $message")
                    }

                    override fun onResultReceived(result: String?) {
                        Log.d("foreground", "onResultReceived: $result")
                        text.value = result ?: ""

                        result?.let {
                            val addr = "10.0.0.120"
                            val port = "8000"
                            // processWhisperCommand(it, addr, port)
                        }
                    }

                })
        }

        val waveFilePath = getFilePath(this, WaveUtil.RECORDING_FILE)
        val record = Recorder(this).apply {
                setListener(object : IRecorderListener {
                    override fun onUpdateReceived(message: String) {
                        Log.d("foreground", "onUpdateReceived: $message")
                        if (message.contains("done")) {
                            Log.d("foreground", "start translation")
                            whisper.setFilePath(waveFilePath)
                            whisper.setAction(Whisper.ACTION_TRANSCRIBE)
                            whisper.start()
                        }
                        Log.d("foreground", "${message.contains("done")} $message ${Whisper.MSG_PROCESSING_DONE}")
                    }

                    override fun onDataReceived(samples: FloatArray?) {
                        Log.d("foreground", "onDataReceived: $samples")
                    }

                })
        }
        record.setFilePath(waveFilePath)
        record.start()


        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                delay(500) // Check for inactivity every 500ms
                if (!record.isInProgress()) {
                    Log.d("foreground", "Recorder stopped. Restarting background recorder...")
                    isForegroundRecording.value = true
                    updateForegroundRecordingSignal(false)
                    delay(5000)
                    micVisibleState.value = false
                    text.value = ""
                    break
                }
            }
        }
    }

    private fun updateForegroundRecordingSignal(isRecording: Boolean) {
        val intent = Intent("com.chatgptlite.wanted.ACTION_FOREGROUND_RECORDING").apply {
            putExtra("isForegroundRecording", isRecording)
        }
        sendBroadcast(intent)
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Register the receiver to listen for keyword detection
        //val filter = IntentFilter("com.chatgptlite.wanted.ACTION_KEYWORD_DETECTED")
        //registerReceiver(keywordReceiver, filter)



        //AudioService.start(this)
        setContentView(
            ComposeView(this).apply {
                consumeWindowInsets = false
                setContent {
                    val navController = rememberNavController()

                    // Bottom navigation bar
                    val bottomNavItems = listOf(
                        NavRoute.VIDEO_STREAM,
                        NavRoute.ROVER_SETTINGS,
//                        NavRoute.OCCUPANCY,
//                        NavRoute.TELEMETRY
                        NavRoute.ADVANCE
                    )

                    // Intercepts back navigation when the drawer is open
                    val scope = rememberCoroutineScope()
                    val focusManager = LocalFocusManager.current

                    val darkTheme = remember(key1 = "darkTheme") {
                        mutableStateOf(true)
                    }


                    ChatGPTLiteTheme(darkTheme.value) {
                        Scaffold(
                            bottomBar = {
                                BottomNavigationBar(
                                    navController = navController,
                                    items = bottomNavItems
                                )
                            },
                            floatingActionButton = {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Floating mic button
                                    FloatingActionButton(
                                        onClick = {
                                            Log.d("MicButton", "Square Mic Button clicked!")
                                            startRecorder()
                                            micVisibleState.value = true  // Show the translucent button
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Mic,
                                            contentDescription = "Mic Button",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Show TranslucentMicButton only when micVisibleState is true
                                    if (micVisibleState.value) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0f)), // Slight background blur effect
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            TranslucentMicButton(
                                                text = text,
                                                isVisible = micVisibleState,
                                                onClick = {
                                                    Log.d("TranslucentMicButton", "Mic button clicked! Hiding overlay.")
                                                    micVisibleState.value = false // Hide when clicked
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Surface(
                                //modifier = Modifier.padding(innerPadding),
//                                modifier = Modifier
//                                    .fillMaxSize()
//                                    .padding(innerPadding),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                // NavHost for managing navigation
                                NavHost(
                                    navController = navController,
                                    startDestination = NavRoute.ROVER_SETTINGS
                                ) {
                                    composable(NavRoute.HOME) {
                                        SettingsScreen(
                                            viewModel = viewModel<RoverSettingsViewModel>(),
                                            onBackPressed = { navController.navigateUp() }
                                        )
                                    }
                                    composable(NavRoute.ROVER_SETTINGS) {
                                        SettingsScreen(
                                            viewModel = viewModel<RoverSettingsViewModel>(),
                                            onBackPressed = { navController.navigateUp() }
                                        )
                                    }
                                    composable(NavRoute.OCCUPANCY) {
                                        Occupancy(
                                            viewModel<OccupancyViewModel>(),
                                            onBackPressed = { navController.navigateUp() }
                                        )
                                    }
                                    composable(NavRoute.VIDEO_STREAM) {
                                        VideoStreamingSetting(
                                            viewModel<VideoCamSettingsViewModel>(),
                                            onBackPressed = { navController.navigateUp() }
                                        )
                                    }
                                    composable(NavRoute.TELEMETRY) {
                                        TelemetryScreen(
                                            viewModel<TelemetryViewModel>(),
                                            onBackPressed = { navController.navigateUp() }
                                        )
                                    }
                                    composable(NavRoute.ADVANCE){
                                        AdvanceScreen(
                                            viewModel<AdvanceViewModel>(),
                                            onBackPressed = { navController.navigateUp() },
                                            onTerminalClick = {
                                                // Navigate to Terminal Page
                                                navController.navigate("terminal")
                                            },
                                            onLLMSettingClick = {
                                                // Navigate to LLM Settings Page
                                                navController.navigate("llm_settings")
                                            }
                                        )
                                    }
                                    composable("terminal") {
                                        TerminalScreen(
                                            viewModel = viewModel<TerminalViewModel>(),
                                            onBackPressed = { navController.navigateUp() }
                                        )
                                    }
                                }


                            }
                        }
                    }


                }
            }
        )

    }




}

@Composable
fun BottomNavigationBar(navController: NavHostController, items: List<String>) {
    NavigationBar {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        items.forEach { route ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    when (route) {
                        NavRoute.VIDEO_STREAM -> Icon(Icons.Default.ControlCamera, contentDescription = "Telemetry")
                        NavRoute.ROVER_SETTINGS -> Icon(Icons.Default.Home, contentDescription = "Settings")
//                        NavRoute.OCCUPANCY -> Icon(Icons.Default.MoreHoriz, contentDescription = "Occupancy")
//                      NavRoute.TELEMETRY-> Icon(Icons.Default.Settings, contentDescription = "Testing")
                        NavRoute.ADVANCE-> Icon(Icons.Default.MoreHoriz, contentDescription = "Occupancy")
                    }
                },
                label = { Text(route) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    ChatGPTLiteTheme {
    }
}

@Composable
fun TranslucentMicButton(
    text: MutableState<String>,
    isVisible: MutableState<Boolean>,
    onClick: () -> Unit
) {
    Log.d("TranslucentMicButton", text.value)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.2f) // Occupy 1/3 of the screen's height
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), // Translucent background
                    //shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center // Center the content inside the Box
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text.value,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold, // Make the text bold
                        fontSize = 20.sp // Increase font size
                    ),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.padding(8.dp)
                )
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice Input",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
}