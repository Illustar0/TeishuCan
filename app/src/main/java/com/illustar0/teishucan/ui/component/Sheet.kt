package com.illustar0.teishucan.ui.component

import android.content.ClipData
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.ui.theme.PageSlideSpec
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val CopiedIndicatorDuration = 2.seconds

/** 行首图标与文字之间的距离：[BasicComponent] 自带 8 dp，这里补足到与设置页的行图标一致。 */
private val DetailRowIconEndPadding = 6.dp

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

/** 行首图标的读屏文字是这一行的类别（[label]），长按整行复制 [copyText]。 */
@Composable
fun DetailRow(
    icon: ImageVector,
    label: String,
    title: String,
    summary: String? = null,
    copyLabel: String = label,
    copyText: String = title,
    onCopy: () -> Unit = {},
    endActions: @Composable (RowScope.() -> Unit)? = null,
) {
    val copy = rememberClipboardCopy(label = copyLabel, text = copyText, onCopy = onCopy)
    BasicComponent(
        modifier = Modifier.combinedClickable(
            onLongClickLabel = stringResource(R.string.timetable_detail_copy),
            onLongClick = copy,
            onClick = {},
        ),
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.padding(end = DetailRowIconEndPadding),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        endActions = endActions,
    )
}

/** 地点和教室名常要拿去导航或发给同学，所以单独给一个复制按钮，复制后短暂显示对勾。 */
@Composable
fun CopyableDetailRow(
    icon: ImageVector,
    label: String,
    title: String,
    summary: String? = null,
    copyLabel: String = label,
    copyText: String = title,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isCopied by remember(copyText) { mutableStateOf(false) }
    val markCopied = { isCopied = true }
    val copy = rememberClipboardCopy(
        label = copyLabel,
        text = copyText,
        onCopy = markCopied,
    )

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(CopiedIndicatorDuration)
            isCopied = false
        }
    }

    DetailRow(
        icon = icon,
        label = label,
        title = title,
        summary = summary,
        copyLabel = copyLabel,
        copyText = copyText,
        onCopy = markCopied,
    ) {
        IconButton(
            onClick = {
                copy()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
        ) {
            Crossfade(targetState = isCopied, label = "DetailCopyIcon") { copied ->
                Icon(
                    imageVector = if (copied) MiuixIcons.Ok else MiuixIcons.Copy,
                    contentDescription = stringResource(R.string.timetable_detail_copy),
                    tint = if (copied) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantActions
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberClipboardCopy(
    label: String,
    text: String,
    onCopy: () -> Unit = {},
): () -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    return {
        coroutineScope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
            onCopy()
        }
    }
}
