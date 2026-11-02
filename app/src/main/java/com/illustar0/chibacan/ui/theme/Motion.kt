package com.illustar0.chibacan.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec

/** 不在分页器里、但要像翻页一样滑动的内容所用的 [PagerNavigationSpringSpec]。 */
val PageSlideSpec: SpringSpec<IntOffset> = spring(
    dampingRatio = PagerNavigationSpringSpec.dampingRatio,
    stiffness = PagerNavigationSpringSpec.stiffness,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
