package zone.clanker.gradle.conventions

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles

abstract class ShellcheckTask : Exec() {
    @get:InputFiles
    abstract val scripts: ConfigurableFileCollection

    @get:Input
    abstract val severity: Property<String>

    @get:Input
    abstract val ciMode: Property<Boolean>

    init {
        group = "verification"
        description = "Run shellcheck over opsx shell scripts."
        isIgnoreExitValue = true
    }

    override fun exec() {
        val files = scripts.files.filter { it.isFile }.sortedBy { it.absolutePath }
        if (files.isEmpty()) {
            logger.lifecycle("shellcheck: no shell scripts found; nothing to check.")
            return
        }

        if (!shellcheckOnPath()) {
            val msg = "shellcheck: 'shellcheck' not found on PATH."
            if (ciMode.get()) {
                throw GradleException("$msg Install shellcheck to run this check in CI.")
            }
            logger.warn("$msg Skipping (install via 'brew install shellcheck' to enable).")
            return
        }

        val sev = severity.get()
        logger.lifecycle("shellcheck: checking ${files.size} script(s) at severity '$sev'.")

        commandLine(
            buildList {
                add("shellcheck")
                add("--severity=$sev")
                add("--color=never")
                addAll(files.map { it.absolutePath })
            },
        )

        super.exec()

        val exit = executionResult.get().exitValue
        if (exit != 0) {
            throw GradleException(
                "shellcheck reported diagnostics at or above severity '$sev' (exit=$exit).",
            )
        }
        logger.lifecycle("shellcheck: clean.")
    }

    private fun shellcheckOnPath(): Boolean =
        runCatching {
            ProcessBuilder("sh", "-c", "command -v shellcheck")
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        }.getOrDefault(false)
}
