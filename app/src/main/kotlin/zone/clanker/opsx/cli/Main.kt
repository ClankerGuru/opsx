package zone.clanker.opsx.cli

import com.github.ajalt.clikt.core.main
import zone.clanker.opsx.tui.Dashboard

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        Dashboard.run()
    } else {
        OpsxCommand().main(args)
    }
}
