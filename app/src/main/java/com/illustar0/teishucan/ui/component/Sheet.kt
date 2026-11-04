package com.illustar0.teishucan.ui.component

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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

fun pageSlide(isForward: Boolean): ContentTransform {
    val direction = if (isForward) 1 else -1
    return slideInHorizontally(PageSlideSpec) { width -> direction * width } togetherWith
        slideOutHorizontally(PageSlideSpec) { width -> -direction * width }
}

/** [isLoading] 期间在文字前转圈，告诉用户请求还在进行。 */
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
