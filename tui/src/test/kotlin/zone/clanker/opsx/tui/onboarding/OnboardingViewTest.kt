package zone.clanker.opsx.tui.onboarding

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import zone.clanker.opsx.tui.render.Keys

class OnboardingViewTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 80, height = 24)
        val t = Terminal(terminalInterface = recorder)
        val pages = OnboardingView.pages

        test("has 5 pages") {
            pages.size shouldBe 5
        }

        test("every page has a title") {
            for (page in pages) {
                page.title.length shouldBeGreaterThan 0
            }
        }

        test("every page has body content") {
            for (page in pages) {
                page.body.length shouldBeGreaterThan 10
            }
        }

        test("first page mentions opsx") {
            pages[0].body shouldContain "opsx"
        }

        test("last page has quick reference table") {
            val text = pages.last().body
            text shouldContain "opsx init"
            text shouldContain "opsx status"
            text shouldContain "opsx nuke"
        }

        test("page 2 mentions /opsx-propose") {
            pages[1].body shouldContain "/opsx-propose"
        }

        test("page 3 mentions /opsx-apply") {
            pages[2].body shouldContain "/opsx-apply"
        }

        test("buildPageWidget renders page title") {
            val widget = OnboardingView.buildPageWidget(t, 0, pages.size)
            val rendered = t.render(widget)
            rendered shouldContain pages[0].title
        }

        test("buildPageWidget renders page body content") {
            val widget = OnboardingView.buildPageWidget(t, 0, pages.size)
            val rendered = t.render(widget)
            rendered shouldContain "opsx"
        }

        test("buildPageWidget renders step indicator") {
            val widget = OnboardingView.buildPageWidget(t, 0, pages.size)
            val rendered = t.render(widget)
            rendered shouldContain "guide"
        }

        test("buildPageWidget renders first page content") {
            val widget = OnboardingView.buildPageWidget(t, 0, pages.size)
            val rendered = t.render(widget)
            rendered shouldContain pages[0].title
        }

        test("buildPageWidget renders middle page content") {
            val widget = OnboardingView.buildPageWidget(t, 2, pages.size)
            val rendered = t.render(widget)
            rendered shouldContain pages[2].title
        }

        test("buildPageWidget renders last page content") {
            val widget = OnboardingView.buildPageWidget(t, pages.size - 1, pages.size)
            val rendered = t.render(widget)
            rendered shouldContain pages[pages.size - 1].title
        }

        test("buildPageWidget renders each page without error") {
            for (i in pages.indices) {
                val widget = OnboardingView.buildPageWidget(t, i, pages.size)
                val rendered = t.render(widget)
                rendered shouldContain pages[i].title
            }
        }

        test("buildPageWidget step indicator highlights current page") {
            val widget0 = OnboardingView.buildPageWidget(t, 0, pages.size)
            val widget2 = OnboardingView.buildPageWidget(t, 2, pages.size)
            val r0 = t.render(widget0)
            val r2 = t.render(widget2)
            // Both should render without error and contain step numbers
            r0 shouldContain "1"
            r2 shouldContain "3"
        }

        test("page 4 mentions /opsx-verify") {
            pages[3].body shouldContain "/opsx-verify"
        }

        test("page 5 mentions /opsx-archive") {
            pages[4].body shouldContain "/opsx-archive"
        }

        test("translateGuideKey maps RIGHT to Next") {
            OnboardingView.translateGuideKey(Keys.RIGHT) shouldBe OnboardingView.GuideAction.Next
        }

        test("translateGuideKey maps l to Next") {
            OnboardingView.translateGuideKey(KeyboardEvent("l")) shouldBe OnboardingView.GuideAction.Next
        }

        test("translateGuideKey maps SPACE to Next") {
            OnboardingView.translateGuideKey(Keys.SPACE) shouldBe OnboardingView.GuideAction.Next
        }

        test("translateGuideKey maps LEFT to Prev") {
            OnboardingView.translateGuideKey(Keys.LEFT) shouldBe OnboardingView.GuideAction.Prev
        }

        test("translateGuideKey maps h to Prev") {
            OnboardingView.translateGuideKey(KeyboardEvent("h")) shouldBe OnboardingView.GuideAction.Prev
        }

        test("translateGuideKey maps ENTER to Advance") {
            OnboardingView.translateGuideKey(Keys.ENTER) shouldBe OnboardingView.GuideAction.Advance
        }

        test("translateGuideKey maps q to Quit") {
            OnboardingView.translateGuideKey(KeyboardEvent("q")) shouldBe OnboardingView.GuideAction.Quit
        }

        test("translateGuideKey maps Q to Quit") {
            OnboardingView.translateGuideKey(KeyboardEvent("Q")) shouldBe OnboardingView.GuideAction.Quit
        }

        test("translateGuideKey maps ESCAPE to Quit") {
            OnboardingView.translateGuideKey(Keys.ESCAPE) shouldBe OnboardingView.GuideAction.Quit
        }

        test("translateGuideKey returns null for unrecognised keys") {
            OnboardingView.translateGuideKey(KeyboardEvent("x")).shouldBeNull()
            OnboardingView.translateGuideKey(KeyboardEvent("j")).shouldBeNull()
        }

        test("applyGuideAction Next advances when not at last page") {
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Next, 0, 5) shouldBe 1
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Next, 3, 5) shouldBe 4
        }

        test("applyGuideAction Next stays at last page") {
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Next, 4, 5) shouldBe 4
        }

        test("applyGuideAction Prev goes back when not at first page") {
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Prev, 3, 5) shouldBe 2
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Prev, 1, 5) shouldBe 0
        }

        test("applyGuideAction Prev stays at first page") {
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Prev, 0, 5) shouldBe 0
        }

        test("applyGuideAction Advance advances when not at last page") {
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Advance, 0, 5) shouldBe 1
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Advance, 3, 5) shouldBe 4
        }

        test("applyGuideAction Advance returns null at last page (finishes guide)") {
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Advance, 4, 5).shouldBeNull()
        }

        test("applyGuideAction Quit returns null") {
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Quit, 0, 5).shouldBeNull()
            OnboardingView.applyGuideAction(OnboardingView.GuideAction.Quit, 2, 5).shouldBeNull()
        }

        test("Page data class holds title and body") {
            val page = OnboardingView.Page("Test Title", "Test Body")
            page.title shouldBe "Test Title"
            page.body shouldBe "Test Body"
        }

        test("show renders first page and exits on q key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }

        test("show navigates pages with arrow keys") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            // Navigate right, then quit
            rec.inputEvents.add(KeyboardEvent("ArrowRight"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }

        test("show advances through all pages with Enter") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            // Enter through all 5 pages
            repeat(5) { rec.inputEvents.add(KeyboardEvent("Enter")) }
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }

        test("show navigates prev from first page stays at first") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            // Try going back at first page, then quit
            rec.inputEvents.add(KeyboardEvent("ArrowLeft"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }

        test("show navigates forward with l key then back with h key") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("l"))
            rec.inputEvents.add(KeyboardEvent("h"))
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }

        test("show handles space to advance page") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent(" "))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }

        test("show ignores unrecognised keys") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("j"))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }

        test("show stays at last page when pressing Next") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 80,
                    height = 40,
                    inputInteractive = true,
                )
            // Navigate to last page using right arrow, then try one more right (stays), then quit
            repeat(4) { rec.inputEvents.add(KeyboardEvent("ArrowRight")) }
            rec.inputEvents.add(KeyboardEvent("ArrowRight")) // stays at last
            rec.inputEvents.add(KeyboardEvent("Q"))
            val term = Terminal(terminalInterface = rec)
            OnboardingView.show(term)
        }
    })
