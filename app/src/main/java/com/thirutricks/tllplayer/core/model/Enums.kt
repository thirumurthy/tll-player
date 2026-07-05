package com.thirutricks.tllplayer.core.model

/** What kind of media an item is. Used to scope favorites/history/progress generically. */
enum class MediaType { LIVE, MOVIE, SERIES, EPISODE }

/** How a source delivers its content. */
enum class SourceType { M3U, XTREAM, LOCAL_BACKUP, TLL }

/** Lifecycle of a download (movies & series only). */
enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }
