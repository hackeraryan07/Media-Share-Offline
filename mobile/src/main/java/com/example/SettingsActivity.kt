package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        
        setContent {
            MyApplicationTheme {
                var apiKey by remember { mutableStateOf(sharedPrefs.getString("gemini_api_key", "") ?: "") }
                var modelName by remember { mutableStateOf(sharedPrefs.getString("gemini_model_name", "gemini-3.5-flash") ?: "gemini-3.5-flash") }
                var expanded by remember { mutableStateOf(false) }
                
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("AI Integration", style = MaterialTheme.typography.titleMedium)
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { 
                                apiKey = it
                                sharedPrefs.edit().putString("gemini_api_key", it).apply()
                            },
                            label = { Text("Gemini API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = modelName,
                                onValueChange = { 
                                    modelName = it
                                    sharedPrefs.edit().putString("gemini_model_name", it).apply()
                                },
                                label = { Text("Model Name") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                val models = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-2.5-flash")
                                models.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            modelName = selectionOption
                                            sharedPrefs.edit().putString("gemini_model_name", selectionOption).apply()
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = "Provide your Gemini API key to enable AI-generated playlists.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
