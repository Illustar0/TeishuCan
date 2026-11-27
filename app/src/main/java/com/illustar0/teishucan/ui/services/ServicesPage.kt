package com.illustar0.teishucan.ui.services

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.ui.component.SearchBarTrigger
import com.illustar0.teishucan.ui.component.SearchPager
import com.illustar0.teishucan.ui.component.SearchStatus
import com.illustar0.teishucan.ui.component.rememberSearchStatus
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.CardSpacing
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val SearchBarMaxTopPadding = 12.dp
private val SearchResultsTopPadding = 6.dp

private val ServiceGroups = ServiceGroup.entries.filterNot(ServiceGroup::isUndergradEas)

@Composable
fun ServicesPage(
    openingService: CampusService?,
    onOpen: (ServiceTarget) -> Unit,
    bottomPadding: Dp,
    onSearchActiveChange: (Boolean) -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val searchPlaceholder = stringResource(R.string.services_search_placeholder)
    var searchStatus by rememberSearchStatus()
    var scaffoldWindowY by remember { mutableFloatStateOf(0f) }
    var triggerWindowY by remember { mutableFloatStateOf(0f) }
    val searchBarTopPadding = { SearchBarMaxTopPadding * (1f - scrollBehavior.state.collapsedFraction) }
    val collapsedOffsetY = with(density) { (triggerWindowY - scaffoldWindowY).toDp() }
    val isSearchActive = !searchStatus.isCollapsed
    val isSearching = searchStatus.query.isNotBlank()
    val searchEntries = if (isSearching) matchingServiceEntries(searchStatus.query) else emptyList()
    val currentOnSearchActiveChange by rememberUpdatedState(onSearchActiveChange)

    LaunchedEffect(isSearchActive) {
        currentOnSearchActiveChange(isSearchActive)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnSearchActiveChange(false) }
    }

    Scaffold(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            scaffoldWindowY = coordinates.positionInWindow().y
        },
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.destination_services),
                    color = containerColor,
                    scrollBehavior = scrollBehavior,
                    bottomContent = {
                        SearchBarTrigger(
                            placeholder = searchPlaceholder,
                            isCollapsed = searchStatus.isCollapsed,
                            topPadding = searchBarTopPadding,
                            onPositionedWindowY = { triggerWindowY = it },
                            onExpand = {
                                searchStatus = searchStatus.copy(phase = SearchStatus.Phase.Expanding)
                            },
                        )
                    },
                )
            }
        },
        popupHost = {
            MiuixPopupHost()
            SearchPager(
                status = searchStatus,
                placeholder = searchPlaceholder,
                collapsedOffsetY = collapsedOffsetY,
                searchBarTopPadding = searchBarTopPadding,
                onStatusChange = { searchStatus = it },
            ) {
                if (isSearching) {
                    ServiceSearchResults(
                        entries = searchEntries,
                        openingService = openingService,
                        bottomPadding = bottomPadding,
                        onOpen = { target ->
                            focusManager.clearFocus()
                            onOpen(target)
                        },
                    )
                }
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
                bottom = bottomPadding + CardSpacing,
            ),
            overscrollEffect = null,
        ) {
            items(ServiceGroups, key = { it.titleRes }) { group ->
                ServiceGroupSection(
                    group = group,
                    openingService = openingService,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun ServiceSearchResults(
    entries: List<ServiceEntry>,
    openingService: CampusService?,
    bottomPadding: Dp,
    onOpen: (ServiceTarget) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.services_search_empty),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = PaddingValues(
            top = SearchResultsTopPadding,
            bottom = maxOf(imeBottomPadding, bottomPadding) + CardSpacing,
        ),
        overscrollEffect = null,
    ) {
        item(key = "results") {
            Card(modifier = Modifier.padding(horizontal = CardSpacing)) {
                entries.forEach { entry ->
                    ServiceEntryRow(
                        entry = entry,
                        isOpening = entry.isOpening(openingService),
                        onClick = { onOpen(entry.target) },
                    )
                }
            }
        }
    }
}

@Composable
private fun matchingServiceEntries(query: String): List<ServiceEntry> =
    ServiceEntry.entries.filter { entry ->
        matchesServiceQuery(
            query,
            stringResource(entry.titleRes),
            stringResource(entry.summaryRes),
            stringResource(entry.group.titleRes),
        )
    }

@Preview(showBackground = true)
@Composable
private fun ServicesPagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        ServicesPage(
            openingService = null,
            onOpen = {},
            bottomPadding = 0.dp,
        )
    }
}
