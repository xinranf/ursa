package com.chatgptlite.wanted.ui.settings.terminal

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatgptlite.wanted.ui.common.AppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = viewModel(),
    onBackPressed: () -> Unit
) {
    var ipAddress by remember { mutableStateOf("10.0.0.120") }
    var port by remember {
        mutableStateOf("8000")
    }
    var textToSend by remember { mutableStateOf("ros2 topic list") }
    val pingResult by viewModel.pingResult.collectAsState()
    val messageResult by viewModel.messageResult.collectAsState()

    LaunchedEffect(Unit) {
        val config = viewModel.loadConfig()
        config?.let {
            ipAddress = it.ipAddress
            port = it.port
            textToSend = it.textToSend
        }
    }

    Scaffold(
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()) // Enable vertical scrolling
        ) {
            // IP Address and Port row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP Address") },
                    modifier = Modifier.weight(2f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = textToSend,
                onValueChange = { textToSend = it },
                label = { Text("Text to send") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 15
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.sendPing("$ipAddress:$port") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Send Ping", color = MaterialTheme.colorScheme.background)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { viewModel.sendMessage(textToSend = textToSend, "$ipAddress:$port") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Send Message", color = MaterialTheme.colorScheme.background)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { viewModel.saveConfig(ipAddress, port, textToSend) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}


