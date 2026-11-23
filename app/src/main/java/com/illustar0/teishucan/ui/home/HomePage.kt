package com.illustar0.teishucan.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ClassPeriods
import com.illustar0.teishucan.domain.NextClass
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.domain.UpcomingClass
import com.illustar0.teishucan.ui.component.RefreshContainer
import com.illustar0.teishucan.ui.component.TimetableDefaults
import com.illustar0.teishucan.ui.component.formatKilowattHours
import com.illustar0.teishucan.ui.component.formatYuan
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import java.math.BigDecimal
import java.time.LocalTime
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.ContactsBook
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val LeadCardTintAlpha = 0.16f
private const val LeadIconAlpha = 0.8f
private const val LeadDescriptionAlpha = 0.8f
private val LeadIconSize = 170.dp
private val LeadIconEndOverhang = 50.dp
private val LeadIconBottomOverhang = 38.dp

/** 让文字和节次条避开图标留在卡片内的部分。 */
private val LeadIconClearance = LeadIconSize - LeadIconEndOverhang - 8.dp

/** 足够高，让图标上沿留在卡片内。 */
private val LeadCardMinHeight = LeadIconSize - LeadIconBottomOverhang

private val PeriodStripHeight = 6.dp
private val PeriodStripGap = 3.dp
private const val UpcomingClassAlpha = 0.35f
private const val FinishedClassAlpha = 0.15f

private const val StatColumnCount = 2

data class HomeState(
    val upcomingClass: UpcomingClass = UpcomingClass.NotFetched,
    val campusCardBalanceYuan: BigDecimal? = null,
    val mailCount: Int? = null,
    val researchProjectCount: Int? = null,
    val electricityKilowattHours: Double? = null,
    val hasMeters: Boolean = false,
    val profile: StudentProfile? = null,
)

private data class HomeStat(
    val title: String,
    val value: AnnotatedString,
    val isPlaceholder: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val isHeldDown: Boolean = false,
)

private class HomeStatActions(
    val onOpenMail: () -> Unit,
    val onOpenElectricitySettings: () -> Unit,
    val onOpenRecharge: () -> Unit,
)

@Composable
fun HomePage(
    state: HomeState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isRechargeVisible: Boolean,
    onOpenRecharge: () -> Unit,
    onOpenMail: () -> Unit,
    onOpenElectricitySettings: () -> Unit,
    bottomPadding: Dp,
    rechargeSheet: @Composable () -> Unit = {},
) {
    val statActions = HomeStatActions(
        onOpenMail = onOpenMail,
        onOpenElectricitySettings = onOpenElectricitySettings,
        onOpenRecharge = onOpenRecharge,
    )

    HomeScaffold(
        bottomPadding = bottomPadding,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        overlay = rechargeSheet,
    ) {
        item(key = "next_class") {
            NextClassCard(upcomingClass = state.upcomingClass)
        }
        item(key = "stats") {
            StatGrid(stats = homeStats(state, statActions, isRechargeVisible))
        }
        item(key = "profile") {
            ProfileCard(profile = state.profile)
        }
        item(key = "links") {
            LinksCard()
        }
    }
}

@Composable
fun SignedOutHomePage(onLoginClick: () -> Unit, bottomPadding: Dp) {
    HomeScaffold(bottomPadding = bottomPadding, onRefresh = null) {
        item(key = "login") {
            LoginPromptCard(onClick = onLoginClick)
        }
        item(key = "links") {
            LinksCard()
        }
    }
}

@Composable
private fun HomeScaffold(
    bottomPadding: Dp,
    onRefresh: (() -> Unit)?,
    isRefreshing: Boolean = false,
    overlay: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.destination_home),
                    color = containerColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = bottomPadding + 12.dp,
        )
        val list = @Composable {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPadding,
                overscrollEffect = null,
                content = content,
            )
        }
        Box(modifier = Modifier.fillMaxSize().blurSource(backdrop)) {
            if (onRefresh == null) {
                list()
            } else {
                RefreshContainer(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    scrollBehavior = scrollBehavior,
                    contentPadding = contentPadding,
                    content = list,
                )
            }
        }
        overlay()
    }
}

@Composable
private fun LeadCard(
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            .semantics(mergeDescendants = true) {},
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primary.copy(alpha = LeadCardTintAlpha),
        ),
        pressFeedbackType = if (onClick == null) PressFeedbackType.None else PressFeedbackType.Sink,
        showIndication = onClick != null,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = LeadIconEndOverhang, y = LeadIconBottomOverhang),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(LeadIconSize),
                    tint = MiuixTheme.colorScheme.primary.copy(alpha = LeadIconAlpha),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LeadCardMinHeight)
                    .padding(start = 16.dp, end = LeadIconClearance, top = 16.dp, bottom = 16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun LoginPromptCard(onClick: () -> Unit) {
    LeadCard(icon = MiuixIcons.ContactsCircle, onClick = onClick) {
        LeadTitle(stringResource(R.string.home_login_title))
        Spacer(modifier = Modifier.height(2.dp))
        LeadDescription(stringResource(R.string.home_login_summary))
    }
}

@Composable
private fun NextClassCard(upcomingClass: UpcomingClass) {
    LeadCard(icon = MiuixIcons.Months) {
        when (upcomingClass) {
            UpcomingClass.NotFetched -> NoNextClass(stringResource(R.string.home_not_fetched))
            UpcomingClass.None -> NoNextClass(stringResource(R.string.home_next_class_none))
            is NextClass -> {
                LeadTitle(nextClassTitle(upcomingClass))
                Spacer(modifier = Modifier.height(2.dp))
                LeadDescription(
                    stringResource(
                        R.string.home_next_class_time,
                        upcomingClass.startTime.hour,
                        upcomingClass.startTime.minute,
                    ),
                )
                Spacer(modifier = Modifier.height(36.dp))
                LeadDescription(upcomingClass.location)
                DayPeriodStrip(nextClass = upcomingClass, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun NoNextClass(description: String) {
    Column {
        LeadTitle(stringResource(R.string.home_next_class))
        Spacer(modifier = Modifier.height(2.dp))
        LeadDescription(description)
    }
}

/** 明天的课在课程名前用主色标出，以免误以为是今天的课。 */
@Composable
private fun nextClassTitle(nextClass: NextClass): AnnotatedString {
    if (!nextClass.isTomorrow) return AnnotatedString(nextClass.name)
    val tomorrow = stringResource(R.string.home_next_class_tomorrow)
    val tomorrowStyle = SpanStyle(color = MiuixTheme.colorScheme.primary)
    return buildAnnotatedString {
        withStyle(tomorrowStyle) { append(tomorrow) }
        append(" ${nextClass.name}")
    }
}

@Composable
private fun LeadTitle(text: String) {
    LeadTitle(AnnotatedString(text))
}

@Composable
private fun LeadTitle(text: AnnotatedString) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title3,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LeadDescription(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = LeadDescriptionAlpha),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DayPeriodStrip(
    nextClass: NextClass,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PeriodStripGap),
    ) {
        for (period in 1..TimetableDefaults.PeriodCount) {
            val dayClass = nextClass.dayClasses.find { period in it }
            val color = when {
                dayClass == null -> colors.dividerLine
                dayClass == nextClass.periods -> colors.primary
                dayClass.startPeriod < nextClass.periods.startPeriod -> colors.primary.copy(alpha = FinishedClassAlpha)
                else -> colors.primary.copy(alpha = UpcomingClassAlpha)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PeriodStripHeight)
                    .squircleSurface(color = color, cornerRadius = PeriodStripHeight / 2),
            )
        }
    }
}

@Composable
private fun homeStats(
    state: HomeState,
    actions: HomeStatActions,
    isRechargeVisible: Boolean,
): List<HomeStat> {
    val notFetched = AnnotatedString(stringResource(R.string.home_not_fetched))
    val addMeterPrompt = AnnotatedString(stringResource(R.string.home_electricity_empty))
    val balance = state.campusCardBalanceYuan?.let { AnnotatedString(formatYuan(it)) }
    val mail = state.mailCount?.let { valueWithUnit(it.toString(), stringResource(R.string.home_mail_unit)) }
    val projects = state.researchProjectCount?.let {
        valueWithUnit(it.toString(), stringResource(R.string.home_research_project_unit))
    }
    val electricity = state.electricityKilowattHours?.let {
        valueWithUnit(formatKilowattHours(it), stringResource(R.string.home_kilowatt_hour_unit))
    }
    return listOf(
        HomeStat(
            title = stringResource(R.string.home_campus_card_balance),
            value = balance ?: notFetched,
            isPlaceholder = balance == null,
            onClick = actions.onOpenRecharge,
            isHeldDown = isRechargeVisible,
        ),
        HomeStat(
            title = stringResource(R.string.home_mail),
            value = mail ?: notFetched,
            isPlaceholder = mail == null,
            onClick = actions.onOpenMail,
        ),
        HomeStat(
            title = stringResource(R.string.home_research_projects),
            value = projects ?: notFetched,
            isPlaceholder = projects == null,
        ),
        HomeStat(
            title = stringResource(R.string.home_remaining_electricity),
            value = electricity ?: if (state.hasMeters) notFetched else addMeterPrompt,
            isPlaceholder = electricity == null,
            onClick = actions.onOpenElectricitySettings,
        ),
    )
}

/** 单位字号小于数字，让较长的数值也放得下半宽卡片。 */
@Composable
private fun valueWithUnit(value: String, unit: String): AnnotatedString {
    val unitStyle = SpanStyle(
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    return buildAnnotatedString {
        append(value)
        withStyle(unitStyle) { append(" $unit") }
    }
}

@Composable
private fun StatGrid(stats: List<HomeStat>) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stats.chunked(StatColumnCount).forEach { rowStats ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowStats.forEach { stat ->
                    StatCard(
                        stat = stat,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    stat: HomeStat,
    modifier: Modifier = Modifier,
) {
    val isInteractive = stat.onClick != null

    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        insideMargin = PaddingValues(16.dp),
        pressFeedbackType = if (isInteractive) PressFeedbackType.Sink else PressFeedbackType.None,
        showIndication = isInteractive,
        holdDownState = stat.isHeldDown,
        onClick = stat.onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stat.title,
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isInteractive) {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(width = 10.dp, height = 16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
            if (stat.isPlaceholder) {
                Text(
                    text = stat.value,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                Text(
                    text = stat.value,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: StudentProfile?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ProfileRow(
                icon = MiuixIcons.Contacts,
                title = stringResource(R.string.home_profile_name),
                content = profile?.name,
            )
            ProfileRow(
                icon = MiuixIcons.Community,
                title = stringResource(R.string.home_profile_faculty),
                content = profile?.faculty,
            )
            ProfileRow(
                icon = MiuixIcons.ContactsBook,
                title = stringResource(R.string.home_profile_identity),
                content = profile?.identityType,
                bottomPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    content: String?,
    bottomPadding: Dp = 24.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
        Column {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = content ?: stringResource(R.string.home_not_fetched),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun LinksCard() {
    val uriHandler = LocalUriHandler.current
    val supportUrl = stringResource(R.string.home_support_url)
    val learnUrl = stringResource(R.string.home_learn_url)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
    ) {
        LinkRow(
            icon = MiuixIcons.Favorites,
            title = stringResource(R.string.home_support_title),
            summary = stringResource(R.string.home_support_summary),
            onClick = { uriHandler.openUri(supportUrl) },
        )
        LinkRow(
            icon = MiuixIcons.Info,
            title = stringResource(R.string.home_learn_title),
            summary = stringResource(R.string.home_learn_summary),
            onClick = { uriHandler.openUri(learnUrl) },
        )
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
                tint = MiuixTheme.colorScheme.onBackground,
            )
        },
        onClick = onClick,
    )
}

@Preview(showBackground = true)
@Composable
private fun HomePagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        HomePage(
            state = HomeState(
                upcomingClass = NextClass(
                    name = "Sample Course",
                    location = "Sample Room",
                    startTime = LocalTime.of(14, 30),
                    periods = ClassPeriods(startPeriod = 5, periodSpan = 2),
                    dayClasses = listOf(
                        ClassPeriods(startPeriod = 1, periodSpan = 2),
                        ClassPeriods(startPeriod = 5, periodSpan = 2),
                        ClassPeriods(startPeriod = 8, periodSpan = 1),
                    ),
                    isTomorrow = false,
                ),
                campusCardBalanceYuan = "56.78".toBigDecimal(),
                mailCount = 12,
                researchProjectCount = 3,
                electricityKilowattHours = 165.95,
                profile = StudentProfile(
                    name = "Sample Name",
                    faculty = "Sample Faculty",
                    identityType = "Sample Type",
                ),
            ),
            isRefreshing = false,
            onRefresh = {},
            isRechargeVisible = false,
            onOpenRecharge = {},
            onOpenMail = {},
            onOpenElectricitySettings = {},
            bottomPadding = 0.dp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignedOutHomePagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        SignedOutHomePage(onLoginClick = {}, bottomPadding = 0.dp)
    }
}
