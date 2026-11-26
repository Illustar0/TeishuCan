package com.illustar0.teishucan.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.platform.PlatformCapabilities
import com.illustar0.teishucan.reminder.ClassReminderAccess
import com.illustar0.teishucan.reminder.missingClassReminderAccess
import com.illustar0.teishucan.reminder.openSettings
import com.illustar0.teishucan.ui.theme.ActionButtonSpacing
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.CardSpacing
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SettingsRoute(
    student: StudentProfile?,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenElectricitySettings: () -> Unit,
    onOpenAbout: () -> Unit,
    bottomPadding: Dp,
) {
    // 切换语言会重建 Activity，因此每个组合周期读取一次就够了。
    val appLanguage = remember { currentAppLanguage() }
    val classReminderViewModel = hiltViewModel<ClassReminderViewModel>()
    val classReminder by classReminderViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val checkReminderAccess = { classReminderViewModel.onAccessChecked(missingClassReminderAccess(context)) }
    // 用户可能从系统设置改完权限回来。
    LifecycleResumeEffect(classReminderViewModel, context) {
        checkReminderAccess()
        onPauseOrDispose {}
    }
    val notificationPermission = rememberLauncherForActivityResult(RequestPermission()) { checkReminderAccess() }

    SettingsPage(
        student = student,
        onLoginClick = onLoginClick,
        onLogout = onLogout,
        appLanguage = appLanguage,
        onAppLanguageChange = ::applyAppLanguage,
        onOpenThemeSettings = onOpenThemeSettings,
        onOpenElectricitySettings = onOpenElectricitySettings,
        classReminder = classReminder,
        onClassReminderEnabledChange = { isEnabled ->
            classReminderViewModel.setEnabled(isEnabled)
            val canRequestNotifications = PlatformCapabilities.isNotificationPermissionRequired &&
                classReminder?.missingAccess.orEmpty().contains(ClassReminderAccess.Notifications)
            if (isEnabled && canRequestNotifications) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onFixClassReminderAccess = { access -> access.openSettings(context) },
        onOpenAbout = onOpenAbout,
        bottomPadding = bottomPadding,
    )
}

@Composable
fun SettingsPage(
    student: StudentProfile?,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenElectricitySettings: () -> Unit,
    classReminder: ClassReminderSettingsState?,
    onClassReminderEnabledChange: (Boolean) -> Unit,
    onFixClassReminderAccess: (ClassReminderAccess) -> Unit,
    onOpenAbout: () -> Unit,
    bottomPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    var isLogoutVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.destination_settings),
                    color = containerColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .blurSource(backdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding + CardSpacing,
            ),
            overscrollEffect = null,
        ) {
            item(key = "account") {
                AccountCard(student = student, onLoginClick = onLoginClick)
            }
            item(key = "general") {
                Card(modifier = Modifier.padding(start = CardSpacing, end = CardSpacing, top = CardSpacing)) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_theme),
                        summary = stringResource(R.string.settings_theme_summary),
                        startAction = { SettingsRowIcon(MiuixIcons.Theme) },
                        onClick = onOpenThemeSettings,
                    )
                    if (student != null) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_electricity),
                            summary = stringResource(R.string.settings_electricity_summary),
                            startAction = { SettingsRowIcon(ImageVector.vectorResource(R.drawable.ic_lucide_zap)) },
                            onClick = onOpenElectricitySettings,
                        )
                    }
                    OverlayDropdownPreference(
                        title = stringResource(R.string.settings_language),
                        items = AppLanguage.entries.map { stringResource(it.labelRes) },
                        selectedIndex = appLanguage.ordinal,
                        startAction = { SettingsRowIcon(MiuixIcons.Translate) },
                        onSelectedIndexChange = { index -> onAppLanguageChange(AppLanguage.entries[index]) },
                    )
                }
            }
            // 提醒来自课表，只在登录期间有意义。
            if (student != null && classReminder != null) {
                item(key = "class_reminder") {
                    ClassReminderCard(
                        state = classReminder,
                        onEnabledChange = onClassReminderEnabledChange,
                        onFixAccess = onFixClassReminderAccess,
                    )
                }
            }
            item(key = "about") {
                Card(modifier = Modifier.padding(start = CardSpacing, end = CardSpacing, top = CardSpacing)) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.settings_about_summary),
                        startAction = { SettingsRowIcon(MiuixIcons.Info) },
                        onClick = onOpenAbout,
                    )
                }
            }
            if (student != null) {
                item(key = "logout") {
                    LogoutCard(onClick = { isLogoutVisible = true })
                }
            }
        }
        LogoutDialog(
            show = isLogoutVisible,
            onConfirm = onLogout,
            onDismissRequest = { isLogoutVisible = false },
        )
    }
}

@Composable
private fun AccountCard(student: StudentProfile?, onLoginClick: () -> Unit) {
    Card(modifier = Modifier.padding(start = CardSpacing, end = CardSpacing, top = CardSpacing)) {
        if (student == null) {
            ArrowPreference(
                title = stringResource(R.string.settings_login),
                summary = stringResource(R.string.settings_login_summary),
                startAction = { SettingsRowIcon(MiuixIcons.ContactsCircle) },
                onClick = onLoginClick,
            )
        } else {
            BasicComponent(
                title = student.name,
                summary = student.faculty,
                startAction = { SettingsRowIcon(MiuixIcons.ContactsCircle) },
            )
        }
    }
}

/** 上课提醒的开关。开着时，用户在系统设置里关掉的能力各占一行，点开就去系统设置里允许。 */
@Composable
private fun ClassReminderCard(
    state: ClassReminderSettingsState,
    onEnabledChange: (Boolean) -> Unit,
    onFixAccess: (ClassReminderAccess) -> Unit,
) {
    Card(modifier = Modifier.padding(start = CardSpacing, end = CardSpacing, top = CardSpacing)) {
        SwitchPreference(
            checked = state.isEnabled,
            onCheckedChange = onEnabledChange,
            title = stringResource(R.string.settings_class_reminder),
            summary = stringResource(R.string.settings_class_reminder_summary),
            startAction = {
                SettingsRowIcon(ImageVector.vectorResource(R.drawable.ic_lucide_calendar_clock))
            },
        )
        if (state.isEnabled) {
            for (access in state.missingAccess) {
                ArrowPreference(
                    title = stringResource(access.titleRes),
                    summary = stringResource(access.summaryRes),
                    onClick = { onFixAccess(access) },
                )
            }
        }
    }
}

@get:StringRes
private val ClassReminderAccess.titleRes: Int
    get() = when (this) {
        ClassReminderAccess.Notifications -> R.string.settings_class_reminder_notifications
        ClassReminderAccess.LiveUpdates -> R.string.settings_class_reminder_live_updates
        ClassReminderAccess.ExactAlarms -> R.string.settings_class_reminder_exact_alarms
    }

@get:StringRes
private val ClassReminderAccess.summaryRes: Int
    get() = when (this) {
        ClassReminderAccess.Notifications -> R.string.settings_class_reminder_notifications_summary
        ClassReminderAccess.LiveUpdates -> R.string.settings_class_reminder_live_updates_summary
        ClassReminderAccess.ExactAlarms -> R.string.settings_class_reminder_exact_alarms_summary
    }

@Composable
private fun LogoutCard(onClick: () -> Unit) {
    Card(modifier = Modifier.padding(start = CardSpacing, end = CardSpacing, top = CardSpacing)) {
        BasicComponent(
            title = stringResource(R.string.settings_logout),
            titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
            onClick = onClick,
        )
    }
}

@Composable
private fun LogoutDialog(
    show: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.logout_title),
        summary = stringResource(R.string.logout_summary),
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ActionButtonSpacing),
        ) {
            TextButton(
                text = stringResource(R.string.logout_cancel),
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.settings_logout),
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
            )
        }
    }
}

@Composable
private fun SettingsRowIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.padding(end = 6.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

@get:StringRes
private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.System -> R.string.language_system
        AppLanguage.Japanese -> R.string.language_japanese
        AppLanguage.Chinese -> R.string.language_chinese
    }

@Preview(showBackground = true)
@Composable
private fun SettingsPagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        SettingsPage(
            student = null,
            onLoginClick = {},
            onLogout = {},
            appLanguage = AppLanguage.System,
            onAppLanguageChange = {},
            onOpenThemeSettings = {},
            onOpenElectricitySettings = {},
            classReminder = null,
            onClassReminderEnabledChange = {},
            onFixClassReminderAccess = {},
            onOpenAbout = {},
            bottomPadding = 0.dp,
        )
    }
}
