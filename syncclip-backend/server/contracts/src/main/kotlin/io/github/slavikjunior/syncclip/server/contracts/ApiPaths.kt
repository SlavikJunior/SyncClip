package io.github.slavikjunior.syncclip.server.contracts

/**
 * Единый список путей API. Значения — из docs/API_SPEC.yaml и
 * docs/backend/CONTRACT_GAPS.md (CG-01, CG-02, CG-04, CG-08, CG-21).
 */
object ApiPaths {
    const val V1 = "/v1"

    const val AUTH_REGISTER = "$V1/auth/register"
    const val AUTH_LOGIN = "$V1/auth/login"
    const val AUTH_REFRESH = "$V1/auth/refresh"
    const val AUTH_CANARY = "$V1/auth/canary"
    const val AUTH_CANARY_ATTEMPT = "$V1/auth/canary/attempt"
    const val AUTH_GUEST_INVITE = "$V1/auth/guest/invite"
    const val AUTH_GUEST_EXCHANGE = "$V1/auth/guest/exchange"
    const val AUTH_LOGOUT = "$V1/auth/logout"
    const val AUTH_FIREBASE = "$V1/auth/firebase"

    const val SYNC_PENDING = "$V1/sync/pending"
    const val SYNC_WS = "$V1/sync"

    const val DEVICES = "$V1/devices"
    const val DEVICE_BY_ID = "$V1/devices/{deviceId}"

    const val FCM_REGISTER = "$V1/fcm/register"
    const val NOTIFICATIONS_UNREAD = "$V1/notifications/unread"

    const val HEALTH_LIVE = "/health/live"
    const val HEALTH_READY = "/health/ready"

    const val DEVICE_ID_PARAM = "deviceId"
    const val LAST_EVENT_ID_PARAM = "lastEventId"
    const val SINCE_PARAM = "since"
    const val LIMIT_PARAM = "limit"
    const val TOKEN_QUERY_PARAM = "token"
}
