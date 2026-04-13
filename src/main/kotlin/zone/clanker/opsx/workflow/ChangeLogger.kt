package zone.clanker.opsx.workflow

import zone.clanker.opsx.model.TaskStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/** Synchronized append-only logger for change execution journals. */
object ChangeLogger {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun append(
        changeDir: File,
        taskId: String,
        taskName: String,
        status: TaskStatus,
        message: String,
    ) {
        val lock = locks.computeIfAbsent(changeDir.absolutePath) { ReentrantLock() }
        lock.lock()
        val logFile = File(changeDir, "log.md")
        runCatching {
            if (!logFile.exists()) {
                logFile.writeText("# Log\n\n")
            }
            val symbol = status.symbol
            val line = "- [$symbol] $taskId | $taskName — $message\n"
            logFile.appendText(line)
        }.also {
            lock.unlock()
        }
    }
}
