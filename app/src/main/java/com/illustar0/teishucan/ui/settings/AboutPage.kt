@file:OptIn(ExperimentalScrollBarApi::class)

package com.illustar0.teishucan.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.illustar0.teishucan.BuildConfig
import com.illustar0.teishucan.R
import com.illustar0.teishucan.ui.component.effect.BgEffectBackground
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.LocalIsDarkTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val SourceCodeUrl = "https://github.com/Illustar0/TeishuCan"
private const val LicenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val LogoSpacerKey = "logoSpacer"

private val HeaderTopPadding = 40.dp + 52.dp
private val HeaderBottomGap = 126.dp
private val TextureShape = RoundedCornerShape(16.dp)
private const val CardBlurRadius = 60f
private const val LogoBlurRadius = 200f
private const val TitleBlurRadius = 150f

// 以下混色取自 Miuix 示例应用的 ColorBlendToken 与关于页。
private val PuredRegularLight = listOf(
    BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
    BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
)
private val OverlayExtraThinDark = listOf(
    BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity),
)
private val LogoBlendLight = listOf(
    BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
    BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
)
private val LogoBlendDark = listOf(
    BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
    BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
)

/** 仿照 InstallerX Revived 的关于页：动态背景上的大标题随滚动淡出，标题栏随之浮现。 */
@Composable
fun AboutPage(onBack: () -> Unit, onOpenOpenSourceLicenses: () -> Unit) {
    val lazyListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val scrollProgress by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) return@derivedStateOf 1f
            val spacer = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == LogoSpacerKey }
            if (spacer == null || spacer.size <= 0) return@derivedStateOf 0f
            (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
        }
    }
    // 只在越过阈值时翻转，标题栏不必每帧重组。
    val isCollapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val topBarBackdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(topBarBackdrop.takeIf { isCollapsed }) { containerColor ->
                SmallTopAppBar(
                    title = stringResource(R.string.settings_about),
                    color = if (isCollapsed) containerColor else Color.Transparent,
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.navigate_back),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        AboutContent(
            padding = innerPadding,
            lazyListState = lazyListState,
            scrollBehavior = scrollBehavior,
            scrollProgress = { scrollProgress },
            onOpenOpenSourceLicenses = onOpenOpenSourceLicenses,
            modifier = Modifier.blurSource(topBarBackdrop),
        )
    }
}

@Composable
private fun AboutContent(
    padding: PaddingValues,
    lazyListState: LazyListState,
    scrollBehavior: ScrollBehavior,
    scrollProgress: () -> Float,
    onOpenOpenSourceLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    // 只录入动态背景，供 Logo、标题与卡片透出它的颜色。
    val backdrop = rememberBlurBackdrop()
    var headerHeight by remember { mutableStateOf(300.dp) }
    val listPadding = PaddingValues(
        start = padding.calculateStartPadding(layoutDirection),
        top = padding.calculateTopPadding(),
        end = padding.calculateEndPadding(layoutDirection),
    )

    BgEffectBackground(
        dynamicBackground = true,
        modifier = modifier.fillMaxSize(),
        bgModifier = Modifier.blurSource(backdrop),
        alpha = { 1f - scrollProgress() },
    ) {
        AboutHeader(
            backdrop = backdrop,
            scrollProgress = scrollProgress,
            modifier = Modifier
                .padding(
                    start = listPadding.calculateStartPadding(layoutDirection),
                    top = listPadding.calculateTopPadding() + HeaderTopPadding,
                    end = listPadding.calculateEndPadding(layoutDirection),
                )
                .onSizeChanged { size -> headerHeight = with(density) { size.height.toDp() } },
        )
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = listPadding,
            overscrollEffect = null,
        ) {
            // 透明占位，让列表从头部下方开始，并在滚过它时推进 scrollProgress。
            item(key = LogoSpacerKey) {
                Spacer(modifier = Modifier.fillMaxWidth().height(headerHeight + HeaderTopPadding + HeaderBottomGap))
            }
            item(key = "about") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = padding.calculateBottomPadding()),
                ) {
                    SmallTitle(text = stringResource(R.string.settings_about))
                    AboutCard(backdrop = backdrop) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_about_source_code),
                            summary = stringResource(R.string.settings_about_source_code_summary),
                            onClick = { uriHandler.openUri(SourceCodeUrl) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_about_license),
                            summary = stringResource(R.string.settings_about_license_summary),
                            onClick = { uriHandler.openUri(LicenseUrl) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_about_open_source_licenses),
                            summary = stringResource(R.string.settings_about_open_source_licenses_summary),
                            onClick = onOpenOpenSourceLicenses,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = padding,
        )
    }
}

/** 图标、应用名与版本号依次在滚动的不同阶段淡出并微缩。 */
@Composable
private fun AboutHeader(
    backdrop: LayerBackdrop?,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val logoBlend = if (LocalIsDarkTheme.current) LogoBlendDark else LogoBlendLight

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .fadeOutOnScroll(scrollProgress, start = 0.35f),
            contentAlignment = Alignment.Center,
        ) {
            if (backdrop != null) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .requiredSize(160.dp)
                        .textureBlur(
                            backdrop = backdrop,
                            shape = TextureShape,
                            blurRadius = LogoBlurRadius,
                            colors = BlurDefaults.blurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                        ),
                )
            } else {
                AppIcon(modifier = Modifier.size(80.dp))
            }
        }
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier
                .padding(top = 12.dp, bottom = 5.dp)
                .fadeOutOnScroll(scrollProgress, start = 0.20f)
                .then(
                    if (backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = TextureShape,
                            blurRadius = TitleBlurRadius,
                            colors = BlurDefaults.blurColors(blendColors = logoBlend),
                            contentBlendMode = BlendMode.DstIn,
                        )
                    } else {
                        Modifier
                    },
                ),
            fontWeight = FontWeight.Bold,
            style = MiuixTheme.textStyles.title1,
        )
        Text(
            text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            modifier = Modifier
                .fillMaxWidth()
                .fadeOutOnScroll(scrollProgress, start = 0.05f),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

private const val FadeOutSpan = 0.15f
private const val FadeOutShrink = 0.05f

/** 在 [start] 起的一小段滚动进度内淡出并缩小 5%。 */
private fun Modifier.fadeOutOnScroll(scrollProgress: () -> Float, start: Float): Modifier = graphicsLayer {
    val progress = ((scrollProgress() - start) / FadeOutSpan).coerceIn(0f, 1f)
    alpha = 1f - progress
    scaleX = 1f - progress * FadeOutShrink
    scaleY = 1f - progress * FadeOutShrink
}

/** 模糊关闭或不受支持时，Logo 退回系统裁切后的应用图标。 */
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(context) {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap()
    }
    Image(bitmap = icon, contentDescription = stringResource(R.string.app_name), modifier = modifier)
}

/** 有 [backdrop] 时透出动态背景，否则用普通卡片底色。 */
@Composable
private fun AboutCard(backdrop: LayerBackdrop?, content: @Composable () -> Unit) {
    val blendColors = if (LocalIsDarkTheme.current) OverlayExtraThinDark else PuredRegularLight
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = TextureShape,
                        blurRadius = CardBlurRadius,
                        colors = BlurDefaults.blurColors(blendColors = blendColors),
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.defaultColors(
            color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            contentColor = Color.Transparent,
        ),
    ) {
        content()
    }
}
