package com.thirutricks.tllplayer.ui.format

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Date

@Composable
fun rememberSystemTimeFormatter(): (Long) -> String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val formatter = remember(context, configuration) {
        DateFormat.getTimeFormat(context)
    }
    return remember(formatter) {
        val date = Date(0L)
        val format: (Long) -> String = { ms ->
            date.time = ms
            formatter.format(date)
        }
        format
    }
}

fun formatSystemTime(context: Context, ms: Long): String {
    return DateFormat.getTimeFormat(context).format(Date(ms))
}
