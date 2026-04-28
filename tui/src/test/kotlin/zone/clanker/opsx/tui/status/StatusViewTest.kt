package zone.clanker.opsx.tui.status

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import zone.clanker.opsx.cli.status.ActivityEvent
import zone.clanker.opsx.cli.status.ChangeEntry
import zone.clanker.opsx.cli.status.State
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

class StatusViewTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 100, height = 40)
        val t = Terminal(terminalInterface = recorder)
        val m = Styles.margin
        test("buildLedgerWidget shows header with change count") {
            val entries =
                listOf(
                    ChangeEntry("add-retry", "active", 3, 8),
                    ChangeEntry("fix-bug", "completed", 5, 5),
                )
            val state = StatusView.LedgerState(cursor = 0, entries = entries)
            val widget = StatusView.buildLedgerWidget(state, m)
            val rendered = t.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "status"
            rendered shouldContain "2 changes"
        }

        test("buildLedgerWidget shows all change names") {
            val entries =
                listOf(
                    ChangeEntry("add-retry", "active", 3, 8),
                    ChangeEntry("fix-bug", "completed", 5, 5),
                )
            val state = StatusView.LedgerState(cursor = 0, entries = entries)
            val widget = StatusView.buildLedgerWidget(state, m)
            val rendered = t.render(widget)
            rendered shouldContain "add-retry"
            rendered shouldContain "fix-bug"
        }

        test("buildLedgerWidget cursor at first entry renders without error") {
            val entries =
                listOf(
                    ChangeEntry("alpha", "active", 0, 5),
                    ChangeEntry("beta", "completed", 5, 5),
                    ChangeEntry("gamma", "verified", 8, 8),
                )
            val state = StatusView.LedgerState(cursor = 0, entries = entries)
            val widget = StatusView.buildLedgerWidget(state, m)
            val rendered = t.render(widget)
            rendered shouldContain "alpha"
        }

        test("buildTreeWidget shows change name in header") {
            val entry = ChangeEntry("add-retry", "active", 3, 8)
            val events = emptyList<ActivityEvent>()
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "add-retry"
        }

        test("buildTreeWidget shows empty state message when no events") {
            val entry = ChangeEntry("fresh-change", "active", 0, 4)
            val events = emptyList<ActivityEvent>()
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "no activity recorded yet"
        }

        test("buildTreeWidget shows agent events grouped by agent") {
            val entry = ChangeEntry("add-retry", "in-progress", 2, 8)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "developer",
                        state = State.START,
                        task = "a1b2c3",
                        desc = "implementing retry",
                    ),
                    ActivityEvent(
                        ts = "2026-04-24T10:05:00Z",
                        agent = "developer",
                        state = State.DONE,
                        task = "a1b2c3",
                        desc = "retry added",
                    ),
                    ActivityEvent(
                        ts = "2026-04-24T10:06:00Z",
                        agent = "qa",
                        state = State.START,
                        task = "d4e5f6",
                        desc = "writing tests",
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "@developer"
            rendered shouldContain "@qa"
            rendered shouldContain "implementing retry"
            rendered shouldContain "retry added"
            rendered shouldContain "writing tests"
        }

        test("buildTreeWidget shows task IDs in brackets") {
            val entry = ChangeEntry("change", "in-progress", 1, 3)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "developer",
                        state = State.DONE,
                        task = "abc123",
                        desc = "done something",
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "[abc123]"
        }

        test("buildTreeWidget shows events without task ID") {
            val entry = ChangeEntry("change", "active", 0, 2)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "lead",
                        state = State.START,
                        task = null,
                        desc = "proposing change",
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "proposing change"
            rendered shouldContain "@lead"
        }

        test("buildTreeWidget shows failed events") {
            val entry = ChangeEntry("buggy", "in-progress", 1, 5)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "qa",
                        state = State.FAILED,
                        task = "test1",
                        desc = "tests failed",
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "tests failed"
        }

        test("buildTreeWidget renders empty events without error") {
            val entry = ChangeEntry("change", "active", 3, 8)
            val events = emptyList<ActivityEvent>()
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "change"
        }

        test("buildTreeWidget handles unknown agent without color") {
            val entry = ChangeEntry("change", "active", 0, 1)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "unknown-agent",
                        state = State.START,
                        desc = "doing something",
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "@unknown-agent"
        }

        test("LedgerState data class holds cursor and entries") {
            val entries = listOf(ChangeEntry("a", "active", 0, 1))
            val state = StatusView.LedgerState(cursor = 0, entries = entries)
            state.cursor shouldBe 0
            state.entries.size shouldBe 1
        }

        test("buildMessageWidget shows opsx status header") {
            val widget = StatusView.buildMessageWidget("No changes found.", m)
            val rendered = t.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "status"
        }

        test("buildMessageWidget shows the message text") {
            val widget = StatusView.buildMessageWidget("No changes found. Use /opsx-propose to start one.", m)
            val rendered = t.render(widget)
            rendered shouldContain "No changes found"
            rendered shouldContain "/opsx-propose"
        }

        test("buildMessageWidget with custom message") {
            val widget = StatusView.buildMessageWidget("Something went wrong", m)
            val rendered = t.render(widget)
            rendered shouldContain "Something went wrong"
        }

        test("buildLedgerWidget with single entry shows cursor pointer") {
            val entries = listOf(ChangeEntry("only-one", "active", 2, 4))
            val state = StatusView.LedgerState(cursor = 0, entries = entries)
            val widget = StatusView.buildLedgerWidget(state, m)
            val rendered = t.render(widget)
            rendered shouldContain "only-one"
        }

        test("buildTreeWidget with multiple agents groups events correctly") {
            val entry = ChangeEntry("multi-agent", "in-progress", 3, 10)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "developer",
                        state = State.START,
                        task = "t1",
                        desc = "coding",
                    ),
                    ActivityEvent(
                        ts = "2026-04-24T10:01:00Z",
                        agent = "qa",
                        state = State.START,
                        task = "t2",
                        desc = "testing",
                    ),
                    ActivityEvent(
                        ts = "2026-04-24T10:02:00Z",
                        agent = "forge",
                        state = State.DONE,
                        task = "t3",
                        desc = "building",
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "@developer"
            rendered shouldContain "@qa"
            rendered shouldContain "@forge"
            rendered shouldContain "coding"
            rendered shouldContain "testing"
            rendered shouldContain "building"
        }

        test("buildTreeWidget shows badge and progress bar") {
            val entry = ChangeEntry("change-with-progress", "in-progress", 5, 10)
            val events = emptyList<ActivityEvent>()
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "change-with-progress"
        }

        test("translateLedgerKey maps DOWN to MoveDown") {
            StatusView.translateLedgerKey(Keys.DOWN) shouldBe StatusView.LedgerAction.MoveDown
        }

        test("translateLedgerKey maps j to MoveDown") {
            StatusView.translateLedgerKey(KeyboardEvent("j")) shouldBe StatusView.LedgerAction.MoveDown
        }

        test("translateLedgerKey maps UP to MoveUp") {
            StatusView.translateLedgerKey(Keys.UP) shouldBe StatusView.LedgerAction.MoveUp
        }

        test("translateLedgerKey maps k to MoveUp") {
            StatusView.translateLedgerKey(KeyboardEvent("k")) shouldBe StatusView.LedgerAction.MoveUp
        }

        test("translateLedgerKey maps ENTER to Open") {
            StatusView.translateLedgerKey(Keys.ENTER) shouldBe StatusView.LedgerAction.Open
        }

        test("translateLedgerKey maps l to Open") {
            StatusView.translateLedgerKey(KeyboardEvent("l")) shouldBe StatusView.LedgerAction.Open
        }

        test("translateLedgerKey maps RIGHT to Open") {
            StatusView.translateLedgerKey(Keys.RIGHT) shouldBe StatusView.LedgerAction.Open
        }

        test("translateLedgerKey maps r to Refresh") {
            StatusView.translateLedgerKey(KeyboardEvent("r")) shouldBe StatusView.LedgerAction.Refresh
        }

        test("translateLedgerKey maps q to Back") {
            StatusView.translateLedgerKey(KeyboardEvent("q")) shouldBe StatusView.LedgerAction.Back
        }

        test("translateLedgerKey maps Q to Back") {
            StatusView.translateLedgerKey(KeyboardEvent("Q")) shouldBe StatusView.LedgerAction.Back
        }

        test("translateLedgerKey maps ESCAPE to Back") {
            StatusView.translateLedgerKey(Keys.ESCAPE) shouldBe StatusView.LedgerAction.Back
        }

        test("translateLedgerKey maps h to Back") {
            StatusView.translateLedgerKey(KeyboardEvent("h")) shouldBe StatusView.LedgerAction.Back
        }

        test("translateLedgerKey maps LEFT to Back") {
            StatusView.translateLedgerKey(Keys.LEFT) shouldBe StatusView.LedgerAction.Back
        }

        test("translateLedgerKey returns null for unrecognised keys") {
            StatusView.translateLedgerKey(KeyboardEvent("x")).shouldBeNull()
            StatusView.translateLedgerKey(KeyboardEvent("z")).shouldBeNull()
        }

        test("applyLedgerNav MoveDown increments cursor") {
            StatusView.applyLedgerNav(StatusView.LedgerAction.MoveDown, 0, 5) shouldBe 1
        }

        test("applyLedgerNav MoveDown clamps at max") {
            StatusView.applyLedgerNav(StatusView.LedgerAction.MoveDown, 4, 5) shouldBe 4
        }

        test("applyLedgerNav MoveUp decrements cursor") {
            StatusView.applyLedgerNav(StatusView.LedgerAction.MoveUp, 3, 5) shouldBe 2
        }

        test("applyLedgerNav MoveUp clamps at zero") {
            StatusView.applyLedgerNav(StatusView.LedgerAction.MoveUp, 0, 5) shouldBe 0
        }

        test("applyLedgerNav Open returns same cursor") {
            StatusView.applyLedgerNav(StatusView.LedgerAction.Open, 2, 5) shouldBe 2
        }

        test("applyLedgerNav Refresh returns same cursor") {
            StatusView.applyLedgerNav(StatusView.LedgerAction.Refresh, 3, 5) shouldBe 3
        }

        test("applyLedgerNav Back returns same cursor") {
            StatusView.applyLedgerNav(StatusView.LedgerAction.Back, 1, 5) shouldBe 1
        }

        test("translateTreeKey maps LEFT to Back") {
            StatusView.translateTreeKey(Keys.LEFT) shouldBe StatusView.TreeAction.Back
        }

        test("translateTreeKey maps h to Back") {
            StatusView.translateTreeKey(KeyboardEvent("h")) shouldBe StatusView.TreeAction.Back
        }

        test("translateTreeKey maps ESCAPE to Back") {
            StatusView.translateTreeKey(Keys.ESCAPE) shouldBe StatusView.TreeAction.Back
        }

        test("translateTreeKey maps q to Back") {
            StatusView.translateTreeKey(KeyboardEvent("q")) shouldBe StatusView.TreeAction.Back
        }

        test("translateTreeKey returns null for unrecognised keys") {
            StatusView.translateTreeKey(KeyboardEvent("j")).shouldBeNull()
            StatusView.translateTreeKey(Keys.DOWN).shouldBeNull()
        }

        test("translateMessageKey maps LEFT to Dismiss") {
            StatusView.translateMessageKey(Keys.LEFT) shouldBe StatusView.MessageAction.Dismiss
        }

        test("translateMessageKey maps h to Dismiss") {
            StatusView.translateMessageKey(KeyboardEvent("h")) shouldBe StatusView.MessageAction.Dismiss
        }

        test("translateMessageKey maps ESCAPE to Dismiss") {
            StatusView.translateMessageKey(Keys.ESCAPE) shouldBe StatusView.MessageAction.Dismiss
        }

        test("translateMessageKey maps q to Dismiss") {
            StatusView.translateMessageKey(KeyboardEvent("q")) shouldBe StatusView.MessageAction.Dismiss
        }

        test("translateMessageKey maps ENTER to Dismiss") {
            StatusView.translateMessageKey(Keys.ENTER) shouldBe StatusView.MessageAction.Dismiss
        }

        test("translateMessageKey returns null for unrecognised keys") {
            StatusView.translateMessageKey(KeyboardEvent("j")).shouldBeNull()
            StatusView.translateMessageKey(KeyboardEvent("x")).shouldBeNull()
        }

        test("refreshEntries returns empty list for nonexistent directory") {
            val (entries, cursor) = StatusView.refreshEntries(Path("/nonexistent/path"), 0)
            entries shouldBe emptyList()
            cursor shouldBe 0
        }

        test("refreshEntries clamps cursor to zero for empty results") {
            val (_, cursor) = StatusView.refreshEntries(Path("/nonexistent/path"), 5)
            cursor shouldBe 0
        }

        test("buildTreeWidget handles null desc in events") {
            val entry = ChangeEntry("change", "active", 0, 1)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "developer",
                        state = State.START,
                        task = "t1",
                        desc = null,
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "@developer"
        }

        test("buildTreeWidget handles null task in events") {
            val entry = ChangeEntry("change", "active", 0, 1)
            val events =
                listOf(
                    ActivityEvent(
                        ts = "2026-04-24T10:00:00Z",
                        agent = "developer",
                        state = State.DONE,
                        task = null,
                        desc = "finished work",
                    ),
                )
            val widget = StatusView.buildTreeWidget(m, entry, events)
            val rendered = t.render(widget)
            rendered shouldContain "finished work"
        }

        test("refreshEntries returns entries and clamped cursor for populated workspace") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-test-${System.nanoTime()}")
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
                    .use { it.writeString("- [x] task1\n- [ ] task2\n") }

                val (entries, cursor) = StatusView.refreshEntries(tmpDir, 0)
                entries.size shouldBe 1
                entries[0].name shouldBe "my-change"
                entries[0].status shouldBe "active"
                cursor shouldBe 0
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }

        test("processLedgerEvent MoveDown returns Navigate with new cursor") {
            val effect = StatusView.processLedgerEvent(StatusView.LedgerAction.MoveDown, 0, 5)
            effect shouldBe StatusView.LedgerEffect.Navigate(1)
        }

        test("processLedgerEvent MoveUp returns Navigate with new cursor") {
            val effect = StatusView.processLedgerEvent(StatusView.LedgerAction.MoveUp, 3, 5)
            effect shouldBe StatusView.LedgerEffect.Navigate(2)
        }

        test("processLedgerEvent MoveDown at max returns Navigate clamped") {
            val effect = StatusView.processLedgerEvent(StatusView.LedgerAction.MoveDown, 4, 5)
            effect shouldBe StatusView.LedgerEffect.Navigate(4)
        }

        test("processLedgerEvent MoveUp at zero returns Navigate clamped") {
            val effect = StatusView.processLedgerEvent(StatusView.LedgerAction.MoveUp, 0, 5)
            effect shouldBe StatusView.LedgerEffect.Navigate(0)
        }

        test("processLedgerEvent Open returns OpenDetail with cursor") {
            val effect = StatusView.processLedgerEvent(StatusView.LedgerAction.Open, 2, 5)
            effect shouldBe StatusView.LedgerEffect.OpenDetail(2)
        }

        test("processLedgerEvent Refresh returns Reload") {
            val effect = StatusView.processLedgerEvent(StatusView.LedgerAction.Refresh, 1, 5)
            effect shouldBe StatusView.LedgerEffect.Reload
        }

        test("processLedgerEvent Back returns Exit") {
            val effect = StatusView.processLedgerEvent(StatusView.LedgerAction.Back, 0, 5)
            effect shouldBe StatusView.LedgerEffect.Exit
        }

        test("processLedgerEvent null returns null") {
            val effect = StatusView.processLedgerEvent(null, 0, 5)
            effect.shouldBeNull()
        }

        test("LedgerEffect Navigate data class") {
            val nav = StatusView.LedgerEffect.Navigate(3)
            nav.newCursor shouldBe 3
        }

        test("LedgerEffect OpenDetail data class") {
            val open = StatusView.LedgerEffect.OpenDetail(2)
            open.cursor shouldBe 2
        }

        test("refreshEntries clamps cursor when cursor exceeds entry count") {
            val tmpDir = Path(SystemTemporaryDirectory, "opsx-test-${System.nanoTime()}")
            try {
                val changesDir = Path(tmpDir, "opsx/changes/only-one")
                SystemFileSystem.createDirectories(changesDir)
                SystemFileSystem
                    .sink(Path(changesDir, ".opsx.yaml"))
                    .buffered()
                    .use { it.writeString("status: draft\n") }

                val (entries, cursor) = StatusView.refreshEntries(tmpDir, 10)
                entries.size shouldBe 1
                cursor shouldBe 0
            } finally {
                java.io.File(tmpDir.toString()).deleteRecursively()
            }
        }
    })
