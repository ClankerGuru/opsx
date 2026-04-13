package zone.clanker.opsx.model

/** Status of an atomic task within a change. */
enum class TaskStatus(
    val symbol: Char,
) {
    TODO(' '),
    IN_PROGRESS('>'),
    DONE('x'),
    BLOCKED('!'),
    SKIPPED('~'),
    ;

    companion object {
        fun fromSymbol(c: Char): TaskStatus =
            entries.firstOrNull { it.symbol == c }
                ?: throw IllegalArgumentException("Unknown task status symbol: '$c'")
    }
}
