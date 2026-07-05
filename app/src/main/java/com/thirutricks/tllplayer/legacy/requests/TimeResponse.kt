package com.thirutricks.tllplayer.legacy.requests


data class TimeResponse(
    val data: Time
) {
    data class Time(
        val t: String
    )
}