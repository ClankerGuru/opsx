package zone.clanker.opsx.model

/**
 * An atomic task within a change.
 *
 * Parsed from tasks.md markdown format:
 * ```
 * - [ ] a1b2c3d4e5 | Test ChangeConfig.parse
 *     Description of what to do.
 *     Can be multiple lines.
 *   depends: none
 * ```
 *
 * @property id unique 10-char alphanumeric identifier
 * @property name short human-readable name
 * @property description multi-line description of the task
 * @property status current execution status
 * @property dependencies list of task IDs this depends on
 */
data class TaskDefinition(
    val id: String,
    val name: String,
    val description: String,
    val status: TaskStatus,
    val dependencies: List<String>,
)
