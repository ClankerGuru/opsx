package zone.clanker.opsx.model

enum class ChangeStatus(
    val value: String,
) {
    DRAFT("draft"),
    PENDING("pending"),
    ACTIVE("active"),
    IN_PROGRESS("in-progress"),
    COMPLETED("completed"),
    DONE("done"),
    VERIFIED("verified"),
    ARCHIVED("archived"),
    ;

    fun canTransitionTo(target: ChangeStatus): Boolean =
        when (this) {
            DRAFT -> target in setOf(ACTIVE, PENDING, ARCHIVED)
            PENDING -> target in setOf(ACTIVE, ARCHIVED)
            ACTIVE -> target in setOf(IN_PROGRESS, ARCHIVED)
            IN_PROGRESS -> target in setOf(COMPLETED, DONE, ACTIVE)
            COMPLETED -> target in setOf(VERIFIED, DONE, ARCHIVED)
            DONE -> target in setOf(VERIFIED, ARCHIVED)
            VERIFIED -> target == ARCHIVED
            ARCHIVED -> false
        }

    companion object {
        fun from(value: String): ChangeStatus =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown change status: '$value'")
    }
}
