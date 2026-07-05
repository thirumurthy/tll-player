package com.thirutricks.tllplayer.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/*
 * External-content FTS4 tables for fast name search across the big lists. Room keeps them in sync
 * with the base tables via generated triggers; `rowid` equals the base row's id, so search joins on
 * `<table>_fts.rowid = <table>.id`. Only the name is indexed to keep the FTS small at 340k+ items.
 */

@Entity(tableName = "channels_fts")
@Fts4(contentEntity = ChannelEntity::class)
data class ChannelFtsEntity(val name: String)

@Entity(tableName = "movies_fts")
@Fts4(contentEntity = MovieEntity::class)
data class MovieFtsEntity(val name: String)

@Entity(tableName = "series_fts")
@Fts4(contentEntity = SeriesEntity::class)
data class SeriesFtsEntity(val name: String)

@Entity(tableName = "episodes_fts")
@Fts4(contentEntity = EpisodeEntity::class)
data class EpisodeFtsEntity(val name: String)
