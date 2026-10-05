package com.das.mobile.core.util

import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * Backend date formats. All dates are zone-less Java LocalDate / LocalDateTime:
 *  - requests: "yyyy-MM-dd" and "yyyy-MM-dd'T'HH:mm:ss" (@JsonFormat on the input DTOs)
 *  - responses: ISO local date / date-time (Jackson defaults)
 */
object ApiDates {
    private val dateTimeOut: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun format(date: LocalDate): String = date.toString()

    fun format(dateTime: LocalDateTime): String = dateTime.truncatedTo(ChronoUnit.SECONDS).format(dateTimeOut)

    fun parseDate(value: String?): LocalDate? =
        value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    fun parseDateTime(value: String?): LocalDateTime? =
        value?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() ?: parseDate(it)?.atStartOfDay() }
}

object UiFormats {
    private val date: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val time: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun date(value: LocalDate?): String = value?.format(date) ?: "—"
    fun dateTime(value: LocalDateTime?): String = value?.format(dateTime) ?: "—"
    fun time(value: LocalDateTime?): String = value?.format(time) ?: "—"
    fun money(value: BigDecimal?): String = value?.let { DecimalFormat("#,##0.00").format(it) } ?: "—"
}
