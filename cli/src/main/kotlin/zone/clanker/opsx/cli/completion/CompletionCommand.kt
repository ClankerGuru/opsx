/**
 * The `opsx completion` subcommand -- generates shell completion scripts for bash, zsh, or fish.
 * Output is printed to stdout so it can be piped into a completions file or eval'd directly.
 */
package zone.clanker.opsx.cli.completion

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice

/**
 * `opsx completion <shell>` -- emit a shell completion script to stdout.
 *
 * Accepts `bash`, `zsh`, or `fish` as the shell argument. The generated script
 * provides tab-completion for opsx subcommands.
 */
class CompletionCommand : CliktCommand(name = "completion") {
    override fun help(context: Context): String = "Generate shell completion script"

    private val shell: String by argument("shell").choice("bash", "zsh", "fish")

    override fun run() {
        echo("# opsx $shell completion")
        echo("# To install, run:")
        echo("#   opsx completion $shell >> ~/.$shell/completions/_opsx")
        echo("#")
        echo("# Or eval directly:")
        echo($$"#   eval \"$(opsx completion $$shell)\"")
        echo("")

        when (shell) {
            "bash" -> echo(bashCompletion())
            "zsh" -> echo(zshCompletion())
            "fish" -> echo(fishCompletion())
        }
    }

    private val commands =
        listOf(
            "init",
            "install",
            "nuke",
            "update",
            "status",
            "list",
            "log",
            "completion",
        )

    private fun bashCompletion() =
        $$"""
        |_opsx() {
        |    local cur prev commands
        |    COMPREPLY=()
        |    cur="${COMP_WORDS[COMP_CWORD]}"
        |    prev="${COMP_WORDS[COMP_CWORD-1]}"
        |    commands="$${commands.joinToString(" ")}"
        |
        |    if [ "$COMP_CWORD" -eq 1 ]; then
        |        COMPREPLY=( $(compgen -W "$commands" -- "$cur") )
        |    fi
        |}
        |complete -F _opsx opsx
        """.trimMargin()

    private fun zshCompletion() =
        """
        |#compdef opsx
        |_opsx() {
        |    local -a commands
        |    commands=(${commands.joinToString(" ") { "'$it'" }})
        |    _describe 'command' commands
        |}
        |compdef _opsx opsx
        """.trimMargin()

    private fun fishCompletion() =
        commands.joinToString("\n") { cmd ->
            "complete -c opsx -n '__fish_use_subcommand' -a '$cmd'"
        }
}
