package com.mamy.android.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object PermissionLauncher {

    val REQUIRED: Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            add("android.permission.FOREGROUND_SERVICE_MICROPHONE")
        }
    }.toTypedArray()

    fun missing(ctx: Context): List<String> = REQUIRED.filter {
        ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
    }

    fun register(activity: ComponentActivity, onResult: (granted: Boolean) -> Unit) =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            onResult(results.values.all { it })
        }
}
