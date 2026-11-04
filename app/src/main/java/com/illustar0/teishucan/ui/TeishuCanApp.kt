package com.illustar0.teishucan.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.ui.component.FailureToasts
import com.illustar0.teishucan.ui.component.OutcomeHaptics
import com.illustar0.teishucan.ui.component.isWideWindow
import com.illustar0.teishucan.ui.component.liquid.LiquidGlassNavigationBar
import com.illustar0.teishucan.ui.home.HomeRoute
import com.illustar0.teishucan.ui.home.SignedOutHomePage
import com.illustar0.teishucan.ui.services.ServicesRoute
import com.illustar0.teishucan.ui.services.UndergradEasRoute
import com.illustar0.teishucan.ui.settings.AboutPage
import com.illustar0.teishucan.ui.settings.AccountViewModel
import com.illustar0.teishucan.ui.settings.ElectricitySettingsRoute
import com.illustar0.teishucan.ui.settings.LoginSheet
import com.illustar0.teishucan.ui.settings.OpenSourceLicensesPage
import com.illustar0.teishucan.ui.settings.SettingsRoute
import com.illustar0.teishucan.ui.settings.ThemeSettingsPage
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.LocalIsBlurEnabled
import com.illustar0.teishucan.ui.theme.LocalIsLiquidGlassEnabled
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import com.illustar0.teishucan.ui.timetable.TimetableRoute
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage

@Serializable
private sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object ThemeSettings : Route

    @Serializable
    data object ElectricitySettings : Route

    @Serializable
    data object UndergradEas : Route

    @Serializable
    data object About : Route

    @Serializable
    data object OpenSourceLicenses : Route
}

private enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.destination_home, MiuixIcons.Home),
    Timetable(R.string.destination_timetable, MiuixIcons.Months),
    Services(R.string.destination_services, MiuixIcons.GridView),
    Settings(R.string.destination_settings, MiuixIcons.Settings),
}

private val SignedOutDestinations = listOf(AppDestination.Home, AppDestination.Settings)

@Composable
fun TeishuCanApp(
    themeSettings: ThemeSettings,
    onThemeSettingsUpdate: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val navController = rememberNavController<Route>(Route.Main)
    val navigate: (Route) -> Unit = { route ->
        // 转场期间入口仍可点击，而返回栈不接受重复的键。
        if (navController.backStack.last() != route) {
            navController.push(route)
        }
    }

    NavDisplay(navController = navController) {
        entry<Route.Main> {
            MainScreen(
                isFloatingNavigationBar = themeSettings.isFloatingNavigationBarEnabled,
                onNavigate = navigate,
            )
        }
        entry<Route.ThemeSettings> {
            CommitOnlyBackHandler(
                enabled = !themeSettings.isPredictiveBackEnabled,
                onCommit = { navController.pop() },
            )
            ThemeSettingsPage(
                settings = themeSettings,
                onUpdate = onThemeSettingsUpdate,
                onBack = { navController.pop() },
            )
        }
        entry<Route.ElectricitySettings> {
            CommitOnlyBackHandler(
                enabled = !themeSettings.isPredictiveBackEnabled,
                onCommit = { navController.pop() },
            )
            ElectricitySettingsRoute(onBack = { navController.pop() })
        }
        entry<Route.UndergradEas> {
            CommitOnlyBackHandler(
                enabled = !themeSettings.isPredictiveBackEnabled,
                onCommit = { navController.pop() },
            )
            UndergradEasRoute(onBack = { navController.pop() })
        }
        entry<Route.About> {
            CommitOnlyBackHandler(
                enabled = !themeSettings.isPredictiveBackEnabled,
                onCommit = { navController.pop() },
            )
            AboutPage(
                onBack = { navController.pop() },
                onOpenOpenSourceLicenses = { navigate(Route.OpenSourceLicenses) },
            )
        }
        entry<Route.OpenSourceLicenses> {
            CommitOnlyBackHandler(
                enabled = !themeSettings.isPredictiveBackEnabled,
                onCommit = { navController.pop() },
            )
            OpenSourceLicensesPage(onBack = { navController.pop() })
        }
    }
}

/** 优先于 NavDisplay 的处理器，页面不跟手，只在松手时返回。 */
@Composable
private fun CommitOnlyBackHandler(enabled: Boolean, onCommit: () -> Unit) {
    PredictiveBackHandler(
        enabled = enabled,
        onProgress = { events -> events.collect() },
        onCommit = onCommit,
        onCancel = {},
    )
}

@Composable
private fun MainScreen(
    isFloatingNavigationBar: Boolean,
    onNavigate: (Route) -> Unit,
) {
    val appViewModel = hiltViewModel<AppViewModel>()
    val accountViewModel = hiltViewModel<AccountViewModel>()
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    // 读出保存的会话之前什么也不画，已登录用户才不会看到一闪而过的登录页。
    if (account == AccountState.Restoring) return
    val student = (account as? AccountState.SignedIn)?.profile
    val loginForm = accountViewModel.loginForm
    val onLoginClick = { loginForm.isVisible = true }
    // 用状态保存：页数会先于 Scaffold 子组合分页内容而变化，内容捕获的列表届时会比页数短。
    val destinations by rememberUpdatedState(
        if (student == null) SignedOutDestinations else AppDestination.entries,
    )
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val coroutineScope = rememberCoroutineScope()
    // 分页器要到本次组合之后的布局阶段才会落到变短列表中的页面。
    val selectedDestination = destinations[pagerState.targetPage.coerceAtMost(destinations.lastIndex)]
    val onDestinationSelect: (AppDestination) -> Unit = { destination ->
        coroutineScope.launch { pagerState.springAnimateToPage(destinations.indexOf(destination)) }
    }
    val isWide = isWideWindow()
    // 只有底栏采样这一层；侧边导航在内容旁边，不在内容之上。
    val isBarBlurEnabled = LocalIsBlurEnabled.current || (isFloatingNavigationBar && LocalIsLiquidGlassEnabled.current)
    val backdrop = rememberBlurBackdrop(isBarBlurEnabled).takeUnless { isWide }

    Scaffold(
        bottomBar = {
            if (!isWide) {
                AppBottomBar(
                    destinations = destinations,
                    selectedDestination = selectedDestination,
                    onDestinationSelect = onDestinationSelect,
                    isFloating = isFloatingNavigationBar,
                    backdrop = backdrop,
                )
            }
        },
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()

        Row(modifier = Modifier.fillMaxSize().blurSource(backdrop)) {
            if (isWide) {
                NavigationRail {
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == selectedDestination,
                            onClick = { onDestinationSelect(destination) },
                            icon = destination.icon,
                            label = stringResource(destination.labelRes),
                        )
                    }
                }
            }
            AppPager(
                pagerState = pagerState,
                destinationAt = { page -> destinations[page] },
                modifier = Modifier
                    .weight(1f)
                    // 侧边导航已经覆盖了窗口的起始侧。
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            if (isWide) WindowInsetsSides.End else WindowInsetsSides.Horizontal,
                        ),
                    ),
            ) { destination ->
                when (destination) {
                    AppDestination.Home -> if (student == null) {
                        SignedOutHomePage(onLoginClick = onLoginClick, bottomPadding = bottomPadding)
                    } else {
                        HomeRoute(
                            onOpenElectricitySettings = { onNavigate(Route.ElectricitySettings) },
                            bottomPadding = bottomPadding,
                        )
                    }

                    AppDestination.Timetable -> TimetableRoute(bottomPadding = bottomPadding)

                    AppDestination.Services -> ServicesRoute(
                        onOpenUndergradEas = { onNavigate(Route.UndergradEas) },
                        bottomPadding = bottomPadding,
                    )

                    AppDestination.Settings -> SettingsRoute(
                        student = student,
                        onLoginClick = onLoginClick,
                        onLogout = accountViewModel::logout,
                        onOpenThemeSettings = { onNavigate(Route.ThemeSettings) },
                        onOpenElectricitySettings = { onNavigate(Route.ElectricitySettings) },
                        onOpenAbout = { onNavigate(Route.About) },
                        bottomPadding = bottomPadding,
                    )
                }
            }
        }
        // 全应用刷新的失败与当前在哪一页无关。
        FailureToasts(appViewModel.refreshTasks)
        // 放在分页器之上，因为登录成功后打开它的页面会被替换。
        OutcomeHaptics(accountViewModel.loginOutcomes)
        LoginSheet(
            form = loginForm,
            onSubmitCredentials = accountViewModel::submitCredentials,
            onSendMfaCode = accountViewModel::sendMfaCode,
            onVerifyMfa = accountViewModel::verifyMfa,
            onDismissRequest = { loginForm.isVisible = false },
        )
    }
}

@Composable
private fun AppBottomBar(
    destinations: List<AppDestination>,
    selectedDestination: AppDestination,
    onDestinationSelect: (AppDestination) -> Unit,
    isFloating: Boolean,
    backdrop: LayerBackdrop?,
) {
    if (isFloating) {
        // 底栏只在自身尺寸变化时重新计算标签宽度，标签数变化需要新建底栏。
        key(destinations.size) {
            LiquidGlassNavigationBar(
                items = destinations.map { NavigationItem(stringResource(it.labelRes), it.icon) },
                selectedIndex = destinations.indexOf(selectedDestination),
                onItemClick = { index -> onDestinationSelect(destinations[index]) },
                backdrop = backdrop,
                isLiquidGlass = backdrop != null && LocalIsLiquidGlassEnabled.current,
            )
        }
    } else {
        BlurredBar(backdrop) { containerColor ->
            NavigationBar(color = containerColor) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selectedDestination,
                        onClick = { onDestinationSelect(destination) },
                        icon = destination.icon,
                        label = stringResource(destination.labelRes),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPager(
    pagerState: PagerState,
    destinationAt: (page: Int) -> AppDestination,
    modifier: Modifier = Modifier,
    pageContent: @Composable (AppDestination) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = PagerNavigationSpringSpec,
        ),
        // 登录或退出改变导航项时，让用户留在同一页。
        key = destinationAt,
    ) { page ->
        pageContent(destinationAt(page))
    }
}
