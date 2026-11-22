package com.illustar0.teishucan.ui.component

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.CampusRequestException
import com.illustar0.teishucan.domain.InvalidMeterLocationException
import com.illustar0.teishucan.domain.campusReason
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
fun RefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        contentPadding = contentPadding,
        topAppBarScrollBehavior = scrollBehavior,
        refreshTexts = listOf(
            stringResource(R.string.refresh_pull),
            stringResource(R.string.refresh_release),
            stringResource(R.string.refresh_running),
            stringResource(R.string.refresh_done),
        ),
        content = content,
    )
}

@Composable
fun FailureToasts(
    failures: StateFlow<Exception?>,
    @StringRes fallbackMessage: Int = R.string.refresh_failed,
    onAcknowledge: (Exception) -> Unit,
) {
    val failure by failures.collectAsStateWithLifecycle()
    val currentOnAcknowledge by rememberUpdatedState(onAcknowledge)
    val context = LocalContext.current
    val resources = LocalResources.current

    failure?.let { shownFailure ->
        LaunchedEffect(shownFailure) {
            val message = shownFailure.campusReason ?: resources.getString(shownFailure.messageRes ?: fallbackMessage)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            currentOnAcknowledge(shownFailure)
        }
    }
}

@get:StringRes
private val Exception.messageRes: Int?
    get() = when (this) {
        is InvalidMeterLocationException -> R.string.electricity_meter_reselect_location
        is CampusRequestException -> when (feature) {
            CampusFeature.Account -> R.string.login_failed
            CampusFeature.Timetable -> R.string.timetable_refresh_failed
            CampusFeature.CampusCard -> R.string.campus_card_refresh_failed
            CampusFeature.Electricity -> R.string.electricity_refresh_failed
            CampusFeature.Mail -> R.string.mail_open_failed
            CampusFeature.Services -> R.string.service_open_failed
        }

        else -> null
    }
