package com.illustar0.libzzu_ffi

import android.util.Base64
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.security.KeyStore

class LibzzuFfiPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "com.illustar0.libzzu_ffi")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getSystemRootCertificatesPem" -> {
                try {
                    result.success(loadSystemRootCertificatesPem())
                } catch (e: Exception) {
                    result.error("CERT_ERROR", e.message, null)
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun loadSystemRootCertificatesPem(): String {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        val pem = StringBuilder()
        val aliases = store.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (!alias.startsWith("system:")) continue
            val cert = store.getCertificate(alias) ?: continue
            val body = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
                .chunked(64)
                .joinToString("\n")
            if (pem.isNotEmpty()) pem.append('\n')
            pem.append("-----BEGIN CERTIFICATE-----\n")
            pem.append(body)
            pem.append("\n-----END CERTIFICATE-----")
        }
        return pem.toString()
    }
}
