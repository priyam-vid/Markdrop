package com.markdrop.app.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class HapticType {
    LIGHT,    // Navigation, selection
    MEDIUM,   // Confirmations (starting conversion)
    SUCCESS,  // Conversion complete
    ERROR,    // Conversion failed
}

object HapticHelper {
    fun performHaptic(view: View, type: HapticType, enabled: Boolean) {
        if (!enabled) return
        
        val feedbackConstant = when (type) {
            HapticType.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
            HapticType.MEDIUM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            HapticType.SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            HapticType.ERROR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        
        view.performHapticFeedback(feedbackConstant)
    }
}

@Composable
fun rememberHapticHelper(hapticsEnabled: Boolean): (HapticType) -> Unit {
    val view = LocalView.current
    return remember(view, hapticsEnabled) {
        { type ->
            HapticHelper.performHaptic(view, type, hapticsEnabled)
        }
    }
}
