package com.illustar0.chibacan.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** 应用可用的语言；[System] 表示跟随设备设置。 */
enum class AppLanguage(val languageTag: String) {
    System(""),
    Japanese("ja"),
    Chinese("zh");

    companion object {
        /** 把逗号分隔的 [languageTags] 映射到语言，忽略地区和文字子标签。 */
        fun fromLanguageTags(languageTags: String): AppLanguage {
            val language = languageTags.substringBefore(',').substringBefore('-')
            return entries.find { it.languageTag == language } ?: System
        }
    }
}

fun currentAppLanguage(): AppLanguage =
    AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

/** 保存 [language] 并以该语言重建运行中的 Activity，须在主线程调用。 */
fun applyAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
}
