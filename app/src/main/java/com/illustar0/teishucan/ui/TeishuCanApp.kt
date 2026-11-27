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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.PredictiveBackAnimation
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.platform.PlatformCapabilities
import com.illustar0.teishucan.ui.classrooms.AvailableClassroomsRoute
import com.illustar0.teishucan.ui.component.FailureToasts
import com.illustar0.teishucan.ui.component.OutcomeHaptics
import com.illustar0.teishucan.ui.component.isWideWindow
import com.illustar0.teishucan.ui.component.liquid.LiquidGlassNavigationBar
import com.illustar0.teishucan.ui.component.predictiveback.predictiveBackNavTransition
import com.illustar0.teishucan.ui.home.HomeRoute
import com.illustar0.teishucan.ui.home.SignedOutHomePage
import com.illustar0.teishucan.ui.services.NativeService
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
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage

@Serializable
internal sealed interface Route : NavKey {
    /** 只在登录期间有意义的页面，不能比登录活得久。 */
    val requiresSignIn: Boolean
        get() = false

    @Serializable
    data object Main : Route

    @Serializable
    data object ThemeSettings : Route

    @Serializable
    data object ElectricitySettings : Route {
        override val requiresSignIn: Boolean
            get() = true
    }

    @Serializable
    data object UndergradEas : Route {
        override val requiresSignIn: Boolean
            get() = true
    }

    @Serializable
    data object AvailableClassrooms : Route {
        override val requiresSignIn: Boolean
            get() = true
    }

    @Serializable
    data object About : Route

    @Serializable
    data object OpenSourceLicenses : Route
}

private fun NativeService.toRoute(): Route = when (this) {
    NativeService.UndergradEas -> Route.UndergradEas
    NativeService.AvailableClassrooms -> Route.AvailableClassrooms
}

internal enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    /** 只在登录期间显示的分页。 */
    val requiresSignIn: Boolean,
) {
    Home(R.string.destination_home, MiuixIcons.Home, requiresSignIn = false),
    Timetable(R.string.destination_timetable, MiuixIcons.Months, requiresSignIn = true),
    Services(R.string.destination_services, MiuixIcons.GridView, requiresSignIn = true),
    Settings(R.string.destination_settings, MiuixIcons.Settings, requiresSignIn = false),
}

private val SignedOutDestinations = AppDestination.entries.filterNot(AppDestination::requiresSignIn)
private val FallbackCardCornerRadius = 32.dp
private const val TransitionDimAmount = 0.5f

@Composable
internal fun TeishuCanApp(
    themeSettings: ThemeSettings,
    onThemeSettingsUpdate: ((ThemeSettings) -> ThemeSettings) -> Unit,
    isSignedOut: Boolean,
) {
    // 返回栈与当前分页都存进 Activity 的保存状态，转屏和进程被回收后都由系统交还。
    val navController = rememberNavController<Route>(Route.Main)
    // 会话因请求失败而结束，或恢复出的返回栈比登录活得久时，用户可能正停在需要登录的页面上，要让它们离开。
    LaunchedEffect(navController, isSignedOut) {
        if (isSignedOut) navController.backStack.removeAll { it is Route && it.requiresSignIn }
    }
    // 记在 NavDisplay 之外：Activity 重建后分页器从这里回到原来的分页。
    var destination by rememberSaveable { mutableStateOf(AppDestination.Home) }
    val navigate: (Route) -> Unit = { route ->
        // 转场期间入口仍可点击，而返回栈不接受重复的键。
        if (navController.backStack.last() != route) {
            navController.push(route)
        }
    }
    val predictiveBackAnimation =
        themeSettings.effectivePredictiveBackAnimation(PlatformCapabilities.isPredictiveBackSupported)
    val transition = remember(predictiveBackAnimation, themeSettings.predictiveBackExitDirection) {
        predictiveBackNavTransition(
            animation = predictiveBackAnimation,
            exitDirection = themeSettings.predictiveBackExitDirection,
        )
    }
    val navCornerRadius = rememberNavSystemCornerRadius()
    val roundsAllCorners = when (predictiveBackAnimation) {
        PredictiveBackAnimation.Aosp,
        PredictiveBackAnimation.Scale,
        PredictiveBackAnimation.Classic,
        -> true

        PredictiveBackAnimation.None,
        PredictiveBackAnimation.Miuix,
        -> false
    }
    val backdropColor = MiuixTheme.colorScheme.surface
    val effects = remember(navCornerRadius, roundsAllCorners, backdropColor) {
        NavDisplayEffects(
            enableCornerClip = true,
            cornerClipRadius = if (roundsAllCorners && navCornerRadius <= 0.dp) {
                FallbackCardCornerRadius
            } else {
                navCornerRadius
            },
            cornerClipMode = if (roundsAllCorners) NavCornerClipMode.All else NavCornerClipMode.Leading,
            dimAmount = TransitionDimAmount,
            backdropColor = backdropColor,
            blockInputDuringTransition = false,
        )
    }
    val isPredictiveBackDisabled = predictiveBackAnimation == PredictiveBackAnimation.None

    NavDisplay(
        navController = navController,
        transition = transition,
        effects = effects,
    ) {
        entry<Route.Main> {
            MainScreen(
                isFloatingNavigationBar = themeSettings.isFloatingNavigationBarEnabled,
                initialDestination = destination,
                onNavigate = navigate,
                onDestinationChange = { destination = it },
            )
        }
        entry<Route.ThemeSettings> {
            CommitOnlyBackHandler(
                enabled = isPredictiveBackDisabled,
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
                enabled = isPredictiveBackDisabled,
                onCommit = { navController.pop() },
            )
            ElectricitySettingsRoute(onBack = { navController.pop() })
        }
        entry<Route.UndergradEas> {
            CommitOnlyBackHandler(
                enabled = isPredictiveBackDisabled,
                onCommit = { navController.pop() },
            )
            UndergradEasRoute(
                onOpenNative = { service -> navigate(service.toRoute()) },
                onBack = { navController.pop() },
            )
        }
        entry<Route.AvailableClassrooms> {
            CommitOnlyBackHandler(
                enabled = isPredictiveBackDisabled,
                onCommit = { navController.pop() },
            )
            AvailableClassroomsRoute(onBack = { navController.pop() })
        }
        entry<Route.About> {
            CommitOnlyBackHandler(
                enabled = isPredictiveBackDisabled,
                onCommit = { navController.pop() },
            )
            AboutPage(
                onBack = { navController.pop() },
                onOpenOpenSourceLicenses = { navigate(Route.OpenSourceLicenses) },
            )
        }
        entry<Route.OpenSourceLicenses> {
            CommitOnlyBackHandler(
                enabled = isPredictiveBackDisabled,
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
    initialDestination: AppDestination,
    onNavigate: (Route) -> Unit,
    onDestinationChange: (AppDestination) -> Unit,
) {
    val appViewModel = hiltViewModel<AppViewModel>()
    val accountViewModel = hiltViewModel<AccountViewModel>()
    // 启动屏等到会话恢复完才放行，这里不会再看到 Restoring。
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val student = (account as? AccountState.SignedIn)?.profile
    val onLoginClick = accountViewModel::startLogin
    // 用状态保存：页数会先于 Scaffold 子组合分页内容而变化，内容捕获的列表届时会比页数短。
    val destinations by rememberUpdatedState(
        if (student == null) SignedOutDestinations else AppDestination.entries,
    )
    val pagerState = rememberPagerState(
        initialPage = destinations.indexOf(initialDestination).coerceAtLeast(0),
        pageCount = { destinations.size },
    )
    val recordDestination by rememberUpdatedState(onDestinationChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { destinations[pagerState.settledPage.coerceAtMost(destinations.lastIndex)] }
            .collect { recordDestination(it) }
    }
    val coroutineScope = rememberCoroutineScope()
    // 分页器要到本次组合之后的布局阶段才会落到变短列表中的页面。
    val selectedDestination = destinations[pagerState.targetPage.coerceAtMost(destinations.lastIndex)]
    var isServiceSearchActive by remember { mutableStateOf(false) }
    val isPagerSwipeEnabled = !(selectedDestination == AppDestination.Services && isServiceSearchActive)
    val onDestinationSelect: (AppDestination) -> Unit = { destination ->
        coroutineScope.launch { pagerState.springAnimateToPage(destinations.indexOf(destination)) }
    }
    val isWide = isWideWindow()
    // 只有底栏采样这一层；侧边导航在内容旁边，不在内容之上。
    val isBarBlurEnabled = LocalIsBlurEnabled.current || (isFloatingNavigationBar && LocalIsLiquidGlassEnabled.current)
    val backdrop = rememberBlurBackdrop(isBarBlurEnabled && !isWide)

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
                isUserScrollEnabled = isPagerSwipeEnabled,
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
                        onOpenNative = { service -> onNavigate(service.toRoute()) },
                        bottomPadding = bottomPadding,
                        onSearchActiveChange = { isServiceSearchActive = it },
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
        FailureToasts(appViewModel.refreshFailure)
        FailureToasts(accountViewModel.logoutFailure, fallbackMessage = R.string.logout_failed)
        // 放在分页器之上，因为登录成功后打开它的页面会被替换。
        OutcomeHaptics(accountViewModel.loginOutcomes)
        LoginSheet(
            attempt = accountViewModel.login,
            passwordSave = accountViewModel.pendingPasswordSave,
            onConsumePasswordSave = accountViewModel::consumePasswordSave,
            onSubmitCredentials = accountViewModel::submitCredentials,
            onSubmitSavedPassword = accountViewModel::submitSavedPassword,
            onSendMfaCode = accountViewModel::sendMfaCode,
            onVerifyMfa = accountViewModel::verifyMfa,
            onLeaveMfa = accountViewModel::leaveMfa,
            onDismissRequest = accountViewModel::cancelLogin,
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
    isUserScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
    pageContent: @Composable (AppDestination) -> Unit,
) {
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = PagerNavigationSpringSpec,
    )
    HorizontalPager(
        state = pagerState,
        modifier = modifier.pagerGestureOverride(
            pagerState = pagerState,
            flingBehavior = flingBehavior,
            enabled = isUserScrollEnabled,
        ),
        verticalAlignment = Alignment.Top,
        userScrollEnabled = false,
        flingBehavior = flingBehavior,
        pageNestedScrollConnection = PagerGestureNestedScrollConnection,
        overscrollEffect = null,
        // 登录或退出改变导航项时，让用户留在同一页。
        key = destinationAt,
    ) { page ->
        pageContent(destinationAt(page))
    }
}
