package com.thirutricks.tllplayer.requests


data class TimeResponse(
    val data: Time
) {
    data class Time(
        val t: String
    )
}