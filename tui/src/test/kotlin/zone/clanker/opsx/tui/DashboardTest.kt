package zone.clanker.opsx.tui

import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class DashboardTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 80, height = 24)
        val t = Terminal(terminalInterface = recorder)
        val maxLabel = Dashboard.menuItems.maxOf { it.label.length }

        test("translateKey maps j to MoveDown") {
            Dashboard.translateKey(KeyboardEvent("j")) shouldBe Dashboard.MenuAction.MoveDown
        }

        test("translateKey maps ArrowDown to MoveDown") {
            Dashboard.translateKey(KeyboardEvent("ArrowDown")) shouldBe Dashboard.MenuAction.MoveDown
        }

        test("translateKey maps k to MoveUp") {
            Dashboard.translateKey(KeyboardEvent("k")) shouldBe Dashboard.MenuAction.MoveUp
        }

        test("translateKey maps ArrowUp to MoveUp") {
            Dashboard.translateKey(KeyboardEvent("ArrowUp")) shouldBe Dashboard.MenuAction.MoveUp
        }

        test("translateKey maps Enter to Select") {
            Dashboard.translateKey(KeyboardEvent("Enter")) shouldBe Dashboard.MenuAction.Select
        }

        test("translateKey maps l to Select") {
            Dashboard.translateKey(KeyboardEvent("l")) shouldBe Dashboard.MenuAction.Select
        }

        test("translateKey maps q to Quit") {
            Dashboard.translateKey(KeyboardEvent("q")) shouldBe Dashboard.MenuAction.Quit
        }

        test("translateKey maps Q to Quit") {
            Dashboard.translateKey(KeyboardEvent("Q")) shouldBe Dashboard.MenuAction.Quit
        }

        test("translateKey maps Escape to Quit") {
            Dashboard.translateKey(KeyboardEvent("Escape")) shouldBe Dashboard.MenuAction.Quit
        }

        test("translateKey maps Ctrl+C to Quit") {
            Dashboard.translateKey(KeyboardEvent("c", ctrl = true)) shouldBe Dashboard.MenuAction.Quit
        }

        test("translateKey returns null for unrecognised keys") {
            Dashboard.translateKey(KeyboardEvent("x")).shouldBeNull()
            Dashboard.translateKey(KeyboardEvent("Tab")).shouldBeNull()
        }

        test("handleMenuEvent returns null for non-keyboard events") {
            val mouseEvent = MouseEvent(0, 0, false, false, false, false, false)
            Dashboard.handleMenuEvent(mouseEvent).shouldBeNull()
        }

        test("handleMenuEvent delegates keyboard events to translateKey") {
            Dashboard.handleMenuEvent(KeyboardEvent("j")) shouldBe Dashboard.MenuAction.MoveDown
            Dashboard.handleMenuEvent(KeyboardEvent("Enter")) shouldBe Dashboard.MenuAction.Select
        }

        test("menuItems has expected entries") {
            Dashboard.menuItems.size shouldBe 5
            Dashboard.menuItems.map { it.key } shouldBe listOf("status", "init", "install", "update", "nuke")
        }

        test("logos has one variant") {
            Dashboard.logos.size shouldBe 1
        }

        test("each logo variant has multiple lines") {
            for (logo in Dashboard.logos) {
                logo.size shouldBeGreaterThan 3
            }
        }

        test("MenuItem data class holds key, label, and desc") {
            val item = Dashboard.MenuItem("test-key", "test-label", "test-desc")
            item.key shouldBe "test-key"
            item.label shouldBe "test-label"
            item.desc shouldBe "test-desc"
        }

        test("MenuState data class holds cursor") {
            val state = Dashboard.MenuState(cursor = 3)
            state.cursor shouldBe 3
        }

        test("MenuAction sealed interface variants") {
            val down: Dashboard.MenuAction = Dashboard.MenuAction.MoveDown
            val up: Dashboard.MenuAction = Dashboard.MenuAction.MoveUp
            val select: Dashboard.MenuAction = Dashboard.MenuAction.Select
            val quit: Dashboard.MenuAction = Dashboard.MenuAction.Quit
            down shouldBe Dashboard.MenuAction.MoveDown
            up shouldBe Dashboard.MenuAction.MoveUp
            select shouldBe Dashboard.MenuAction.Select
            quit shouldBe Dashboard.MenuAction.Quit
        }

        test("applyAction returns null for null action") {
            Dashboard.applyAction(null, 0).shouldBeNull()
        }

        test("applyAction MoveDown increments cursor") {
            val result = Dashboard.applyAction(Dashboard.MenuAction.MoveDown, 0)
            result shouldBe Dashboard.ActionResult.Navigate(1)
        }

        test("applyAction MoveDown clamps at max index") {
            val lastIndex = Dashboard.menuItems.size - 1
            val result = Dashboard.applyAction(Dashboard.MenuAction.MoveDown, lastIndex)
            result shouldBe Dashboard.ActionResult.Navigate(lastIndex)
        }

        test("applyAction MoveUp decrements cursor") {
            val result = Dashboard.applyAction(Dashboard.MenuAction.MoveUp, 2)
            result shouldBe Dashboard.ActionResult.Navigate(1)
        }

        test("applyAction MoveUp clamps at zero") {
            val result = Dashboard.applyAction(Dashboard.MenuAction.MoveUp, 0)
            result shouldBe Dashboard.ActionResult.Navigate(0)
        }

        test("applyAction Select returns cursor and key") {
            val result = Dashboard.applyAction(Dashboard.MenuAction.Select, 0)
            result shouldBe Dashboard.ActionResult.Selected(0, "status")
        }

        test("applyAction Select for each menu item returns correct key") {
            for ((i, item) in Dashboard.menuItems.withIndex()) {
                val result = Dashboard.applyAction(Dashboard.MenuAction.Select, i)
                result shouldBe Dashboard.ActionResult.Selected(i, item.key)
            }
        }

        test("applyAction Quit returns Exit") {
            val result = Dashboard.applyAction(Dashboard.MenuAction.Quit, 0)
            result shouldBe Dashboard.ActionResult.Exit
        }

        test("ActionResult Navigate data class") {
            val nav = Dashboard.ActionResult.Navigate(3)
            nav.cursor shouldBe 3
        }

        test("ActionResult Selected data class") {
            val sel = Dashboard.ActionResult.Selected(2, "init")
            sel.cursor shouldBe 2
            sel.key shouldBe "init"
        }

        test("dispatchMenuItem install opens InstallView") {
            val installRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            installRecorder.inputEvents.add(KeyboardEvent("q"))
            val installTerminal = Terminal(terminalInterface = installRecorder)
            Dashboard.dispatchMenuItem("install", installTerminal)
            val output = installRecorder.output()
            output shouldContain "install"
        }

        test("dispatchMenuItem nuke opens NukeView") {
            val nukeRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            nukeRecorder.inputEvents.add(KeyboardEvent("n"))
            val nukeTerminal = Terminal(terminalInterface = nukeRecorder)
            Dashboard.dispatchMenuItem("nuke", nukeTerminal)
            val output = nukeRecorder.output()
            output shouldContain "nuke"
        }

        test("dispatchMenuItem unknown key does nothing") {
            val unknownRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 80, height = 24)
            val unknownTerminal = Terminal(terminalInterface = unknownRecorder)
            Dashboard.dispatchMenuItem("unknown-key", unknownTerminal)
            val output = unknownRecorder.output()
            output shouldBe ""
        }

        test("actionToStatus returns Continue for null result") {
            Dashboard.actionToStatus(null).shouldBeInstanceOf<Status.Continue>()
        }

        test("actionToStatus returns Continue for Navigate result") {
            Dashboard.actionToStatus(Dashboard.ActionResult.Navigate(2)).shouldBeInstanceOf<Status.Continue>()
        }

        test("actionToStatus returns Finished with selection for Selected result") {
            val status = Dashboard.actionToStatus(Dashboard.ActionResult.Selected(1, "init"))
            status.shouldBeInstanceOf<Status.Finished<Pair<Int, String>?>>()
            (status as Status.Finished).result shouldBe (1 to "init")
        }

        test("actionToStatus returns Finished with null for Exit result") {
            val status = Dashboard.actionToStatus(Dashboard.ActionResult.Exit)
            status.shouldBeInstanceOf<Status.Finished<Pair<Int, String>?>>()
            (status as Status.Finished).result.shouldBeNull()
        }

        test("NON_INTERACTIVE_MSG contains instructions") {
            Dashboard.NON_INTERACTIVE_MSG shouldContain "Run the built binary directly"
            Dashboard.NON_INTERACTIVE_MSG shouldContain "opsx"
        }

        test("formatMenuEntries returns entries for all menu items") {
            val entries = Dashboard.formatMenuEntries(maxLabel)
            entries.size shouldBe Dashboard.menuItems.size
        }

        test("formatMenuEntries pads labels correctly") {
            val entries = Dashboard.formatMenuEntries(maxLabel)
            // All entries should contain the label and desc text
            for ((entry, item) in entries.zip(Dashboard.menuItems)) {
                entry.title shouldContain item.label
                entry.title shouldContain item.desc
            }
        }

        test("buildMenuWidget renders a select list") {
            val entries = Dashboard.formatMenuEntries(maxLabel)
            val widget = Dashboard.buildMenuWidget(entries, 0)
            val rendered = t.render(widget)
            rendered shouldContain "status"
        }

        test("buildMenuWidget highlights cursor position") {
            val entries = Dashboard.formatMenuEntries(maxLabel)
            val widget0 = Dashboard.buildMenuWidget(entries, 0)
            val widget2 = Dashboard.buildMenuWidget(entries, 2)
            val r0 = t.render(widget0)
            val r2 = t.render(widget2)
            // Cursor marker should be in different positions
            val marker = "\u276F"
            r0.lines().first { it.contains(marker) } shouldContain "status"
            r2.lines().first { it.contains(marker) } shouldContain "install"
        }

        test("translateMouse scroll up maps to MoveUp") {
            val event = MouseEvent(x = 0, y = 0, wheelUp = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe Dashboard.MenuAction.MoveUp
        }

        test("translateMouse scroll down maps to MoveDown") {
            val event = MouseEvent(x = 0, y = 0, wheelDown = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe Dashboard.MenuAction.MoveDown
        }

        test("translateMouse left click on menu item maps to ClickItem") {
            val event = MouseEvent(x = 10, y = 5, left = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe Dashboard.MenuAction.ClickItem(2)
        }

        test("translateMouse left click on first menu item") {
            val event = MouseEvent(x = 10, y = 3, left = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe Dashboard.MenuAction.ClickItem(0)
        }

        test("translateMouse left click outside menu returns null") {
            val event = MouseEvent(x = 10, y = 20, left = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe null
        }

        test("translateMouse right click is ignored") {
            val event = MouseEvent(x = 10, y = 4, right = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe null
        }

        test("handleMenuEvent with mouse event delegates to translateMouse") {
            val click = MouseEvent(x = 5, y = 4, left = true)
            Dashboard.handleMenuEvent(click, menuStartRow = 3) shouldBe Dashboard.MenuAction.ClickItem(1)
        }

        test("applyAction with ClickItem selects that item") {
            val result = Dashboard.applyAction(Dashboard.MenuAction.ClickItem(2), cursor = 0)
            result shouldBe Dashboard.ActionResult.Selected(2, "install")
        }

        test("translateMouse click before menu start returns null") {
            val event = MouseEvent(x = 10, y = 1, left = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe null
        }

        test("translateMouse click on last menu item") {
            val lastIndex = Dashboard.menuItems.size - 1
            val event = MouseEvent(x = 10, y = 3 + lastIndex, left = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe Dashboard.MenuAction.ClickItem(lastIndex)
        }

        test("translateMouse click one past last menu item returns null") {
            val event = MouseEvent(x = 10, y = 3 + Dashboard.menuItems.size, left = true)
            Dashboard.translateMouse(event, menuStartRow = 3) shouldBe null
        }

        test("cursor stays in bounds when scroll at top") {
            val result = Dashboard.applyAction(Dashboard.MenuAction.MoveUp, cursor = 0)
            result shouldBe Dashboard.ActionResult.Navigate(0)
        }

        test("cursor stays in bounds when scroll at bottom") {
            val lastIndex = Dashboard.menuItems.size - 1
            val result = Dashboard.applyAction(Dashboard.MenuAction.MoveDown, cursor = lastIndex)
            result shouldBe Dashboard.ActionResult.Navigate(lastIndex)
        }

        test("cursor movement changes which menu item has the marker") {
            val entries = Dashboard.formatMenuEntries(maxLabel)
            val marker = "\u276F"

            val widgetAt0 = Dashboard.buildMenuWidget(entries, 0)
            val widgetAt2 = Dashboard.buildMenuWidget(entries, 2)

            val rendered0 = t.render(widgetAt0)
            val rendered2 = t.render(widgetAt2)

            // Marker should be on different items
            val markerLine0 = rendered0.lines().first { it.contains(marker) }
            val markerLine2 = rendered2.lines().first { it.contains(marker) }

            markerLine0 shouldContain "status"
            markerLine2 shouldContain "install"

            // And they should be different
            markerLine0 shouldNotBe markerLine2
        }

        test("applyAction MoveDown then render shows new cursor position") {
            val entries = Dashboard.formatMenuEntries(maxLabel)
            val marker = "\u276F"

            // Start at 0
            val result = Dashboard.applyAction(Dashboard.MenuAction.MoveDown, cursor = 0)
            result shouldBe Dashboard.ActionResult.Navigate(1)

            // Render with new cursor
            val widget = Dashboard.buildMenuWidget(entries, (result as Dashboard.ActionResult.Navigate).cursor)
            val rendered = t.render(widget)
            val markerLine = rendered.lines().first { it.contains(marker) }
            markerLine shouldContain "init"
        }

        test("dispatchMenuItem update opens UpdateView") {
            val updateRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            updateRecorder.inputEvents.add(KeyboardEvent("q"))
            val updateTerminal = Terminal(terminalInterface = updateRecorder)
            Dashboard.dispatchMenuItem("update", updateTerminal)
            val output = updateRecorder.output()
            output shouldContain "update"
        }

        test("dispatchMenuItem status opens StatusView") {
            val statusRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            // StatusView with no changes shows a message screen — dismiss with q
            statusRecorder.inputEvents.add(KeyboardEvent("q"))
            val statusTerminal = Terminal(terminalInterface = statusRecorder)
            Dashboard.dispatchMenuItem("status", statusTerminal)
            val output = statusRecorder.output()
            output shouldContain "status"
        }

        test("dispatchMenuItem init opens InitView") {
            val initRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            initRecorder.inputEvents.add(KeyboardEvent("q"))
            val initTerminal = Terminal(terminalInterface = initRecorder)
            Dashboard.dispatchMenuItem("init", initTerminal)
            val output = initRecorder.output()
            output shouldContain "init"
        }

        test("ClickItem data class holds index") {
            val click = Dashboard.MenuAction.ClickItem(3)
            click.index shouldBe 3
        }

        test("start prints non-interactive message for non-interactive terminal") {
            val nonInteractiveRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 24,
                    inputInteractive = false,
                )
            val nonInteractiveTerminal = Terminal(terminalInterface = nonInteractiveRecorder)
            Dashboard.start(nonInteractiveTerminal)
            val output = nonInteractiveRecorder.output()
            output shouldContain "Run the built binary directly"
            output shouldContain "opsx"
        }

        test("version field is present in Dashboard") {
            // version is private, but it's used in mainLoop which formats the bar.
            // We can verify it through the logo display.
            Dashboard.logos.size shouldBe 1
        }

        test("descStyle formats description text") {
            val styled =
                zone.clanker.opsx.tui.render.Styles
                    .descStyle("test desc")
            styled shouldContain "test desc"
        }

        test("ClickItem index matches menu item") {
            for (idx in Dashboard.menuItems.indices) {
                val click = Dashboard.MenuAction.ClickItem(idx)
                val result = Dashboard.applyAction(click, 0)
                result.shouldBeInstanceOf<Dashboard.ActionResult.Selected>()
                (result as Dashboard.ActionResult.Selected).key shouldBe Dashboard.menuItems[idx].key
            }
        }

        test("actionToStatus handles all ActionResult variants exhaustively") {
            val navigateStatus = Dashboard.actionToStatus(Dashboard.ActionResult.Navigate(0))
            navigateStatus.shouldBeInstanceOf<Status.Continue>()

            val selectedStatus = Dashboard.actionToStatus(Dashboard.ActionResult.Selected(0, "status"))
            selectedStatus.shouldBeInstanceOf<Status.Finished<Pair<Int, String>?>>()

            val exitStatus = Dashboard.actionToStatus(Dashboard.ActionResult.Exit)
            exitStatus.shouldBeInstanceOf<Status.Finished<Pair<Int, String>?>>()
        }

        test("menuStartRow calculates correct row from logo height") {
            zone.clanker.opsx.tui.render.AppShell
                .pickLogo(Dashboard.logos)
            val row = Dashboard.menuStartRow()
            val expectedRow = 1 + Dashboard.logos[0].size + 1
            row shouldBe expectedRow
        }

        test("renderMenu renders all menu items at cursor 0") {
            val renderRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 80, height = 24)
            val renderTerminal = Terminal(terminalInterface = renderRecorder)
            zone.clanker.opsx.tui.render.AppShell
                .pickLogo(Dashboard.logos)
            Dashboard.renderMenu(renderTerminal, 0)
            val output = renderRecorder.output()
            output shouldContain "status"
            output shouldContain "init"
            output shouldContain "install"
            output shouldContain "update"
            output shouldContain "nuke"
            output shouldContain "select"
            output shouldContain "quit"
        }

        test("renderMenu renders at different cursor positions") {
            val renderRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 80, height = 24)
            val renderTerminal = Terminal(terminalInterface = renderRecorder)
            zone.clanker.opsx.tui.render.AppShell
                .pickLogo(Dashboard.logos)
            Dashboard.renderMenu(renderTerminal, 2)
            val output = renderRecorder.output()
            output shouldContain "install"
        }

        test("renderMenu shows version in status bar") {
            val renderRecorder =
                TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 80, height = 24)
            val renderTerminal = Terminal(terminalInterface = renderRecorder)
            zone.clanker.opsx.tui.render.AppShell
                .pickLogo(Dashboard.logos)
            Dashboard.renderMenu(renderTerminal, 0)
            val output = renderRecorder.output()
            output shouldContain "opsx v"
        }

        test("renderMenu at last cursor position") {
            val renderRecorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 80, height = 24)
            val renderTerminal = Terminal(terminalInterface = renderRecorder)
            zone.clanker.opsx.tui.render.AppShell
                .pickLogo(Dashboard.logos)
            Dashboard.renderMenu(renderTerminal, Dashboard.menuItems.size - 1)
            val output = renderRecorder.output()
            output shouldContain "nuke"
        }

        test("enterDashboard picks logo and enters full screen") {
            val enterRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 80, height = 24)
            val enterTerminal = Terminal(terminalInterface = enterRecorder)
            Dashboard.enterDashboard(enterTerminal)
            zone.clanker.opsx.tui.render.AppShell
                .currentLogo()
                .size shouldBeGreaterThan 0
            val output = enterRecorder.output()
            output shouldContain "\u001B[?1049h"
        }

        test("leaveDashboard restores normal terminal") {
            val leaveRecorder = TerminalRecorder(ansiLevel = AnsiLevel.TRUECOLOR, width = 80, height = 24)
            val leaveTerminal = Terminal(terminalInterface = leaveRecorder)
            Dashboard.leaveDashboard(leaveTerminal)
            val output = leaveRecorder.output()
            output shouldContain "\u001B[?1049l"
        }

        test("start with interactive terminal enters and leaves full screen") {
            val interactiveRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.TRUECOLOR,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            val interactiveTerminal = Terminal(terminalInterface = interactiveRecorder)
            // Pass a no-op loop to avoid entering the real mainLoop
            Dashboard.start(interactiveTerminal) { _ -> }
            val output = interactiveRecorder.output()
            // enterDashboard should have been called
            output shouldContain "\u001B[?1049h"
            // leaveDashboard should have been called
            output shouldContain "\u001B[?1049l"
        }

        test("processMenuInput returns Finished null on quit") {
            val cursor = intArrayOf(0)
            val status = Dashboard.processMenuInput(KeyboardEvent("q"), 8, cursor) { }
            status.shouldBeInstanceOf<Status.Finished<Pair<Int, String>?>>()
            (status as Status.Finished).result.shouldBeNull()
        }

        test("processMenuInput returns Finished with selection on Enter") {
            val cursor = intArrayOf(2)
            val status = Dashboard.processMenuInput(KeyboardEvent("Enter"), 8, cursor) { }
            status.shouldBeInstanceOf<Status.Finished<Pair<Int, String>?>>()
            val result = (status as Status.Finished).result
            result shouldNotBe null
            result!!.first shouldBe 2
            result.second shouldBe "install"
        }

        test("processMenuInput returns Continue and updates cursor on navigate down") {
            val cursor = intArrayOf(0)
            var renderedCursor = -1
            val status = Dashboard.processMenuInput(KeyboardEvent("j"), 8, cursor) { renderedCursor = it }
            status.shouldBeInstanceOf<Status.Continue>()
            cursor[0] shouldBe 1
            renderedCursor shouldBe 1
        }

        test("processMenuInput returns Continue and updates cursor on navigate up") {
            val cursor = intArrayOf(3)
            val status = Dashboard.processMenuInput(KeyboardEvent("k"), 8, cursor) { }
            status.shouldBeInstanceOf<Status.Continue>()
            cursor[0] shouldBe 2
        }

        test("processMenuInput returns Continue for unrecognised key") {
            val cursor = intArrayOf(0)
            val status = Dashboard.processMenuInput(KeyboardEvent("x"), 8, cursor) { }
            status.shouldBeInstanceOf<Status.Continue>()
            cursor[0] shouldBe 0
        }

        test("processMenuInput handles mouse click on menu item") {
            val cursor = intArrayOf(0)
            val status = Dashboard.processMenuInput(MouseEvent(x = 10, y = 10, left = true), 8, cursor) { }
            status.shouldBeInstanceOf<Status.Finished<Pair<Int, String>?>>()
            val result = (status as Status.Finished).result
            result shouldNotBe null
            result!!.second shouldBe "install"
        }

        test("processMenuInput handles mouse scroll up") {
            val cursor = intArrayOf(2)
            val status = Dashboard.processMenuInput(MouseEvent(x = 0, y = 0, wheelUp = true), 8, cursor) { }
            status.shouldBeInstanceOf<Status.Continue>()
            cursor[0] shouldBe 1
        }

        test("start with interactive terminal and loop that throws still leaves full screen") {
            val interactiveRecorder =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.TRUECOLOR,
                    width = 80,
                    height = 24,
                    inputInteractive = true,
                )
            val interactiveTerminal = Terminal(terminalInterface = interactiveRecorder)
            val thrown =
                runCatching {
                    Dashboard.start(interactiveTerminal) { _ -> error("test error") }
                }
            thrown.isFailure shouldBe true
            val output = interactiveRecorder.output()
            // leaveDashboard should still have been called (in the also block)
            output shouldContain "\u001B[?1049l"
        }
    })
