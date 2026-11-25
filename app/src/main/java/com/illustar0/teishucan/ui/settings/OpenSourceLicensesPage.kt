package com.illustar0.teishucan.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.ui.component.rememberShownContent
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val LicenseChipAlpha = 0.2f

@Composable
fun OpenSourceLicensesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val libraries by produceState<List<Library>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            Libs.Builder().withJson(context, R.raw.aboutlibraries).build().libraries
        }
    }
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.settings_about_open_source_licenses),
                    color = containerColor,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .blurSource(backdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            items(libraries, key = { it.uniqueId }) { library ->
                LibraryCard(
                    library = library,
                    onClick = { selectedLibrary = library },
                )
            }
        }
        val dialog = rememberShownContent(selectedLibrary)
        dialog.value?.let { library ->
            LicenseDialog(
                library = library,
                show = selectedLibrary != null,
                onDismissRequest = { selectedLibrary = null },
                onDismissFinished = dialog.release,
            )
        }
    }
}

@Composable
private fun LibraryCard(library: Library, onClick: () -> Unit) {
    Card(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = library.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 5.dp),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MiuixTheme.textStyles.title2,
                )
                library.artifactVersion?.let { version ->
                    Text(
                        text = version,
                        color = MiuixTheme.colorScheme.secondary,
                        style = MiuixTheme.textStyles.headline1,
                    )
                }
            }
            if (library.author.isNotEmpty()) {
                Text(
                    text = library.author,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body1,
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                library.licenses.forEach { license -> LicenseChip(name = license.name) }
            }
        }
    }
}

@Composable
private fun LicenseChip(name: String) {
    Card(
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = LicenseChipAlpha),
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = name,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}

@Composable
private fun LicenseDialog(
    library: Library,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    OverlayDialog(
        show = show,
        title = library.name,
        summary = stringResource(
            R.string.settings_about_open_source_licenses_license,
            library.licenses.joinToString { it.name },
        ),
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        val dismiss = LocalDismissState.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                items(library.licenses.toList(), key = { it.hash }) { license ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = license.name,
                                modifier = license.url?.let { url ->
                                    Modifier.clickable { uriHandler.openUri(url) }
                                } ?: Modifier,
                                color = MiuixTheme.colorScheme.primary,
                                style = MiuixTheme.textStyles.title4,
                            )
                            Text(
                                text = license.licenseContent
                                    ?: stringResource(R.string.settings_about_open_source_licenses_no_text),
                                modifier = Modifier.padding(top = 8.dp),
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                library.website?.takeIf { it.isNotBlank() }?.let { website ->
                    TextButton(
                        text = stringResource(R.string.settings_about_open_source_licenses_website),
                        onClick = { uriHandler.openUri(website) },
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = stringResource(R.string.settings_about_open_source_licenses_close),
                    onClick = { dismiss?.invoke() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** 优先列出开发者，没有时退回组织名，与 AboutLibraries 自带界面一致。 */
private val Library.author: String
    get() = developers.mapNotNull { it.name }.joinToString().ifEmpty { organization?.name.orEmpty() }
