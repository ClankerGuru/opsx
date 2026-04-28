package zone.clanker.opsx.tui.init

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.io.files.Path
import zone.clanker.opsx.cli.init.HostResult
import zone.clanker.opsx.cli.init.InitResult
import zone.clanker.opsx.cli.init.emitter.EmitPlan
import zone.clanker.opsx.cli.init.emitter.ExternalCommand
import zone.clanker.opsx.cli.init.host.Host
import zone.clanker.opsx.tui.render.Keys
import zone.clanker.opsx.tui.render.Styles

class InitViewTest :
    FunSpec({

        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 100, height = 40)
        val terminal = Terminal(terminalInterface = recorder)
        val margin = Styles.margin

        // --- hostLabels ---

        test("hostLabels contains all Host entries") {
            for (host in Host.entries) {
                InitView.hostLabels.keys shouldContain host
            }
        }

        test("hostLabels has human-readable labels") {
            InitView.hostLabels[Host.CLAUDE] shouldBe "Claude Code CLI"
            InitView.hostLabels[Host.COPILOT] shouldBe "GitHub Copilot CLI"
            InitView.hostLabels[Host.CODEX] shouldBe "OpenAI Codex CLI"
            InitView.hostLabels[Host.OPENCODE] shouldBe "OpenCode CLI"
        }

        // --- SelectorState ---

        test("SelectorState data class holds cursor, selected, and detected") {
            val state =
                InitView.SelectorState(
                    cursor = 1,
                    selected = setOf(Host.CLAUDE),
                    detected = setOf(Host.CLAUDE, Host.OPENCODE),
                )
            state.cursor shouldBe 1
            state.selected shouldBe setOf(Host.CLAUDE)
            state.detected shouldBe setOf(Host.CLAUDE, Host.OPENCODE)
        }

        // --- translateSelectorKey ---

        test("translateSelectorKey maps DOWN to MoveDown") {
            InitView.translateSelectorKey(Keys.DOWN) shouldBe InitView.SelectorAction.MoveDown
        }

        test("translateSelectorKey maps j to MoveDown") {
            InitView.translateSelectorKey(KeyboardEvent("j")) shouldBe InitView.SelectorAction.MoveDown
        }

        test("translateSelectorKey maps UP to MoveUp") {
            InitView.translateSelectorKey(Keys.UP) shouldBe InitView.SelectorAction.MoveUp
        }

        test("translateSelectorKey maps k to MoveUp") {
            InitView.translateSelectorKey(KeyboardEvent("k")) shouldBe InitView.SelectorAction.MoveUp
        }

        test("translateSelectorKey maps SPACE to Toggle") {
            InitView.translateSelectorKey(Keys.SPACE) shouldBe InitView.SelectorAction.Toggle
        }

        test("translateSelectorKey maps ENTER to Apply") {
            InitView.translateSelectorKey(Keys.ENTER) shouldBe InitView.SelectorAction.Apply
        }

        test("translateSelectorKey maps LEFT to Back") {
            InitView.translateSelectorKey(Keys.LEFT) shouldBe InitView.SelectorAction.Back
        }

        test("translateSelectorKey maps h to Back") {
            InitView.translateSelectorKey(KeyboardEvent("h")) shouldBe InitView.SelectorAction.Back
        }

        test("translateSelectorKey maps ESCAPE to Back") {
            InitView.translateSelectorKey(Keys.ESCAPE) shouldBe InitView.SelectorAction.Back
        }

        test("translateSelectorKey maps q to Back") {
            InitView.translateSelectorKey(KeyboardEvent("q")) shouldBe InitView.SelectorAction.Back
        }

        test("translateSelectorKey maps Q to Back") {
            InitView.translateSelectorKey(KeyboardEvent("Q")) shouldBe InitView.SelectorAction.Back
        }

        test("translateSelectorKey returns null for unrecognised keys") {
            InitView.translateSelectorKey(KeyboardEvent("x")).shouldBeNull()
            InitView.translateSelectorKey(KeyboardEvent("z")).shouldBeNull()
        }

        // --- applySelectorNav ---

        test("applySelectorNav MoveDown increments cursor") {
            InitView.applySelectorNav(InitView.SelectorAction.MoveDown, 0, 4) shouldBe 1
        }

        test("applySelectorNav MoveDown clamps at max") {
            InitView.applySelectorNav(InitView.SelectorAction.MoveDown, 3, 4) shouldBe 3
        }

        test("applySelectorNav MoveUp decrements cursor") {
            InitView.applySelectorNav(InitView.SelectorAction.MoveUp, 2, 4) shouldBe 1
        }

        test("applySelectorNav MoveUp clamps at zero") {
            InitView.applySelectorNav(InitView.SelectorAction.MoveUp, 0, 4) shouldBe 0
        }

        test("applySelectorNav other actions return same cursor") {
            InitView.applySelectorNav(InitView.SelectorAction.Toggle, 2, 4) shouldBe 2
            InitView.applySelectorNav(InitView.SelectorAction.Apply, 1, 4) shouldBe 1
            InitView.applySelectorNav(InitView.SelectorAction.Back, 3, 4) shouldBe 3
        }

        // --- toggleHost ---

        test("toggleHost adds host when not selected") {
            val result = InitView.toggleHost(emptySet(), Host.CLAUDE)
            result shouldContain Host.CLAUDE
        }

        test("toggleHost removes host when already selected") {
            val result = InitView.toggleHost(setOf(Host.CLAUDE, Host.COPILOT), Host.CLAUDE)
            result shouldNotContain Host.CLAUDE
            result shouldContain Host.COPILOT
        }

        test("toggleHost preserves other selections") {
            val result = InitView.toggleHost(setOf(Host.CODEX), Host.CLAUDE)
            result shouldContain Host.CODEX
            result shouldContain Host.CLAUDE
        }

        // --- processSelectorEvent ---

        test("processSelectorEvent MoveDown returns Navigate with new cursor") {
            val effect = InitView.processSelectorEvent(InitView.SelectorAction.MoveDown, 0, 4)
            effect shouldBe InitView.SelectorEffect.Navigate(1)
        }

        test("processSelectorEvent MoveUp returns Navigate with new cursor") {
            val effect = InitView.processSelectorEvent(InitView.SelectorAction.MoveUp, 2, 4)
            effect shouldBe InitView.SelectorEffect.Navigate(1)
        }

        test("processSelectorEvent Toggle returns ToggleAt with cursor") {
            val effect = InitView.processSelectorEvent(InitView.SelectorAction.Toggle, 1, 4)
            effect shouldBe InitView.SelectorEffect.ToggleAt(1)
        }

        test("processSelectorEvent Apply returns ApplySelection") {
            val effect = InitView.processSelectorEvent(InitView.SelectorAction.Apply, 0, 4)
            effect shouldBe InitView.SelectorEffect.ApplySelection
        }

        test("processSelectorEvent Back returns Exit") {
            val effect = InitView.processSelectorEvent(InitView.SelectorAction.Back, 0, 4)
            effect shouldBe InitView.SelectorEffect.Exit
        }

        test("processSelectorEvent null returns null") {
            InitView.processSelectorEvent(null, 0, 4).shouldBeNull()
        }

        // --- SelectorEffect data classes ---

        test("SelectorEffect Navigate data class holds newCursor") {
            val nav = InitView.SelectorEffect.Navigate(2)
            nav.newCursor shouldBe 2
        }

        test("SelectorEffect ToggleAt data class holds cursor") {
            val toggle = InitView.SelectorEffect.ToggleAt(3)
            toggle.cursor shouldBe 3
        }

        // --- detectHosts ---

        test("detectHosts returns a non-empty set") {
            val detected = InitView.detectHosts()
            // At minimum CLAUDE and OPENCODE have no externalCli, so they always pass
            detected.size shouldBeGreaterThan 0
            detected shouldContain Host.CLAUDE
            detected shouldContain Host.OPENCODE
        }

        // --- buildSelectorWidget ---

        test("buildSelectorWidget shows opsx init header") {
            val state =
                InitView.SelectorState(
                    cursor = 0,
                    selected = setOf(Host.CLAUDE),
                    detected = setOf(Host.CLAUDE, Host.OPENCODE),
                )
            val widget = InitView.buildSelectorWidget(state, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "init"
        }

        test("buildSelectorWidget shows select hosts prompt") {
            val state =
                InitView.SelectorState(
                    cursor = 0,
                    selected = emptySet(),
                    detected = emptySet(),
                )
            val widget = InitView.buildSelectorWidget(state, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "Select hosts to configure"
        }

        test("buildSelectorWidget shows all host IDs") {
            val state =
                InitView.SelectorState(
                    cursor = 0,
                    selected = emptySet(),
                    detected = Host.entries.toSet(),
                )
            val widget = InitView.buildSelectorWidget(state, margin)
            val rendered = terminal.render(widget)
            for (host in Host.entries) {
                rendered shouldContain host.id
            }
        }

        test("buildSelectorWidget shows not found hint for undetected hosts") {
            val state =
                InitView.SelectorState(
                    cursor = 0,
                    selected = emptySet(),
                    detected = setOf(Host.CLAUDE),
                )
            val widget = InitView.buildSelectorWidget(state, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "not found on PATH"
        }

        test("buildSelectorWidget shows skills and agents install note") {
            val state =
                InitView.SelectorState(
                    cursor = 0,
                    selected = emptySet(),
                    detected = emptySet(),
                )
            val widget = InitView.buildSelectorWidget(state, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "skills and agents"
        }

        // --- translateResultKey ---

        test("translateResultKey maps ENTER to Dismiss") {
            InitView.translateResultKey(Keys.ENTER) shouldBe InitView.ResultAction.Dismiss
        }

        test("translateResultKey maps LEFT to Dismiss") {
            InitView.translateResultKey(Keys.LEFT) shouldBe InitView.ResultAction.Dismiss
        }

        test("translateResultKey maps h to Dismiss") {
            InitView.translateResultKey(KeyboardEvent("h")) shouldBe InitView.ResultAction.Dismiss
        }

        test("translateResultKey maps ESCAPE to Dismiss") {
            InitView.translateResultKey(Keys.ESCAPE) shouldBe InitView.ResultAction.Dismiss
        }

        test("translateResultKey maps q to Dismiss") {
            InitView.translateResultKey(KeyboardEvent("q")) shouldBe InitView.ResultAction.Dismiss
        }

        test("translateResultKey maps Q to Dismiss") {
            InitView.translateResultKey(KeyboardEvent("Q")) shouldBe InitView.ResultAction.Dismiss
        }

        test("translateResultKey returns null for unrecognised keys") {
            InitView.translateResultKey(KeyboardEvent("x")).shouldBeNull()
            InitView.translateResultKey(KeyboardEvent("j")).shouldBeNull()
        }

        // --- buildResultWidget ---

        test("buildResultWidget shows complete header") {
            val result =
                InitResult(
                    hosts = emptyList(),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "opsx"
            rendered shouldContain "init"
            rendered shouldContain "complete"
        }

        test("buildResultWidget shows config.json entry") {
            val result =
                InitResult(
                    hosts = emptyList(),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "config.json"
        }

        test("buildResultWidget shows done when no failures") {
            val result =
                InitResult(
                    hosts = emptyList(),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "done"
        }

        test("buildResultWidget shows failure indicator when hosts failed") {
            val result =
                InitResult(
                    hosts =
                        listOf(
                            HostResult(
                                host = Host.CLAUDE,
                                plan = null,
                                error = "something broke",
                                canRunExternal = true,
                            ),
                        ),
                    cleanedPaths = emptyList(),
                    anyFailed = true,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "some hosts failed"
            rendered shouldContain "something broke"
        }

        test("buildResultWidget shows host results with skill and agent counts") {
            val plan =
                EmitPlan(
                    writtenFiles =
                        listOf(
                            Path(".claude/skills/foo/SKILL.md"),
                            Path(".claude/skills/bar/SKILL.md"),
                            Path(".claude/agents/dev.md"),
                        ),
                    deletedFiles = emptyList(),
                    externalCommands = emptyList(),
                    warnings = emptyList(),
                )
            val result =
                InitResult(
                    hosts =
                        listOf(
                            HostResult(
                                host = Host.CLAUDE,
                                plan = plan,
                                error = null,
                                canRunExternal = true,
                            ),
                        ),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "claude"
            rendered shouldContain "skills"
            rendered shouldContain "agents"
        }

        test("buildResultWidget shows cleaned paths count") {
            val result =
                InitResult(
                    hosts = emptyList(),
                    cleanedPaths = listOf(".opencode/skills", ".codex/skills"),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "Cleaned up 2 legacy paths"
        }

        test("buildResultWidget shows zero cleaned paths") {
            val result =
                InitResult(
                    hosts = emptyList(),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "Cleaned up 0 legacy paths"
        }

        // --- show interactive tests ---

        test("show renders selector and returns on q key without applying") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            InitView.show(term)
            val output = rec.output()
            output shouldContain "opsx"
            output shouldContain "init"
        }

        test("show navigates down and back without applying") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("j"))
            rec.inputEvents.add(KeyboardEvent("k"))
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            InitView.show(term)
        }

        test("show toggles selection and exits") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent(" "))
            rec.inputEvents.add(KeyboardEvent("q"))
            val term = Terminal(terminalInterface = rec)
            InitView.show(term)
        }

        test("show applies selection and shows result screen") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            // Enter = apply selection, y = confirm dialog, Enter = dismiss result
            rec.inputEvents.add(KeyboardEvent("Enter"))
            rec.inputEvents.add(KeyboardEvent("y"))
            rec.inputEvents.add(KeyboardEvent("Enter"))
            val term = Terminal(terminalInterface = rec)
            InitView.show(term)
            val output = rec.output()
            output shouldContain "complete"
        }

        test("buildResultWidget shows external commands with optional and required glyphs") {
            val plan =
                EmitPlan(
                    writtenFiles =
                        listOf(
                            Path(".claude/skills/foo/SKILL.md"),
                        ),
                    deletedFiles = emptyList(),
                    externalCommands =
                        listOf(
                            zone.clanker.opsx.cli.init.emitter.ExternalCommand(
                                description = "Register plugin",
                                argv = listOf("copilot", "plugin", "add"),
                                optional = false,
                            ),
                            zone.clanker.opsx.cli.init.emitter.ExternalCommand(
                                description = "Optional setup",
                                argv = listOf("echo", "hi"),
                                optional = true,
                            ),
                        ),
                    warnings = emptyList(),
                )
            val result =
                InitResult(
                    hosts =
                        listOf(
                            HostResult(
                                host = Host.CLAUDE,
                                plan = plan,
                                error = null,
                                canRunExternal = true,
                            ),
                        ),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "Register plugin"
            rendered shouldContain "Optional setup"
        }

        test("buildResultWidget shows marker instruction file") {
            val plan =
                EmitPlan(
                    writtenFiles =
                        listOf(
                            Path(".claude/skills/foo/SKILL.md"),
                            Path(".claude/agents/dev.md"),
                        ),
                    deletedFiles = emptyList(),
                    externalCommands = emptyList(),
                    warnings = emptyList(),
                )
            val result =
                InitResult(
                    hosts =
                        listOf(
                            HostResult(
                                host = Host.CLAUDE,
                                plan = plan,
                                error = null,
                                canRunExternal = true,
                            ),
                        ),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "marker"
            rendered shouldContain "CLAUDE.md"
        }

        test("buildResultWidget shows host without agentDir") {
            // OPENCODE has agentDir, but we test a host result with no marker
            val plan =
                EmitPlan(
                    writtenFiles =
                        listOf(
                            Path(".opencode/command/foo/SKILL.md"),
                        ),
                    deletedFiles = emptyList(),
                    externalCommands = emptyList(),
                    warnings = emptyList(),
                )
            val result =
                InitResult(
                    hosts =
                        listOf(
                            HostResult(
                                host = Host.OPENCODE,
                                plan = plan,
                                error = null,
                                canRunExternal = true,
                            ),
                        ),
                    cleanedPaths = emptyList(),
                    anyFailed = false,
                )
            val widget = InitView.buildResultWidget(result, margin)
            val rendered = terminal.render(widget)
            rendered shouldContain "opencode"
            rendered shouldContain "skills"
        }

        test("show ignores unrecognised keys in selector then exits") {
            val rec =
                TerminalRecorder(
                    ansiLevel = AnsiLevel.NONE,
                    width = 100,
                    height = 40,
                    inputInteractive = true,
                )
            rec.inputEvents.add(KeyboardEvent("x"))
            rec.inputEvents.add(KeyboardEvent("Escape"))
            val term = Terminal(terminalInterface = rec)
            InitView.show(term)
        }
    })
