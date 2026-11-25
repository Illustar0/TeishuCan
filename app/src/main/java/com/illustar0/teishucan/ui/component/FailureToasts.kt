package com.illustar0.teishucan.ui.component

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.R
import com.illustar0.teishucan.concurrency.PendingFailure
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.CampusRequestException
import com.illustar0.teishucan.domain.InvalidMeterLocationException
import com.illustar0.teishucan.domain.campusReason

/**
 * 用 Toast 提示 [failures] 里待提示的失败，提示后确认它。
 *
 * 优先显示校园服务给出的原因，其次是该失败场景的提示语，都没有时用 [fallbackMessage]。
 */
@Composable
fun FailureToasts(failures: PendingFailure, @StringRes fallbackMessage: Int = R.string.refresh_failed) {
    val failure by failures.failure.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    failure?.let { shownFailure ->
        LaunchedEffect(shownFailure) {
            val message = shownFailure.campusReason ?: resources.getString(shownFailure.messageRes ?: fallbackMessage)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            failures.acknowledge(shownFailure)
        }
    }
}

@get:StringRes
private val Exception.messageRes: Int?
    get() = when (this) {
        is InvalidMeterLocationException -> R.string.electricity_meter_reselect_location
        is CampusRequestException -> when (feature) {
            CampusFeature.Account -> R.string.login_failed
            CampusFeature.Profile -> R.string.profile_refresh_failed
            CampusFeature.Timetable -> R.string.timetable_refresh_failed
            CampusFeature.CampusCard -> R.string.campus_card_refresh_failed
            CampusFeature.Electricity -> R.string.electricity_refresh_failed
            CampusFeature.Services -> R.string.service_open_failed
        }

        else -> null
    }
