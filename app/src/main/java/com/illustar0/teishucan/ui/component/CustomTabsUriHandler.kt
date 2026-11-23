package com.illustar0.teishucan.ui.component

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

internal class CustomTabsUriHandler(
    private val activity: Activity,
    private val defaultUriHandler: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        val url = uri.toUri().normalizeScheme()
        if (url.scheme != "https" && url.scheme != "http") {
            defaultUriHandler.openUri(uri)
            return
        }

        try {
            CustomTabsIntent.Builder().build().launchUrl(activity, url)
        } catch (_: ActivityNotFoundException) {
            throw IllegalArgumentException("No activity can open this web link")
        }
    }
}
