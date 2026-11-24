package com.illustar0.teishucan.domain

import kotlinx.serialization.Serializable

@Serializable
data class StudentProfile(val name: String, val faculty: String, val identityType: String)

sealed interface AccountState {
    /** 正在读取本机保存的会话，此时还不知道是否已登录。 */
    data object Restoring : AccountState

    data object SignedOut : AccountState

    data class SignedIn(val profile: StudentProfile) : AccountState
}

sealed interface LoginResult {
    data object SignedIn : LoginResult

    /** 还需要短信验证才能完成登录；[securePhone] 是服务端返回的脱敏手机号，未提供时为 null。 */
    data class MfaRequired(val securePhone: String? = null) : LoginResult
}
