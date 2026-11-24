package com.illustar0.teishucan.ui.services

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal val UndergradEasGroups = listOf(
    ServiceGroup(
        titleRes = R.string.services_eas_records,
        services = listOf(
            CampusService.StudentStatus,
            CampusService.ProgramCompletion,
            CampusService.AcademicWarnings,
        ),
    ),
    ServiceGroup(
        titleRes = R.string.services_eas_exams_and_grades,
        services = listOf(CampusService.Exams, CampusService.Grades),
    ),
    ServiceGroup(
        titleRes = R.string.services_eas_schedule,
        services = listOf(
            CampusService.Timetable,
            CampusService.CourseOfferings,
            CampusService.SchoolCalendar,
            CampusService.AvailableClassrooms,
            CampusService.ClassroomTimetable,
        ),
    ),
)

@Composable
fun UndergradEasPage(
    openingService: CampusService?,
    onOpenService: (CampusService) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.service_undergrad_eas),
                    color = containerColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.navigate_back),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
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
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            items(UndergradEasGroups, key = { it.titleRes }) { group ->
                ServiceGroupSection(
                    group = group,
                    openingService = openingService,
                    onOpenService = onOpenService,
                )
            }
            item(key = "end") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UndergradEasPagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        UndergradEasPage(openingService = null, onOpenService = {}, onBack = {})
    }
}
