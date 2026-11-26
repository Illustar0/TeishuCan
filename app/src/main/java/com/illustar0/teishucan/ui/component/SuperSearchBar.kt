// SPDX-License-Identifier: GPL-3.0-only
// Adapted from compose-miuix-ui/miuix (Apache-2.0) and tiann/KernelSU (GPL-3.0).

package com.illustar0.teishucan.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.illustar0.teishucan.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.basic.SearchClearButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val ExpandDurationMillis = 300
private const val SurfaceFadeDurationMillis = 200
private const val SearchOverlayZIndex = 5f
private val ExpandedTopGap = 5.dp
private val CapsuleHorizontalPadding = 12.dp
private val CapsuleBottomPadding = 6.dp

/** [SearchClearButton] 把清除图形固定画成 20dp，搜索图标取相同大小。 */
private val IconGlyphSize = 20.dp
private val IconOuterPadding = 16.dp
private val IconInnerPadding = 8.dp

/**
 * [SearchClearButton] 在传入的 modifier 之后才接 clickable，用 padding 留白会落到可点区域之外，
 * 所以只给尺寸、让图形居中：两侧各留 [IconOuterPadding]，整块高度与输入框一致。
 */
private val ClearButtonSize = DpSize(
    width = IconOuterPadding + IconGlyphSize + IconOuterPadding,
    height = SearchBarDefaults.InputFieldMinHeight,
)

private val CancelStartPadding = 4.dp
private val CancelEndPadding = 16.dp
private val CancelVerticalPadding = 8.dp

/** 搜索页展开与收起的状态机；动画结束后调用 [onAnimationComplete] 推进到稳态。 */
@Immutable
data class SearchStatus(
    val query: String = "",
    val phase: Phase = Phase.Collapsed,
) {
    val isCollapsed: Boolean
        get() = phase == Phase.Collapsed
    val isExpanded: Boolean
        get() = phase == Phase.Expanded
    val shouldExpand: Boolean
        get() = phase == Phase.Expanding || phase == Phase.Expanded
    val shouldCollapse: Boolean
        get() = phase == Phase.Collapsed || phase == Phase.Collapsing

    fun onAnimationComplete(): SearchStatus = when (phase) {
        Phase.Expanding -> copy(phase = Phase.Expanded)
        Phase.Collapsing -> copy(query = "", phase = Phase.Collapsed)
        Phase.Collapsed, Phase.Expanded -> this
    }

    enum class Phase { Collapsed, Expanding, Expanded, Collapsing }
}

/** 嵌入顶栏底部的收起态搜索胶囊；点击后由 [SearchPager] 接管并上移展开。 */
@Composable
fun SearchBarTrigger(
    placeholder: String,
    isCollapsed: Boolean,
    topPadding: () -> Dp,
    onPositionedWindowY: (Float) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnExpand by rememberUpdatedState(onExpand)
    Box(
        modifier = modifier
            .alpha(if (isCollapsed) 1f else 0f)
            .onGloballyPositioned { coordinates -> onPositionedWindowY(coordinates.positionInWindow().y) }
            .then(
                if (isCollapsed) {
                    Modifier
                        .semantics {
                            onClick {
                                currentOnExpand()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { currentOnExpand() }
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        InputField(
            query = "",
            onQueryChange = {},
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CapsuleHorizontalPadding)
                .padding(bottom = CapsuleBottomPadding)
                .topInset(topPadding),
            label = placeholder,
            enabled = false,
            leadingIcon = { SearchLeadingIcon() },
        )
    }
}

/** 全屏搜索浮层，放在 `Scaffold` 的 `popupHost` 中，使胶囊从 [collapsedOffsetY] 平滑上移覆盖顶栏。 */
@Composable
fun SearchPager(
    status: SearchStatus,
    placeholder: String,
    collapsedOffsetY: Dp,
    searchBarTopPadding: () -> Dp,
    onStatusChange: (SearchStatus) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val latestStatus by rememberUpdatedState(status)
    val currentOnStatusChange by rememberUpdatedState(onStatusChange)
    val focusManager = LocalFocusManager.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val targetTopPadding = if (status.shouldExpand) {
        statusBarTop + ExpandedTopGap
    } else {
        max(collapsedOffsetY, 0.dp)
    }
    val topPadding by animateDpAsState(
        targetValue = targetTopPadding,
        animationSpec = tween(ExpandDurationMillis, easing = LinearOutSlowInEasing),
        label = "SearchPagerTopPadding",
        finishedListener = { currentOnStatusChange(latestStatus.onAnimationComplete()) },
    )
    // 若起止位置恰好相等，动画不会触发 finishedListener，直接推进到稳态避免卡住。
    LaunchedEffect(status.phase, targetTopPadding) {
        val isTransitioning = status.phase == SearchStatus.Phase.Expanding ||
            status.phase == SearchStatus.Phase.Collapsing
        if (isTransitioning && topPadding == targetTopPadding) {
            currentOnStatusChange(latestStatus.onAnimationComplete())
        }
    }
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (status.shouldExpand) 1f else 0f,
        animationSpec = tween(SurfaceFadeDurationMillis, easing = FastOutSlowInEasing),
        label = "SearchPagerSurfaceAlpha",
    )
    val surfaceColor = MiuixTheme.colorScheme.surface
    val collapseSearch: () -> Unit = {
        focusManager.clearFocus()
        currentOnStatusChange(latestStatus.copy(query = "", phase = SearchStatus.Phase.Collapsing))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .zIndex(SearchOverlayZIndex)
            .drawBehind { drawRect(surfaceColor.copy(alpha = surfaceAlpha)) }
            .then(
                if (!status.isCollapsed) {
                    // 展开与动画期间拦截点击和水平滑动，防止穿透到底层页面或翻动外层 Pager。
                    Modifier
                        .pointerInput(Unit) { detectTapGestures {} }
                        .draggable(
                            state = rememberDraggableState {},
                            orientation = Orientation.Horizontal,
                        )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .topInset { topPadding }
                .then(if (!status.isCollapsed) Modifier.background(surfaceColor) else Modifier),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!status.isCollapsed) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(surfaceColor),
                ) {
                    ExpandedSearchField(
                        status = status,
                        placeholder = placeholder,
                        topPadding = searchBarTopPadding,
                        onQueryChange = { query -> currentOnStatusChange(latestStatus.copy(query = query)) },
                        onSearch = { focusManager.clearFocus() },
                    )
                }
            }
            CapturedAnimatedVisibility(
                visible = status.shouldExpand,
                enter = expandHorizontally() + slideInHorizontally(initialOffsetX = { it }),
                exit = shrinkHorizontally() + slideOutHorizontally(targetOffsetX = { it }),
            ) {
                Text(
                    text = stringResource(R.string.services_search_cancel),
                    modifier = Modifier
                        .topInset(searchBarTopPadding)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = status.isExpanded,
                            role = Role.Button,
                            onClick = collapseSearch,
                        )
                        .padding(
                            start = CancelStartPadding,
                            top = CancelVerticalPadding,
                            end = CancelEndPadding,
                            bottom = CapsuleBottomPadding + CancelVerticalPadding,
                        ),
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = SearchBarDefaults.InputFieldFontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        AnimatedVisibility(
            visible = status.isExpanded,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            content()
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = status.shouldExpand,
        onBackCompleted = collapseSearch,
    )
}

@Composable
private fun ExpandedSearchField(
    status: SearchStatus,
    placeholder: String,
    topPadding: () -> Dp,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val clearDescription = stringResource(R.string.services_search_clear)

    InputField(
        query = status.query,
        onQueryChange = onQueryChange,
        onSearch = { onSearch() },
        expanded = status.shouldExpand,
        onExpandedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CapsuleHorizontalPadding)
            .padding(bottom = CapsuleBottomPadding)
            .topInset(topPadding)
            .focusRequester(focusRequester),
        label = placeholder,
        leadingIcon = { SearchLeadingIcon() },
        trailingIcon = {
            CapturedAnimatedVisibility(
                visible = status.query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                SearchClearButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(ClearButtonSize),
                    contentDescription = clearDescription,
                )
            }
        },
    )

    LaunchedEffect(status.shouldExpand) {
        if (status.shouldExpand) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
private fun SearchLeadingIcon() {
    Icon(
        imageVector = MiuixIcons.Basic.Search,
        contentDescription = null,
        modifier = Modifier
            .padding(start = IconOuterPadding, end = IconInnerPadding)
            .size(IconGlyphSize),
        tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
    )
}

/** 在布局阶段读取 [inset] 作为顶部留白，避免滚动或动画期间每帧重组调用方。 */
private fun Modifier.topInset(inset: () -> Dp): Modifier = layout { measurable, constraints ->
    val insetPx = inset().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(vertical = -insetPx))
    layout(placeable.width, placeable.height + insetPx) {
        placeable.placeRelative(0, insetPx)
    }
}
