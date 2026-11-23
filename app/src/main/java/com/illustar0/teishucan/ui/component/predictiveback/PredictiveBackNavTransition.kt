// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 InstallerX Revived contributors
// Modified for TeishuCan on 2026-11-23 from InstallerX-Revived (ui/animation/predictiveback/InstallerNavTransition.kt): package and entry point renamed, bound to TeishuCan's ThemeSettings enums.
package com.illustar0.teishucan.ui.component.predictiveback

import com.illustar0.teishucan.domain.PredictiveBackAnimation
import com.illustar0.teishucan.domain.PredictiveBackExitDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

internal fun predictiveBackNavTransition(
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
): NavTransition = when (animation) {
    PredictiveBackAnimation.None -> NoPredictiveBackTransition
    PredictiveBackAnimation.Miuix -> NavTransitions.MiuixDefault
    PredictiveBackAnimation.Aosp -> AospNavTransition
    PredictiveBackAnimation.Scale -> scaleNavTransition(exitDirection)
    PredictiveBackAnimation.Classic -> ClassicNavTransition
}
