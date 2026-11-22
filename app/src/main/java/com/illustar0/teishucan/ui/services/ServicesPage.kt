package com.illustar0.teishucan.ui.services

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val ServiceGroups = listOf(
    ServiceGroup(
        titleRes = R.string.services_library,
        services = listOf(CampusService.Library, CampusService.LibraryReservation),
    ),
    ServiceGroup(
        titleRes = R.string.services_student_affairs,
        services = listOf(CampusService.CampusCard, CampusService.Leave, CampusService.PresidentMailbox),
    ),
)

@Composable
fun ServicesPage(
    openingService: CampusService?,
    onOpenService: (CampusService) -> Unit,
    onOpenUndergradEas: () -> Unit,
    bottomPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.destination_services),
                    color = containerColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .blurSource(backdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding + 12.dp,
            ),
            overscrollEffect = null,
        ) {
            item(key = "academic") {
                SmallTitle(
                    text = stringResource(R.string.services_academic),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = stringResource(R.string.service_undergrad_eas),
                        summary = stringResource(R.string.service_undergrad_eas_summary),
                        startAction = { ServiceIcon(ImageVector.vectorResource(R.drawable.ic_lucide_graduation_cap)) },
                        onClick = onOpenUndergradEas,
                    )
                }
            }
            items(ServiceGroups, key = { it.titleRes }) { group ->
                ServiceGroupSection(
                    group = group,
                    openingService = openingService,
                    onOpenService = onOpenService,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServicesPagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        ServicesPage(
            openingService = null,
            onOpenService = {},
            onOpenUndergradEas = {},
            bottomPadding = 0.dp,
        )
    }
}
