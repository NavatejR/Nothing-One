package com.nothing.one.feature.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** One event read from the device calendar provider. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendarDisplayName: String,
    /** True when the event lives in the app-owned local calendar. */
    val fromNothingOne: Boolean,
)

/**
 * The only calendar surface Nothing One touches: the device's own
 * [CalendarContract] provider. Events the user's Google account syncs to the
 * phone appear through the same provider — which is how the app stays
 * 100% local while still "syncing with Google": the OS does all syncing,
 * the app holds no credentials and makes no network calls.
 *
 * Writes land in a dedicated local calendar ("Nothing One") that this class
 * creates on demand. Local-calendar events are visible in Google Calendar and
 * any other calendar app, and sync upstream exactly like events typed there.
 */
@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Events overlapping [startMs, endMs), sorted by start time. Instances
     * (not raw events) are read so recurring events expand naturally.
     */
    suspend fun eventsBetween(startMs: Long, endMs: Long): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            if (!hasReadPermission()) return@withContext emptyList()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(startMs.toString())
                .appendPath(endMs.toString())
                .build()
            val projection = arrayOf(
                CalendarContract.Instances._ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            )
            val events = mutableListOf<CalendarEvent>()
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val calNameCol =
                    cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    events += CalendarEvent(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol).ifBlank { "(no title)" },
                        beginMs = cursor.getLong(beginCol),
                        endMs = cursor.getLong(endCol),
                        allDay = cursor.getInt(allDayCol) != 0,
                        calendarDisplayName = cursor.getString(calNameCol).orEmpty(),
                        fromNothingOne = cursor.getString(calNameCol) == LOCAL_CALENDAR_NAME,
                    )
                }
            }
            events
        }

    /**
     * Adds a timed event into the app-owned local calendar, creating that
     * calendar on first write. Returns the new event id, or null on failure.
     */
    suspend fun addLocalEvent(
        title: String,
        startMs: Long,
        durationMs: Long,
    ): Long? = withContext(Dispatchers.IO) {
        if (!hasWritePermission() || !hasReadPermission()) return@withContext null
        val calId = ensureLocalCalendar() ?: return@withContext null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, startMs + durationMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        try {
            val uri: Uri? = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Finds (or creates) the "Nothing One" local calendar. Local calendars
     * carry ACCOUNT_TYPE_LOCAL and sync nowhere on their own — the account
     * the user adds in Google Calendar is what syncs them.
     */
    private fun ensureLocalCalendar(): Long? {
        val existing = queryLocalCalendarId()
        if (existing != null) return existing

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.NAME, LOCAL_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, LOCAL_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFFFF0044.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        val builder = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        return try {
            context.contentResolver.insert(builder.build(), values)
                ?.lastPathSegment?.toLongOrNull()
        } catch (_: SecurityException) {
            null
        }
    }

    private fun queryLocalCalendarId(): Long? {
        if (!hasReadPermission()) return null
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.NAME} = ?",
                arrayOf(LOCAL_CALENDAR_NAME),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    companion object {
        const val LOCAL_CALENDAR_NAME = "Nothing One"
        const val LOCAL_ACCOUNT = "nothing.one.local"
    }
}
