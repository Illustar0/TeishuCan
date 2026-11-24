package com.illustar0.teishucan.data

import com.illustar0.teishucan.concurrency.RefreshTracker
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.zzu.cas.SsoService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.time.Duration
import kotlin.time.Instant

/** 校园邮箱未读数与网页版入口，独立维护自身的刷新状态和快照核对时间。 */
@Singleton
class MailRepository @Inject constructor(
    private val session: ZzuSession,
) {
    private val unread = session.createSnapshotCache(
        "mail.json",
        null,
        Int.serializer().nullable,
    )
    private val refreshTracker = RefreshTracker()

    val unreadCount: StateFlow<Int?> = unread.value
    val isRefreshing: StateFlow<Boolean> = refreshTracker.isRefreshing

    suspend fun isStaleAt(now: Instant, maxAge: Duration): Boolean = unread.isStaleAt(now, maxAge)

    suspend fun refresh() = refreshTracker.track {
        val refreshVersion = unread.nextRefreshVersion()
        session.request(CampusFeature.Mail) {
            val count = mail().unreadCount()
            unread.update(this, refreshVersion) { count }
        }
    }

    suspend fun loginUrl(): String =
        session.request(CampusFeature.Mail) { cas.serviceSsoUrl(SsoService.EMAIL) }
}
