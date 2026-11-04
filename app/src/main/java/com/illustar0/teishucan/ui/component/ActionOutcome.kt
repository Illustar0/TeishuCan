package com.illustar0.teishucan.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** ViewModel 里一次操作的结果，界面据此给出反馈。 */
enum class ActionOutcome { Succeeded, Failed }

/** 用触感反馈 [outcomes] 中的每个结果，操作成功时还会调用 [onSucceed]。 */
@Composable
fun OutcomeHaptics(outcomes: Flow<ActionOutcome>, onSucceed: suspend () -> Unit = {}) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnSucceed by rememberUpdatedState(onSucceed)

    LaunchedEffect(outcomes) {
        outcomes.collect { outcome ->
            when (outcome) {
                ActionOutcome.Succeeded -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    launch { currentOnSucceed() }
                }

                ActionOutcome.Failed -> hapticFeedback.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }
}
