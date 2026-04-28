package zone.clanker.opsx.tui.status

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString

/** Interactive [StatusView.show] tests extracted to keep class size under the detekt threshold. */
class StatusViewShowTest :
    FunSpec({

        test("show displays message screen when no changes exist and exits on q") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            StatusView.show(term, Path("/nonexistent/path"))
        }

        test("show displays ledger and navigates when changes exist") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-status-test-${System.nanoTime()}")
            try {
                val changesDir = Path(tmpDir, "opsx/changes/test-change")
                SystemFileSystem.createDirectories(changesDir)
                SystemFileSystem
                    .sink(Path(changesDir, ".opsx.yaml"))
                    .buffered()
                    .use { it.writeString("status: active\n") }
                SystemFileSystem
                    .sink(Path(changesDir, "tasks.md"))
                    .buffered()
                    .use { it.writeString("- [x] task1\n- [ ] task2\n") }

                val rec =
                    TerminalRecorder(
                        ansiLevel = AnsiLevel.NONE,
                        width = 100,
                        height = 40,
                        inputInteractive = true,
                    )
                rec.inputEvents.add(KeyboardEvent("j"))
                rec.inputEvents.add(KeyboardEvent("q"))
                val term = Terminal(terminalInterface = rec)
                StatusView.show(term, tmpDir)
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }

        test("show opens tree view and returns on Enter then q") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-tree-test-${System.nanoTime()}")
            try {
                val changesDir = Path(tmpDir, "opsx/changes/my-change")
                SystemFileSystem.createDirectories(changesDir)
                SystemFileSystem
                    .sink(Path(changesDir, ".opsx.yaml"))
                    .buffered()
                    .use { it.writeString("status: active\n") }
                SystemFileSystem
                    .sink(Path(changesDir, "tasks.md"))
                    .buffered()
                    .use { it.writeString("- [x] t1\n- [ ] t2\n") }

                val rec =
                    TerminalRecorder(
                        ansiLevel = AnsiLevel.NONE,
                        width = 100,
                        height = 40,
                        inputInteractive = true,
                    )
                rec.inputEvents.add(KeyboardEvent("Enter"))
                rec.inputEvents.add(KeyboardEvent("q"))
                rec.inputEvents.add(KeyboardEvent("q"))
                val term = Terminal(terminalInterface = rec)
                StatusView.show(term, tmpDir)
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }

        test("show handles refresh action") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-refresh-test-${System.nanoTime()}")
            try {
                val changesDir = Path(tmpDir, "opsx/changes/refreshable")
                SystemFileSystem.createDirectories(changesDir)
                SystemFileSystem
                    .sink(Path(changesDir, ".opsx.yaml"))
                    .buffered()
                    .use { it.writeString("status: active\n") }

                val rec =
                    TerminalRecorder(
                        ansiLevel = AnsiLevel.NONE,
                        width = 100,
                        height = 40,
                        inputInteractive = true,
                    )
                rec.inputEvents.add(KeyboardEvent("r"))
                rec.inputEvents.add(KeyboardEvent("q"))
                val term = Terminal(terminalInterface = rec)
                StatusView.show(term, tmpDir)
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }

        test("show displays message screen and dismisses on Enter") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            StatusView.show(term, Path("/nonexistent/path"))
        }

        test("show ignores unrecognised keys in ledger before quitting") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-unrecognised-test-${System.nanoTime()}")
            try {
                val changesDir = Path(tmpDir, "opsx/changes/some-change")
                SystemFileSystem.createDirectories(changesDir)
                SystemFileSystem
                    .sink(Path(changesDir, ".opsx.yaml"))
                    .buffered()
                    .use { it.writeString("status: active\n") }

                val rec =
                    TerminalRecorder(
                        ansiLevel = AnsiLevel.NONE,
                        width = 100,
                        height = 40,
                        inputInteractive = true,
                    )
                rec.inputEvents.add(KeyboardEvent("x"))
                rec.inputEvents.add(KeyboardEvent("z"))
                rec.inputEvents.add(KeyboardEvent("q"))
                val term = Terminal(terminalInterface = rec)
                StatusView.show(term, tmpDir)
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }

        test("show handles navigation up at top stays at cursor 0") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-nav-up-test-${System.nanoTime()}")
            try {
                val changesDir = Path(tmpDir, "opsx/changes/only-change")
                SystemFileSystem.createDirectories(changesDir)
                SystemFileSystem
                    .sink(Path(changesDir, ".opsx.yaml"))
                    .buffered()
                    .use { it.writeString("status: active\n") }

                val rec =
                    TerminalRecorder(
                        ansiLevel = AnsiLevel.NONE,
                        width = 100,
                        height = 40,
                        inputInteractive = true,
                    )
                rec.inputEvents.add(KeyboardEvent("k"))
                rec.inputEvents.add(KeyboardEvent("q"))
                val term = Terminal(terminalInterface = rec)
                StatusView.show(term, tmpDir)
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }

        test("show message screen ignores unrecognised then exits on h") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("h"))
            val term = Terminal(terminalInterface = rec)
            StatusView.show(term, Path("/nonexistent/path"))
        }

        test("show opens tree view then navigates back with h") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-tree-h-test-${System.nanoTime()}")
            try {
                val changesDir = Path(tmpDir, "opsx/changes/my-change")
                SystemFileSystem.createDirectories(changesDir)
                SystemFileSystem
                    .sink(Path(changesDir, ".opsx.yaml"))
                    .buffered()
                    .use { it.writeString("status: active\n") }
                SystemFileSystem
                    .sink(Path(changesDir, "tasks.md"))
                    .buffered()
                    .use { it.writeString("- [x] t1\n") }

                val rec =
                    TerminalRecorder(
                        ansiLevel = AnsiLevel.NONE,
                        width = 100,
                        height = 40,
                        inputInteractive = true,
                    )
                rec.inputEvents.add(KeyboardEvent("l"))
                rec.inputEvents.add(KeyboardEvent("x"))
                rec.inputEvents.add(KeyboardEvent("h"))
                rec.inputEvents.add(KeyboardEvent("Escape"))
                val term = Terminal(terminalInterface = rec)
                StatusView.show(term, tmpDir)
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }
    })
