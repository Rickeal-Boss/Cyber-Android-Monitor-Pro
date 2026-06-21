package com.example.deviceinfoviewer.util

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.deviceinfoviewer.R

/**
 * 权限引导工具类，三步引导流程
 * 通过 pendingCallback/pendingRequestCode 机制在 MainActivity.onRequestPermissionsResult 中接收结果
 */
object PermissionHelper {

    interface PermissionCallback {
        fun onAllGranted()
        fun onDenied()
    }

    private val LOCATION_PERMS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // 待处理的权限回调
    private var pendingCallback: PermissionCallback? = null
    private var pendingRequestCode = -1

    /**
     * 三步引导：定位权限 → 悬浮窗权限 → 电话状态权限
     */
    fun requestPermissionsSequential(activity: Activity, callback: PermissionCallback) {
        requestLocationPermission(activity, {
            requestOverlayPermission(activity, {
                requestPhonePermission(activity, callback)
            }, callback)
        }, callback)
    }

    /**
     * 处理权限请求结果 —— 由 Activity 的 onRequestPermissionsResult 调用
     */
    @JvmStatic
    fun onPermissionResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val cb = pendingCallback
        if (cb != null && requestCode == pendingRequestCode) {
            pendingCallback = null
            pendingRequestCode = -1

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cb.onAllGranted()
            } else {
                cb.onDenied()
            }
        }
    }

    private fun requestLocationPermission(activity: Activity, onGranted: Runnable, callback: PermissionCallback) {
        if (hasLocationPermission(activity)) {
            onGranted.run()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.perm_location_title))
            .setMessage(activity.getString(R.string.perm_location_msg))
            .setPositiveButton(activity.getString(R.string.perm_grant)) { _, _ ->
                pendingCallback = object : PermissionCallback {
                    override fun onAllGranted() { onGranted.run() }
                    override fun onDenied() { onGranted.run() }
                }
                pendingRequestCode = 100
                ActivityCompat.requestPermissions(activity, LOCATION_PERMS, 100)
            }
            .setNegativeButton(activity.getString(R.string.perm_skip)) { _, _ -> onGranted.run() }
            .show()
    }

    /**
     * 引导用户开启悬浮窗权限（跳转系统设置）
     */
    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.perm_overlay_title))
                .setMessage(activity.getString(R.string.perm_overlay_msg))
                .setPositiveButton(activity.getString(R.string.perm_go_settings)) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivityForResult(intent, 200)
                }
                .setNegativeButton(activity.getString(R.string.perm_cancel), null)
                .show()
        }
    }

    private fun requestOverlayPermission(activity: Activity, onGranted: Runnable, callback: PermissionCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(activity)) {
            onGranted.run()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.perm_overlay_title))
                .setMessage(activity.getString(R.string.perm_overlay_msg))
                .setPositiveButton(activity.getString(R.string.perm_go_settings)) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivityForResult(intent, 200)
                }
                .setNegativeButton(activity.getString(R.string.perm_skip)) { _, _ -> }
                .show()
        }
        // 悬浮窗权限不阻塞后续流程
        onGranted.run()
    }

    private fun requestPhonePermission(activity: Activity, callback: PermissionCallback) {
        if (hasPhonePermission(activity)) {
            callback.onAllGranted()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.perm_phone_title))
            .setMessage(activity.getString(R.string.perm_phone_msg))
            .setPositiveButton(activity.getString(R.string.perm_grant)) { _, _ ->
                pendingCallback = callback
                pendingRequestCode = 300
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.READ_PHONE_STATE), 300
                )
            }
            .setNegativeButton(activity.getString(R.string.perm_skip)) { _, _ -> callback.onAllGranted() }
            .show()
    }

    fun hasLocationPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasPhonePermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
}
