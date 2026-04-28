/**
 * Data model for a single opsx change discovered by [ChangeScanner].
 */
package zone.clanker.opsx.cli.status

/**
 * One change from `opsx/changes/` or `opsx/archive/`.
 * Pure data -- no rendering, no IO after construction.
 *
 * @property name directory name of the change (e.g. `add-retry-logic`)
 * @property status lifecycle status read from `.opsx.yaml` (e.g. `active`, `completed`)
 * @property done number of checked tasks (`- [x]`) in `tasks.md`
 * @property total total number of task checkboxes in `tasks.md`
 */
data class ChangeEntry(
    val name: String,
    val status: String,
    val done: Int,
    val total: Int,
)
