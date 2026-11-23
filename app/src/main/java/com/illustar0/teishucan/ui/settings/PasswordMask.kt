package com.illustar0.teishucan.ui.settings

import androidx.compose.foundation.text.input.OutputTransformation

internal val PasswordMask = OutputTransformation {
    for (index in 0 until length) {
        replace(index, index + 1, "•")
    }
}
