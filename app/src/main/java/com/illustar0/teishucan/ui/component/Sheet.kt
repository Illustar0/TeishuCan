package com.illustar0.teishucan.ui.component

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.ui.theme.PageSlideSpec
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 打开 [uri]；设备上没有能处理它的应用时返回 false。 */
fun UriHandler.tryOpenUri(uri: String): Boolean = try {
    openUri(uri)
    true
} catch (_: IllegalArgumentException) {
    false
}

/** [rememberShownContent] 的结果。 */
class ShownContent<T : Any>(
    /** 此刻要画的内容，没有时为 null。 */
    val value: T?,
    /** 不再需要最后一个值时调用，例如交给弹层的 onDismissFinished。 */
    val release: () -> Unit,
)

/**
 * 让弹层或页面在 [current] 变成 null 之后还能画完离场动画。
 *
 * 弹层展示的东西（例如一次登录尝试）在用户关闭时就该结束，离场动画却在那之后才播放，还要画最后一帧。
 * 这里只为动画留住最后一个非 null 的值，直到 [ShownContent.release] 或离开组合。
 * 不能反过来等 onDismissFinished 才结束它：弹层在关闭途中被重建（例如转屏）时，这个回调根本不会来。
 */
@Composable
fun <T : Any> rememberShownContent(current: T?): ShownContent<T> {
    var last by remember { mutableStateOf<T?>(null) }
    SideEffect { if (current != null) last = current }
    return ShownContent(value = current ?: last, release = { last = null })
}

fun pageSlide(isForward: Boolean): ContentTransform {
    val direction = if (isForward) 1 else -1
    return slideInHorizontally(PageSlideSpec) { width -> direction * width } togetherWith
        slideOutHorizontally(PageSlideSpec) { width -> -direction * width }
}

@Composable
fun LoadingButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColorsPrimary(),
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors) {
        if (isLoading) {
            InfiniteProgressIndicator(color = LocalContentColor.current)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MiuixTheme.textStyles.button)
    }
}

@Composable
fun SheetActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MiuixTheme.colorScheme.onBackground
            } else {
                MiuixTheme.colorScheme.disabledOnSecondaryVariant
            },
        )
    }
}
