package com.nothing.one.feature.music.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

object PermissionUtils {

    val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasRequiredPermission(context: android.content.Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberPermissionState(): com.nothing.one.feature.music.util.PermissionState {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(PermissionUtils.hasRequiredPermission(context))
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
    }
    return com.nothing.one.feature.music.util.PermissionState(
        isGranted = granted,
        launchRequest = { launcher.launch(PermissionUtils.requiredPermission) },
        recheck = { granted = PermissionUtils.hasRequiredPermission(context) },
    )
}

data class PermissionState(
    val isGranted: Boolean,
    val launchRequest: () -> Unit,
    val recheck: () -> Unit,
)