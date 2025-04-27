package com.chatgptlite.wanted

//import com.chatgptlite.wanted.ui.conversations.components.startBackgroundRecorder

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chatgptlite.wanted.data.whisper.asr.IRecorderListener
import com.chatgptlite.wanted.data.whisper.asr.IWhisperListener
import com.chatgptlite.wanted.data.whisper.asr.Recorder
import com.chatgptlite.wanted.data.whisper.asr.Whisper
import com.chatgptlite.wanted.data.whisper.utils.WaveUtil
import com.chatgptlite.wanted.helpers.sendMessage
import com.chatgptlite.wanted.services.getFilePath
import com.chatgptlite.wanted.ui.NavRoute
import com.chatgptlite.wanted.ui.settings.advance.AdvanceScreen
import com.chatgptlite.wanted.ui.settings.advance.AdvanceViewModel
import com.chatgptlite.wanted.ui.settings.occupancy.Occupancy
import com.chatgptlite.wanted.ui.settings.occupancy.OccupancyViewModel
import com.chatgptlite.wanted.ui.settings.rover.RoverSettingsViewModel
import com.chatgptlite.wanted.ui.settings.rover.SettingsScreen
import com.chatgptlite.wanted.ui.settings.rover.TelemetryScreen
import com.chatgptlite.wanted.ui.settings.rover.TelemetryViewModel
import com.chatgptlite.wanted.ui.settings.terminal.TerminalScreen
import com.chatgptlite.wanted.ui.settings.terminal.TerminalViewModel
import com.chatgptlite.wanted.ui.settings.controller.VideoCamSettingsViewModel
import com.chatgptlite.wanted.ui.settings.controller.VideoStreamingSetting
import com.chatgptlite.wanted.ui.theme.ChatGPTLiteTheme
import com.quicinc.chatapp.ChatMessage
import com.quicinc.chatapp.GenieWrapper
import com.quicinc.chatapp.R
import com.quicinc.chatapp.StringCallback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.Paths

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("chatapp")
        }
    }
    private val mainViewModel: MainViewModel by viewModels()
    private val TAG = "MainActivity"
    private val micVisibleState = mutableStateOf(false)

    var text = mutableStateOf("")
    var genieResponse = mutableStateOf("")
    private val isForegroundRecording = mutableStateOf(false)
    lateinit var genieWrapper: GenieWrapper

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
            command.contains("forward", ignoreCase = true) -> "ros2 run drive_pkg drive_publisher –ros-args -p x:=1"
            command.contains("backward", ignoreCase = true) -> "ros2 run drive_pkg drive_publisher –ros-args -p x:=-1"
            command.contains("left", ignoreCase = true) ->"ros2 run drive_pkg drive_publisher –ros-args -p y:=1"
            command.contains("right", ignoreCase = true) -> "ros2 run drive_pkg drive_publisher –ros-args -p y:=-1"
            command.contains("base", ignoreCase = true) -> "ros2 topic pub /goal_pose geometry_msgs/PoseStamped \"{header: {stamp: {sec: 0}, frame_id: 'map'}, pose: {position: {x: 0.0, y: 0.0, z: 0.0}, orientation: {w: 1.0}}}\" —once"

            else -> {
                Log.d(TAG, "Command not recognized: $command")
                return
            }
        }

        Log.d(TAG, "Sending command to rover: $textToSend")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                mainViewModel.setRoverState("Executing") // Broadcast state
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
                        genieResponse.value = ""

                        getGenieResponse(it) { responseToken ->
                            // Append each token to genieResponse
                            runOnUiThread {
                                genieResponse.value += responseToken
                                Log.d("MainActivity", "Genie response so far: $genieResponse")
                            }
                        }

                        val addr = "10.0.0.120"
                        val port = "8000"
//                        processWhisperCommand(it, addr, port)
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
                    Log.d(
                        "foreground",
                        "${message.contains("done")} $message ${Whisper.MSG_PROCESSING_DONE}"
                    )
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
                    delay(10000) // how long the popup will stay
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

    /**
     * copyAssetsDir: Copies provided assets to output path
     *
     * @param inputAssetRelPath relative path to asset from asset root
     * @param outputPath        output path to copy assets to
     * @throws IOException
     * @throws NullPointerException
     */
    @Throws(IOException::class, NullPointerException::class)
    fun copyAssetsDir(inputAssetRelPath: String?, outputPath: String?) {
        val outputAssetPath = File(Paths.get(outputPath, inputAssetRelPath).toString())

        val subAssetList = this.assets.list(inputAssetRelPath!!)
        if (subAssetList!!.size == 0) {
            // If file already present, skip copy.
            if (!outputAssetPath.exists()) {
                copyFile(inputAssetRelPath, outputAssetPath)
            }
            return
        }

        // Input asset is a directory, create directory if not present already.
        if (!outputAssetPath.exists()) {
            outputAssetPath.mkdirs()
        }
        for (subAssetName in subAssetList) {
            // Copy content of sub-directory
            val input_sub_asset_path = Paths.get(inputAssetRelPath, subAssetName).toString()
            // NOTE: Not to modify output path, relative asset path is being updated.
            copyAssetsDir(input_sub_asset_path, outputPath)
        }
    }

    /**
     * copyFile: Copies provided input file asset into output asset file
     *
     * @param inputFilePath   relative file path from asset root directory
     * @param outputAssetFile output file to copy input asset file into
     * @throws IOException
     */
    @Throws(IOException::class)
    fun copyFile(inputFilePath: String?, outputAssetFile: File?) {
        val `in` = this.assets.open(inputFilePath!!)
        val out: OutputStream = FileOutputStream(outputAssetFile)

        val buffer = ByteArray(1024 * 1024)
        var read: Int
        while ((`in`.read(buffer).also { read = it }) != -1) {
            out.write(buffer, 0, read)
        }
        Log.i("chatbackend", "copied from" + inputFilePath)
    }

    fun getGenieResponse(prompt: String, onResponse: (String) -> Unit) {
        genieWrapper.getResponseForPrompt(prompt, object : StringCallback {
            override fun onNewString(str: String?) {
                str?.let {
                    Log.d("GenieResponse", "Received token: $it")
                    onResponse(it)
                }
            }
        })
    }


    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        lateinit var htpExtConfigPath: Path
        try {
            // Get SoC model from build properties
            // As of now, only Snapdragon Gen 3 and 8 Elite is supported.
            val supportedSocModel = HashMap<String, String>()
            supportedSocModel.putIfAbsent("SM8750", "qualcomm-snapdragon-8-elite.json")
            supportedSocModel.putIfAbsent("SM8650", "qualcomm-snapdragon-8-gen3.json")
            supportedSocModel.putIfAbsent("QCS8550", "qualcomm-snapdragon-8-gen2.json")

            val socModel = Build.SOC_MODEL
            if (!supportedSocModel.containsKey(socModel)) {
                val errorMsg =
                    "Unsupported device. Please ensure you have one of the following device to run the ChatApp: $supportedSocModel"
                Log.e("ChatApp", errorMsg)
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                finish()
            }

            // Copy assets to External cache
            //  - <assets>/models
            //      - has list of models with tokenizer.json, genie-config.json and model binaries
            //  - <assets>/htp_config/
            //      - has SM8750.json and SM8650.json and picked up according to device SOC Model at runtime.
            val externalDir = externalCacheDir!!.absolutePath
            try {
                // Copy assets to External cache if not already present
                copyAssetsDir("models", externalDir.toString())
                copyAssetsDir("htp_config", externalDir.toString())
            } catch (e: IOException) {
                val errorMsg = "Error during copying model asset to external storage: $e"
                Log.e("ChatApp", errorMsg)
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                finish()
            }
            htpExtConfigPath = Paths.get(
                externalDir, "htp_config",
                supportedSocModel[socModel]
            )
//                val intent = Intent(this@MainActivity, Conversation::class.java)
//                intent.putExtra(
//                    Conversation.cConversationActivityKeyHtpConfig,
//                    htpExtConfigPath.toString()
//                )
//                intent.putExtra(Conversation.cConversationActivityKeyModelName, "llama3_2_3b")
//                startActivity(intent)
        } catch (e: java.lang.Exception) {
            val errorMsg = "Unexpected error occurred while running ChatApp:$e"
            Log.e("ChatApp", errorMsg)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            finish()
        }


        val messages = ArrayList<ChatMessage>(1000)

        val cWelcomeMessage = "Hi! How can I help you?"
        val cConversationActivityKeyHtpConfig = htpExtConfigPath.toString()
        val cConversationActivityKeyModelName = "llama3_2_3b"

            try {
                // Make QNN libraries discoverable
                val nativeLibPath = applicationContext.applicationInfo.nativeLibraryDir
                Os.setenv("ADSP_LIBRARY_PATH", nativeLibPath, true)
                Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true)

                // Get information from MainActivity regarding
                //  - Model to run
                //  - HTP config to use
//                val bundle = savedInstanceState
//                if (bundle == null) {
//                    Log.e("ChatApp", "Error getting additional info from bundle.")
//                    Toast.makeText(
//                        this,
//                        "Unexpected error observed. Exiting app.",
//                        Toast.LENGTH_LONG
//                    ).show()
//                    finish()
//                }

                val htpExtensionsDir = cConversationActivityKeyHtpConfig
                val modelName = cConversationActivityKeyModelName
                val externalCacheDir = this.externalCacheDir!!.absolutePath.toString()
                val modelDir = Paths.get(externalCacheDir, "models", modelName).toString()

                // Load Model
                genieWrapper = GenieWrapper(modelDir, htpExtensionsDir)
                Log.i("Chatbackend", "$modelName Loaded.")
            } catch (e: java.lang.Exception) {
                Log.e("ChatApp", "Error during conversation with Chatbot: $e")
                Toast.makeText(this, "Unexpected error observed. Exiting app.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }

//        getGenieResponse("test message") { responseToken ->
//            Log.d("Chatbackend", "Genie response: $responseToken")
//        }


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
                                }

                                val animationFrames = listOf(
                                    R.drawable.mic_img_1, // Replace with your actual drawable resources
                                    R.drawable.mic_img_2,
                                    R.drawable.mic_img_3,
                                    R.drawable.mic_img_4,
                                    R.drawable.mic_img_5,
                                    R.drawable.mic_img_6,
                                    R.drawable.mic_img_7,
                                    R.drawable.mic_img_8,
                                    R.drawable.mic_img_9,
                                    R.drawable.mic_img_10,
                                    R.drawable.mic_img_11,
                                    R.drawable.mic_img_12
                                )

                                if (micVisibleState.value) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.BottomCenter // Ensures it appears at the bottom
                                    ) {
                                        MicPopup(
                                            text = text,
                                            genieResponse = genieResponse,
                                            animationFrames = animationFrames
                                        )
                                    }
                                }

                            }
                        ) { innerPadding ->
                            Surface(
                                color = MaterialTheme.colorScheme.background
                            ) {
                                // NavHost for managing navigation
                                NavHost(
                                    navController = navController,
                                    startDestination = NavRoute.ROVER_SETTINGS
                                ) {
                                    composable(NavRoute.HOME) {
                                        SettingsScreen(
                                            mainViewModel = mainViewModel,
                                            viewModel = viewModel<RoverSettingsViewModel>(),
                                            onBackPressed = { navController.navigateUp() }
                                        )
                                    }
                                    composable(NavRoute.ROVER_SETTINGS) {
                                        SettingsScreen(
                                            mainViewModel = mainViewModel,
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
                                    composable(NavRoute.ADVANCE) {
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
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        val currentRoute by navController.currentBackStackEntryFlow
            .map { it?.destination?.route ?: "Status" }
            .collectAsState(initial = "Status")
        items.forEach { route ->
            val isSelected = currentRoute == route
            Log.d("navbar", "$currentRoute, $route, $isSelected");
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = when (route) {
                            NavRoute.VIDEO_STREAM -> Icons.Default.ControlCamera
                            NavRoute.ROVER_SETTINGS -> Icons.Default.Home
                            NavRoute.ADVANCE -> Icons.Default.MoreHoriz
                            else -> Icons.Default.Help // Default case
                        },
                        contentDescription = route,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = route,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },

                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.background // Prevents background change
                )
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
fun MicPopup(
    text: MutableState<String>,
    genieResponse: MutableState<String>,
    animationFrames: List<Int>, // List of drawable resource IDs
) {
    val currentFrameIndex = remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { // Keep looping as long as the popup is visible
            delay(200) // Change frame every 500ms
            currentFrameIndex.value = (currentFrameIndex.value + 1) % animationFrames.size
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.4f)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0f), // Start with background color (faded)
                        MaterialTheme.colorScheme.background.copy(alpha = 0.6f), // Middle transition
                        MaterialTheme.colorScheme.background.copy(alpha = 1f) // Fully transparent at the top
                    )
                )
            ),
        contentAlignment = Alignment.Center // Center the content inside the Box
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            // User Input Dialog Box
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f) // 90% of the width
                    .padding(horizontal = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, color = Color.White, shape = RoundedCornerShape(8.dp))
                    .padding(12.dp), // Inner padding for text
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text.value.ifEmpty { "Listening..." },
                    fontSize = 12.sp,
                    color = Color.White // White text
                )

                if (genieResponse.value.isNotBlank()) {
                    Text(
                        text = genieResponse.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Animated Circle at the Bottom
            Image(
                painter = painterResource(id = animationFrames[currentFrameIndex.value]),
                contentDescription = "Listening Animation",
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.Transparent)
            )
        }
    }
}