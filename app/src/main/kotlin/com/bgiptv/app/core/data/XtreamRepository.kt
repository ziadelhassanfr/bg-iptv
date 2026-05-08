package com.bgiptv.app.core.data

import com.bgiptv.app.core.data.dao.ChannelDao
import com.bgiptv.app.core.data.dao.ProgramDao
import com.bgiptv.app.core.data.entity.ChannelEntity
import com.bgiptv.app.core.data.entity.ProgramEntity
import com.bgiptv.app.core.network.XtreamApiService
import com.bgiptv.app.core.network.XtreamEpgInfo
import com.bgiptv.app.core.security.CredentialsManager
import com.bgiptv.app.core.security.XtreamCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

data class ImportProgress(
    val channels: Int = 0,
    val epg: Int = 0,
    val vod: Int = 0,
    val dedupRemoved: Int = 0,
    val isComplete: Boolean = false,
)

@Singleton
class XtreamRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val programDao: ProgramDao,
    private val normalizer: ChannelNormalizer,
    private val credentialsManager: CredentialsManager,
    private val okHttpClient: OkHttpClient,
    private val moshi: com.squareup.moshi.Moshi,
) {
    private fun apiFor(serverUrl: String): XtreamApiService =
        Retrofit.Builder()
            .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(XtreamApiService::class.java)

    suspend fun importAll(
        credentials: XtreamCredentials,
        onProgress: (ImportProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val api = apiFor(credentials.serverUrl)
        var progress = ImportProgress()

        coroutineScope {
            // Import categories + streams in parallel
            val categoriesDeferred = async {
                runCatching { api.getLiveCategories(credentials.username, credentials.password) }
                    .getOrDefault(emptyList())
            }
            val streamsDeferred = async {
                runCatching { api.getLiveStreams(credentials.username, credentials.password) }
                    .getOrDefault(emptyList())
            }

            val categories = categoriesDeferred.await()
            val streams = streamsDeferred.await()

            val categoryMap = categories.associateBy { it.id }

            // Normalize + deduplicate
            val now = System.currentTimeMillis()
            val channelEntities = mutableListOf<ChannelEntity>()
            val seenCanonicals = mutableMapOf<String, Int>() // canonical → first streamId
            var dedupCount = 0

            streams.forEach { stream ->
                val rawGroup = categoryMap[stream.categoryId]?.name ?: ""
                val canonical = normalizer.canonicalName(stream.name)
                val quality = normalizer.extractQuality(stream.name)
                val countryCode = normalizer.extractCountryCode(stream.name)
                    ?: normalizer.extractCountryCode(rawGroup)
                val groupTag = normalizer.groupTag(rawGroup, stream.name)

                // Skip obvious duplicates (same canonical, lower quality)
                val existingId = seenCanonicals[canonical]
                if (existingId != null) {
                    // Keep the highest quality variant as primary
                    val existingQuality = channelEntities.find { it.streamId == existingId }?.quality
                    if (qualityRank(quality) > qualityRank(existingQuality ?: "SD")) {
                        // Replace with higher quality
                        channelEntities.removeIf { it.streamId == existingId }
                        seenCanonicals[canonical] = stream.streamId
                    } else {
                        dedupCount++
                        return@forEach
                    }
                } else {
                    seenCanonicals[canonical] = stream.streamId
                }

                channelEntities += ChannelEntity(
                    streamId = stream.streamId,
                    name = stream.name,
                    canonicalName = canonical.replaceFirstChar { it.uppercase() },
                    groupId = stream.categoryId ?: "",
                    groupTag = groupTag,
                    countryCode = countryCode,
                    iconUrl = stream.streamIcon,
                    epgChannelId = stream.epgChannelId,
                    quality = quality,
                    hasCatchup = (stream.tvArchive ?: 0) > 0,
                    catchupDays = stream.tvArchiveDuration ?: 0,
                    lcn = stream.num,
                    isFavorite = false,
                    isHidden = false,
                    userCodecOverride = null,
                    userQualityOverride = null,
                    addedAt = now,
                    lastWatchedAt = null,
                )
            }

            channelDao.deleteAll()
            channelDao.insertAll(channelEntities)

            progress = progress.copy(channels = channelEntities.size, dedupRemoved = dedupCount)
            onProgress(progress)

            // Import EPG for sport channels first (priority)
            val sportChannels = channelEntities.filter {
                it.groupTag in listOf("FOOT", "F1", "NBA", "TENNIS", "SPORT")
            }
            importEpgForChannels(api, credentials, sportChannels, batchSize = 10) { count ->
                progress = progress.copy(epg = count)
                onProgress(progress)
            }

            // Then EPG for all remaining channels
            val remaining = channelEntities.filter { it.groupTag !in listOf("FOOT", "F1", "NBA", "TENNIS", "SPORT") }
            importEpgForChannels(api, credentials, remaining, batchSize = 20) { count ->
                progress = progress.copy(epg = progress.epg + count)
                onProgress(progress)
            }
        }

        onProgress(progress.copy(isComplete = true))
    }

    private suspend fun importEpgForChannels(
        api: XtreamApiService,
        credentials: XtreamCredentials,
        channels: List<ChannelEntity>,
        batchSize: Int,
        onBatch: (Int) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        var totalImported = 0

        channels.chunked(batchSize).forEach { batch ->
            coroutineScope {
                batch.map { channel ->
                    async {
                        runCatching {
                            api.getShortEpg(
                                username = credentials.username,
                                password = credentials.password,
                                streamId = channel.streamId,
                                limit = 8,
                            ).listings.map { it.toProgramEntity(channel.streamId) }
                        }.getOrDefault(emptyList())
                    }
                }.map { it.await() }
                    .flatten()
                    .also { programs ->
                        if (programs.isNotEmpty()) {
                            programDao.insertAll(programs)
                            totalImported += programs.size
                        }
                    }
            }
            onBatch(totalImported)
        }
    }

    fun buildStreamUrl(credentials: XtreamCredentials, streamId: Int): String =
        "${credentials.serverUrl}/live/${credentials.username}/${credentials.password}/$streamId"

    fun buildCatchupUrl(
        credentials: XtreamCredentials,
        streamId: Int,
        startTimestamp: Long,
        durationMinutes: Int,
    ): String = "${credentials.serverUrl}/timeshift/${credentials.username}/${credentials.password}" +
        "/$durationMinutes/${java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).format(java.util.Date(startTimestamp))}/$streamId"

    private fun qualityRank(quality: String): Int = when (quality.uppercase()) {
        "8K" -> 5
        "4K", "UHD" -> 4
        "FHD" -> 3
        "HD" -> 2
        "SD" -> 1
        else -> 2
    }
}

private fun XtreamEpgInfo.toProgramEntity(streamId: Int) = ProgramEntity(
    id = id,
    channelId = streamId,
    epgChannelId = channelId,
    title = title,
    description = description,
    startTimestamp = startTimestamp * 1000L,
    stopTimestamp = stopTimestamp * 1000L,
    categoryHint = inferCategory(title),
)

private fun inferCategory(title: String): String? {
    val upper = title.uppercase()
    return when {
        upper.containsAny("FOOT", "SOCCER", "MATCH", "LIGUE", "CHAMPIONS", "PREMIER LEAGUE") -> "sport"
        upper.containsAny("FILM", "CINÉMA", "MOVIE") -> "movie"
        upper.containsAny("SÉRIE", "EPISODE", "SAISON") -> "series"
        upper.containsAny("JT", "JOURNAL", "INFO", "NEWS") -> "news"
        else -> null
    }
}

private fun String.containsAny(vararg terms: String): Boolean =
    terms.any { this.contains(it) }
