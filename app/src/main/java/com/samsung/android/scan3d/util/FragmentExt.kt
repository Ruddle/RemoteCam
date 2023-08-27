package com.samsung.android.scan3d.util

import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

fun Fragment.requestPermissionList(
    request: ActivityResultLauncher<Array<String>>, permissions: Array<String>
) = request.launch(permissions)

fun Fragment.isAllPermissionsGranted(permissions: Array<String>): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
}