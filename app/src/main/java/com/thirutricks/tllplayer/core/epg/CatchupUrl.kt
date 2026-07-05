package com.thirutricks.tllplayer.core.epg

import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.core.parser.XtreamClient
import java.util.TimeZone

/**
 * Builds catch-up / archive playback URLs for a past programme (#13 / Catch-up TV).
 *
 * Two provider conventions:
 *  - **M3U** carries a `catchup-source` URL *template* with placeholders (`${start}`, `{utc}`,
 *    `{Y}-{m}-{d}`, …) that [fromTemplate] fills from the programme's start/end. A `catchup="append"`
 *    channel instead *appends* its template to the live URL — that join is the caller's job.
 *  - **Xtream** has no template; its timeshift URL is built from the source credentials (see
 *    `XtreamClient.timeshiftUrl`).
 *
 * Pure string work, no I/O — easy to unit-test and reuse from any layer.
 */
object CatchupUrl {

    /** Unix-second tokens (both `${name}` and `{name}`, case-insensitive) → their value. */
    private val START_TOKENS = setOf("start", "utc", "timestamp", "start-timestamp", "utcstart")
    private val END_TOKENS = setOf("end", "utcend", "end-timestamp", "stop")

    private val TOKEN = Regex("\\$?\\{([A-Za-z_][A-Za-z0-9_-]*)\\}")

    /**
     * Fill an M3U `catchup-source` [template] for the programme spanning [startMs]..[endMs].
     * Recognised placeholders (UTC): unix start/end/duration/offset, and date parts Y/m/d/H/M/S of the
     * start. Unknown placeholders are left untouched so unusual templates degrade gracefully.
     */
    fun fromTemplate(template: String, startMs: Long, endMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val startS = startMs / 1000
        val endS = endMs / 1000
        val durationS = ((endMs - startMs) / 1000).coerceAtLeast(0)
        val offsetS = ((nowMs - startMs) / 1000).coerceAtLeast(0)
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = startMs }
        fun two(n: Int) = n.toString().padStart(2, '0')
        val dateParts = mapOf(
            "y" to cal.get(java.util.Calendar.YEAR).toString(),
            "m" to two(cal.get(java.util.Calendar.MONTH) + 1),
            "d" to two(cal.get(java.util.Calendar.DAY_OF_MONTH)),
            "h" to two(cal.get(java.util.Calendar.HOUR_OF_DAY)),
            "min" to two(cal.get(java.util.Calendar.MINUTE)),
            "s" to two(cal.get(java.util.Calendar.SECOND)),
        )
        return TOKEN.replace(template) { match ->
            val raw = match.groupValues[1]
            val name = raw.lowercase()
            when {
                name in START_TOKENS -> startS.toString()
                name in END_TOKENS -> endS.toString()
                name == "duration" -> durationS.toString()
                name == "offset" -> offsetS.toString()
                // Date parts are single-letter and case-sensitive in the wild (M = month/minute), so map
                // by the ORIGINAL token: Y/m/d/H/M/S.
                raw == "Y" -> dateParts["y"]!!
                raw == "m" -> dateParts["m"]!!
                raw == "d" -> dateParts["d"]!!
                raw == "H" -> dateParts["h"]!!
                raw == "M" -> dateParts["min"]!!
                raw == "S" -> dateParts["s"]!!
                else -> match.value // unknown placeholder: leave as-is
            }
        }
    }

    /**
     * Build the catch-up URL for a [programme] on [channel] given its resolved [source] and the [tz] the
     * provider expects timeshift timestamps in. Shared by the Guide and Live TV catch-up entry points.
     */
    fun forSource(
        channel: ChannelEntity,
        programme: EpgProgrammeEntity,
        source: SourceEntity,
        tz: TimeZone,
        xtream: XtreamClient,
    ): String? {
        if (!channel.catchup) return null
        return when (source.type) {
            SourceType.XTREAM -> channel.remoteId?.let { streamId ->
                val durationMin = (((programme.stopMs - programme.startMs) / 60_000L).toInt()).coerceAtLeast(1)
                xtream.timeshiftUrl(source, streamId, programme.startMs, durationMin, tz)
            }
            SourceType.M3U, SourceType.TLL -> forM3u(channel.streamUrl, null, channel.catchupSource, programme.startMs, programme.stopMs)
            else -> null
        }
    }

    private val TIMESHIFT_PATH = Regex(
        "^(.+?)/timeshift/([^/]+)/([^/]+)/([^/]+)/([^/]+)/([^/.]+)\\.(?:ts|m3u8)$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Convert a path-style Xtream catch-up URL
     * (`…/timeshift/user/pass/duration/start/streamId.ts`) into the PHP query form
     * (`…/streaming/timeshift.php?username=…&password=…&stream=…&start=…&duration=…`) that some panels
     * require instead. Returns null if [url] isn't a recognised timeshift path. The player tries this
     * automatically when the path form is rejected (issue: some panels reply with an HTML error page).
     */
    fun timeshiftPhpAlternate(url: String?): String? {
        val g = url?.let { TIMESHIFT_PATH.find(it) }?.groupValues ?: return null
        return "${g[1]}/streaming/timeshift.php?username=${g[2]}&password=${g[3]}&stream=${g[6]}&start=${g[5]}&duration=${g[4]}"
    }

    /**
     * Resolve an M3U channel's catch-up URL: substitute its [catchupSource] template, and for the
     * `append` type join it onto the [liveUrl]. Returns null if there's no usable template.
     */
    fun forM3u(liveUrl: String, catchupType: String?, catchupSource: String?, startMs: Long, endMs: Long): String? {
        val template = catchupSource?.takeIf { it.isNotBlank() }
        return when {
            template != null && catchupType.equals("append", ignoreCase = true) ->
                liveUrl + fromTemplate(template, startMs, endMs)
            template != null -> fromTemplate(template, startMs, endMs)
            else -> null // no template → nothing we can build (a bare catchup="default" needs server support we don't model)
        }
    }
}
