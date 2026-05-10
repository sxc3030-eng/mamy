package com.mamy.android.util

import android.content.Context
import android.os.Build
import com.mamy.android.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Captures uncaught exceptions early in the app lifecycle so we can debug
 * crashes that happen during `Application.onCreate()` (Hilt init, eager
 * singletons, etc.) — i.e. before any UI shows and before `adb logcat` is
 * reasonable to ask of an alpha tester.
 *
 * Strategy:
 *  1. Always persist the crash report to internal storage:
 *     `<filesDir>/crash-<ts>.txt` so the next launch (or a Logcat Reader app
 *     reading shared dirs) can recover it.
 *  2. Best-effort HTTP POST to `<OLLAMA_BASE_URL>/api/crash` so the developer
 *     sees the trace within seconds. Capped at 4s so we don't keep the user
 *     staring at a frozen "MamY keeps stopping" dialog.
 *  3. Always delegate to the default handler at the end — the OS still gets
 *     to terminate the process the way it normally would.
 *
 * Installed from [com.mamy.android.MamYApplication.attachBaseContext] so it
 * is the very first thing that runs in the application lifecycle.
 */
class CrashReporter(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val endpointBaseUrl: String = BuildConfig.OLLAMA_BASE_URL,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val report = buildReport(t, e)
            persist(report)
            tryPost(report)
        } catch (_: Throwable) {
            // never let the reporter itself crash inside an uncaught handler
        } finally {
            defaultHandler?.uncaughtException(t, e)
        }
    }

    private fun buildReport(t: Thread, e: Throwable): String {
        val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return buildString {
            append("MamY crash report\n")
            append("ts: ").append(ts).append("\n")
            append("app: ").append(BuildConfig.APPLICATION_ID)
                .append(" v").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(") ")
                .append(BuildConfig.BUILD_TYPE).append("\n")
            append("device: ").append(Build.MANUFACTURER).append(" ")
                .append(Build.MODEL).append(" / Android ")
                .append(Build.VERSION.RELEASE).append(" (sdk ")
                .append(Build.VERSION.SDK_INT).append(")\n")
            append("thread: ").append(t.name).append("\n")
            append("exception: ").append(e.javaClass.name)
                .append(": ").append(e.message ?: "<no message>").append("\n")
            append("stacktrace:\n").append(sw.toString())
            var cause = e.cause
            var depth = 0
            while (cause != null && depth < 5) {
                val csw = StringWriter()
                cause.printStackTrace(PrintWriter(csw))
                append("\nCaused by: ").append(cause.javaClass.name)
                    .append(": ").append(cause.message ?: "<no message>").append("\n")
                append(csw.toString())
                cause = cause.cause
                depth++
            }
        }
    }

    private fun persist(report: String) {
        try {
            val dir = File(context.filesDir, "crash").apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            File(dir, "crash-$ts.txt").writeText(report)
        } catch (_: Throwable) {
            // disk full / permission — ignore, network attempt below may still succeed
        }
    }

    private fun tryPost(report: String) {
        val url = endpointBaseUrl.trimEnd('/') + "/api/crash"
        // Spawn a short-lived thread so we never block the OS killing the process
        val thread = Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.doOutput = true
                conn.outputStream.use { it.write(report.toByteArray(Charsets.UTF_8)) }
                // drain response so the connection closes cleanly
                conn.inputStream.use { it.readBytes() }
            } catch (_: Throwable) {
                // network unreachable / DNS / proxy down — local file copy still has it
            }
        }.apply { isDaemon = true; start() }
        thread.join(4000)
    }
}
