package com.bgiptv.app.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class XtreamUserInfo(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
    @Json(name = "status") val status: String,
    @Json(name = "exp_date") val expDate: String?,
    @Json(name = "max_connections") val maxConnections: String,
)

@JsonClass(generateAdapter = true)
data class XtreamServerInfo(
    @Json(name = "url") val url: String,
    @Json(name = "port") val port: String,
    @Json(name = "https_port") val httpsPort: String?,
    @Json(name = "server_protocol") val protocol: String,
    @Json(name = "timezone") val timezone: String,
)

@JsonClass(generateAdapter = true)
data class XtreamAuthResponse(
    @Json(name = "user_info") val userInfo: XtreamUserInfo,
    @Json(name = "server_info") val serverInfo: XtreamServerInfo,
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val id: String,
    @Json(name = "category_name") val name: String,
    @Json(name = "parent_id") val parentId: Int,
)

@JsonClass(generateAdapter = true)
data class XtreamStream(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_icon") val iconUrl: String?,
    @Json(name = "epg_channel_id") val epgChannelId: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "num") val num: Int?,
    @Json(name = "stream_type") val streamType: String?,
    @Json(name = "added") val added: String?,
    @Json(name = "custom_sid") val customSid: String?,
    @Json(name = "tv_archive") val tvArchive: Int?,
    @Json(name = "tv_archive_duration") val tvArchiveDuration: Int?,
)

@JsonClass(generateAdapter = true)
data class XtreamEpgInfo(
    @Json(name = "id") val id: String,
    @Json(name = "epg_id") val epgId: String,
    @Json(name = "title") val title: String,
    @Json(name = "lang") val lang: String,
    @Json(name = "start") val start: String,
    @Json(name = "end") val end: String,
    @Json(name = "description") val description: String?,
    @Json(name = "channel_id") val channelId: String,
    @Json(name = "start_timestamp") val startTimestamp: Long,
    @Json(name = "stop_timestamp") val stopTimestamp: Long,
)

@JsonClass(generateAdapter = true)
data class XtreamEpgResponse(
    @Json(name = "epg_listings") val listings: List<XtreamEpgInfo>,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfo(
    @Json(name = "num") val num: Int?,
    @Json(name = "name") val name: String,
    @Json(name = "stream_type") val streamType: String,
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val iconUrl: String?,
    @Json(name = "rating") val rating: String?,
    @Json(name = "rating_5based") val rating5: Double?,
    @Json(name = "added") val added: String?,
    @Json(name = "category_id") val categoryId: String?,
    @Json(name = "container_extension") val extension: String?,
)
