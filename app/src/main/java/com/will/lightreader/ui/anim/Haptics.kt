package com.will.lightreader.ui.anim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 细节触感：关键动作给一记轻震（TEXT_HANDLE_MOVE 级，仿 iOS 轻点质感）。
 * 用法：`val tap = rememberHapticTap()` 然后在 onClick 里 `tap()`。
 */
@Composable
fun rememberHapticTap(): () -> Unit {
    val haptics: HapticFeedback = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

/** 稍重一档：翻章/确认等有"落地感"的动作 */
@Composable
fun rememberHapticThud(): () -> Unit {
    val haptics: HapticFeedback = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
