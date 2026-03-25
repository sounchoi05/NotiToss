package com.ckchoi.notitoss.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormats {
    fun date(timeMillis: Long): String = formatter("yyyy-MM-dd").format(Date(timeMillis))

    fun time(timeMillis: Long): String = formatter("HH:mm:ss").format(Date(timeMillis))

    fun dateTime(timeMillis: Long): String = formatter("yyyy-MM-dd HH:mm:ss").format(Date(timeMillis))

    private fun formatter(pattern: String): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.getDefault())
    }
}
