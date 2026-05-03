package com.mamy.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(vm: LlmSettingsViewModel = hiltViewModel()) {
    val providers = remember { vm.availableProviders() }
    var selectedProvider by remember { mutableStateOf(providers.first().id) }
    var key by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val testResult by vm.lastTestResult.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            TextField(
                value = providers.first { it.id == selectedProvider }.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                providers.forEach { p ->
                    DropdownMenuItem(text = { Text(p.displayName) }, onClick = {
                        selectedProvider = p.id; expanded = false; vm.selectProvider(p.id)
                    })
                }
            }
        }

        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { vm.saveKey(selectedProvider, key) }) { Text("Save key") }
        Button(onClick = { vm.testKey(selectedProvider) }) { Text("Test key") }

        Text(when (val r = testResult) {
            KeyTestResult.Idle -> ""
            is KeyTestResult.Pending -> "Testing ${r.providerId}..."
            is KeyTestResult.Success -> "Key OK for ${r.providerId}"
            is KeyTestResult.Failure -> "Key test failed: ${r.message}"
        })
    }
}
