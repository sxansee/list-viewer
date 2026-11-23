package app.fileviewer

import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

private val URI_IN_TEXT = Regex("""\b(content|file)://\S+""")
private val PATH_IN_TEXT = Regex("""/(?:storage|sdcard|mnt)/[^:\r\n]*?(?=\s\(|:|\r|\n|$)""")

/**
 * Hides what an error message may say about a person's files: the addresses ("content://…") and the paths of files in
 * the shared storage. The rest of the message is kept, since it is what says what went wrong.
 */
fun hidePaths(text: String): String =
    text.replace(URI_IN_TEXT) { it.groupValues[1] + "://…" }.replace(PATH_IN_TEXT, "/…")

/** Keeps the start and the end of a long list of lines: a stack trace says what failed at the top and why at the bottom. */
fun shorten(lines: List<String>, head: Int = 45, tail: Int = 25): List<String> {
    if (lines.size <= head + tail + 1) return lines
    val skipped = lines.size - head - tail
    return lines.take(head) + "… $skipped lines left out …" + lines.takeLast(tail)
}

/**
 * The text of a crash report: [details] (version, system, device), the time and the thread, then the stack trace
 * [trace] with the paths of files hidden, cut to a length that is easy to read and to send.
 */
fun crashReport(details: String, time: String, thread: String, trace: String): String {
    val top = listOf(details.trim(), "Crash: $time", "Thread: $thread").filter { it.isNotEmpty() }
    val lines = hidePaths(trace).trim().lines().map { if (it.length > MAX_LINE) it.take(MAX_LINE) + "…" else it }
    return top.joinToString("\n") + "\n\n" + shorten(lines).joinToString("\n") + "\n"
}

private const val MAX_LINE = 400

/** The stack trace of [error] as text, with the causes. */
fun traceOf(error: Throwable): String {
    val w = StringWriter()
    PrintWriter(w).use { error.printStackTrace(it) }
    return w.toString()
}

/**
 * The report of the last crash: one file in [dir], overwritten by the next crash, and a second file that remembers which
 * report the person has already been told about. Nothing here leaves the device; the person copies the report out
 * themselves.
 */
class CrashStore(private val dir: File) {
    private val report = File(dir, "last_crash.txt")
    private val seen = File(dir, "last_crash.seen")

    fun save(text: String) {
        dir.mkdirs()
        report.writeText(text)
    }

    /** The last report, or null when there has been no crash. */
    fun last(): String? {
        if (!report.isFile) return null
        return try {
            report.readText().takeIf { it.isNotBlank() }
        } catch (e: IOException) {
            null
        }
    }

    /** The last report if the person has not been shown it yet. */
    fun unseen(): String? {
        val text = last() ?: return null
        return if (readSeen() == text.hashCode()) null else text
    }

    fun markSeen() {
        val text = last() ?: return
        try {
            seen.writeText(text.hashCode().toString())
        } catch (e: IOException) {
            // The report is then offered once more at the next start, which does no harm.
        }
    }

    private fun readSeen(): Int? = try {
        seen.readText().trim().toInt()
    } catch (e: Exception) {
        null
    }
}
