package com.vs.gt.bledemo

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val demoTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/**
 * 给 Demo 日志加一个本地时间戳。
 *
 * 真实项目通常会接入自己的日志系统；这里直接显示在页面上，方便客户边操作边观察 SDK 回调。
 */
fun demoLogLine(message: String): String {
    return "${demoTimeFormat.format(Date())}  $message"
}
