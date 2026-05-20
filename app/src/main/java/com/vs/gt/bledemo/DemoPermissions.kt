package com.vs.gt.bledemo

import android.Manifest
import android.os.Build

/**
 * BLE 扫描/连接所需运行时权限。
 *
 * Android 12(API 31) 开始，蓝牙扫描和连接权限从定位权限中拆分出来。
 * SDK 不会主动弹权限框，调用方 App 必须在使用 startScan/connect 前自行申请。
 */
fun requiredBlePermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}
