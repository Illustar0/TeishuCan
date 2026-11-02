package com.illustar0.chibacan.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun fromLanguageTags_emptyTags_followsSystem() {
        assertEquals(AppLanguage.System, AppLanguage.fromLanguageTags(""))
    }

    @Test
    fun fromLanguageTags_exactTag_matchesLanguage() {
        assertEquals(AppLanguage.Japanese, AppLanguage.fromLanguageTags("ja"))
        assertEquals(AppLanguage.Chinese, AppLanguage.fromLanguageTags("zh"))
    }

    @Test
    fun fromLanguageTags_regionAndScriptSubtags_areIgnored() {
        assertEquals(AppLanguage.Japanese, AppLanguage.fromLanguageTags("ja-JP"))
        assertEquals(AppLanguage.Chinese, AppLanguage.fromLanguageTags("zh-Hans-CN"))
    }

    @Test
    fun fromLanguageTags_multipleTags_usesTheFirst() {
        assertEquals(AppLanguage.Chinese, AppLanguage.fromLanguageTags("zh-CN,ja"))
    }

    @Test
    fun fromLanguageTags_unsupportedLanguage_followsSystem() {
        assertEquals(AppLanguage.System, AppLanguage.fromLanguageTags("fr-FR"))
    }
}
